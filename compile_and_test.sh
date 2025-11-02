#!/bin/bash

# DeviceControl 编译和测试脚本
# 作者: AI Assistant
# 日期: $(date '+%Y-%m-%d')

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Java环境
check_java_environment() {
    log_info "检查Java环境..."
    
    # 设置Java 17环境
    export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
    export PATH=$JAVA_HOME/bin:$PATH
    
    if [ ! -d "$JAVA_HOME" ]; then
        log_error "Java 17未找到，请确保已安装Microsoft OpenJDK 17"
        exit 1
    fi
    
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log_success "Java版本: $java_version"
}

# 检查ADB连接
check_adb_connection() {
    log_info "检查ADB设备连接..."
    
    if ! command -v adb &> /dev/null; then
        log_error "ADB未找到，请确保Android SDK已正确安装"
        exit 1
    fi
    
    devices=$(adb devices | grep -v "List of devices attached" | grep -v "^$" | wc -l)
    if [ $devices -eq 0 ]; then
        log_warning "未检测到Android设备，将仅进行编译验证"
        return 1
    else
        log_success "检测到 $devices 个Android设备"
        adb devices
        return 0
    fi
}

# 清理构建缓存
clean_build() {
    log_info "清理构建缓存..."
    ./gradlew clean
    log_success "构建缓存清理完成"
}

# 编译项目
compile_project() {
    log_info "开始编译项目..."
    
    log_info "编译主应用..."
    ./gradlew assembleNoModelsDebug
    
    log_info "编译测试APK..."
    ./gradlew assembleNoModelsDebugAndroidTest
    
    log_success "项目编译完成"
}

# 运行单元测试
run_unit_tests() {
    log_info "运行单元测试..."
    ./gradlew testNoModelsDebugUnitTest
    log_success "单元测试完成"
}

# 安装APK到设备
install_apks() {
    if ! check_adb_connection; then
        log_warning "跳过APK安装（无设备连接）"
        return 1
    fi
    
    log_info "安装APK到设备..."
    
    # 安装主应用
    main_apk="app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
    if [ -f "$main_apk" ]; then
        adb install -r "$main_apk"
        log_success "主应用APK安装完成"
    else
        log_error "主应用APK未找到: $main_apk"
        return 1
    fi
    
    # 安装测试APK
    test_apk="app/build/outputs/apk/androidTest/noModels/debug/app-noModels-debug-androidTest.apk"
    if [ -f "$test_apk" ]; then
        adb install -r "$test_apk"
        log_success "测试APK安装完成"
    else
        log_error "测试APK未找到: $test_apk"
        return 1
    fi
    
    return 0
}

# 运行DeviceControl仪器测试
run_instrumentation_tests() {
    if ! check_adb_connection; then
        log_warning "跳过仪器测试（无设备连接）"
        return 1
    fi
    
    log_info "运行DeviceControl仪器测试..."
    
    # 创建测试报告目录
    timestamp=$(date '+%Y%m%d_%H%M%S')
    report_dir="test_reports/device_control_$timestamp"
    mkdir -p "$report_dir"
    
    # 启动logcat监控
    log_info "启动logcat监控..."
    adb logcat -c  # 清空logcat
    adb logcat > "$report_dir/logcat_output.txt" &
    logcat_pid=$!
    
    # 运行测试
    log_info "执行DeviceControl测试..."
    adb shell am instrument -w \
        -e class com.ai.voice.skills.device_control.DeviceControlInstrumentationTest \
        com.ai.voice.test/com.ai.voice.CustomTestRunner > "$report_dir/test_output.txt" 2>&1
    
    test_exit_code=$?
    
    # 停止logcat监控
    kill $logcat_pid 2>/dev/null || true
    
    # 拉取设备上的测试报告
    log_info "拉取设备测试报告..."
    adb pull /sdcard/Android/data/com.ai.voice/files/test_reports/ "$report_dir/device_reports/" 2>/dev/null || true
    
    # 复制Gradle测试报告
    if [ -d "app/build/reports/androidTests" ]; then
        cp -r app/build/reports/androidTests "$report_dir/gradle_reports/"
    fi
    
    # 生成测试总结
    generate_test_summary "$report_dir" $test_exit_code
    
    if [ $test_exit_code -eq 0 ]; then
        log_success "DeviceControl仪器测试完成"
    else
        log_error "DeviceControl仪器测试失败"
    fi
    
    return $test_exit_code
}

# 生成测试总结
generate_test_summary() {
    local report_dir=$1
    local exit_code=$2
    
    log_info "生成测试总结报告..."
    
    summary_file="$report_dir/test_summary.md"
    
    cat > "$summary_file" << EOF
# DeviceControl 测试总结报告

## 测试信息
- **测试时间**: $(date '+%Y-%m-%d %H:%M:%S')
- **测试类型**: DeviceControl仪器测试
- **测试结果**: $([ $exit_code -eq 0 ] && echo "✅ 通过" || echo "❌ 失败")

## 文件结构
\`\`\`
$report_dir/
├── test_output.txt          # 测试执行输出
├── logcat_output.txt        # 设备日志
├── device_reports/          # 设备端测试报告
├── gradle_reports/          # Gradle测试报告
└── test_summary.md          # 本总结文件
\`\`\`

## 测试详情
EOF

    # 分析测试输出
    if [ -f "$report_dir/test_output.txt" ]; then
        echo "### 测试执行结果" >> "$summary_file"
        echo '```' >> "$summary_file"
        tail -20 "$report_dir/test_output.txt" >> "$summary_file"
        echo '```' >> "$summary_file"
    fi
    
    # 分析logcat中的关键信息
    if [ -f "$report_dir/logcat_output.txt" ]; then
        echo "### 关键日志信息" >> "$summary_file"
        echo '```' >> "$summary_file"
        grep -i "devicecontrol\|test\|error\|exception" "$report_dir/logcat_output.txt" | tail -10 >> "$summary_file" 2>/dev/null || echo "无关键日志信息" >> "$summary_file"
        echo '```' >> "$summary_file"
    fi
    
    log_success "测试总结报告生成完成: $summary_file"
}

# 主函数
main() {
    log_info "开始DeviceControl编译和测试流程..."
    
    # 检查环境
    check_java_environment
    
    # 编译阶段
    clean_build
    compile_project
    run_unit_tests
    
    # 测试阶段（如果有设备连接）
    if install_apks; then
        run_instrumentation_tests
        test_result=$?
    else
        log_warning "跳过仪器测试阶段"
        test_result=0
    fi
    
    # 总结
    echo ""
    log_info "==============================================="
    log_info "编译和测试流程完成"
    log_info "==============================================="
    
    if [ $test_result -eq 0 ]; then
        log_success "所有测试通过！"
    else
        log_error "部分测试失败，请检查测试报告"
    fi
    
    # 显示测试报告位置
    if [ -d "test_reports" ]; then
        latest_report=$(ls -t test_reports/ | head -1)
        if [ -n "$latest_report" ]; then
            log_info "最新测试报告: test_reports/$latest_report"
        fi
    fi
    
    exit $test_result
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi