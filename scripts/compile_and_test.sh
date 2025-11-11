#!/bin/bash

# DeviceControl 编译和测试脚本
# 作者: AI Assistant
# 日期: $(date '+%Y-%m-%d')
# 
# 注意：DeviceControl已拆分为5个技能：
# - PowerControl (电源和音量控制)
# - InputSourceControl (输入源切换)
# - AppLauncher (应用启动)
# - WhiteboardTools (白板工具)
# - SystemNavigation (系统导航和功能)
# 此脚本测试所有拆分后的技能命令
#
# 使用方法:
#   ./compile_and_test.sh              # 批量测试模式（默认）
#   ./compile_and_test.sh --single     # 单个测试模式（交互式）

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

# 测试用例列表
declare -a TEST_CASES=(
    "testVolumeUp:音量增加:PowerControl"
    "testVolumeDown:音量减小:PowerControl"
    "testMute:静音/取消静音:PowerControl"
    "testPowerOff:关机:PowerControl"
    "testPowerOn:开机:PowerControl"
    "testInputSource:打开输入源窗口:InputSourceControl"
    "testHdmiOne:切换到HDMI 1:InputSourceControl"
    "testHdmiTwo:切换到HDMI 2:InputSourceControl"
    "testDpPort:切换到DP端口:InputSourceControl"
    "testFrontHdmi:切换到前面板HDMI:InputSourceControl"
    "testFrontUsbC:切换到前面板USB-C:InputSourceControl"
    "testOps:切换到OPS:InputSourceControl"
    "testHomeScreen:返回主屏幕:AppLauncher"
    "testGoogle:打开Google:AppLauncher"
    "testBrowser:打开浏览器:AppLauncher"
    "testPlayStore:打开Play商店:AppLauncher"
    "testYoutube:打开YouTube:AppLauncher"
    "testSettings:打开设置:AppLauncher"
    "testRecorder:打开录音机:AppLauncher"
    "testEshare:打开E-Share:AppLauncher"
    "testCamera:打开相机:AppLauncher"
    "testFinder:打开文件管理器:AppLauncher"
    "testScreenshot:截图:SystemNavigation"
    "testGoBack:返回:SystemNavigation"
    "testNoteMode:切换到笔记模式:SystemNavigation"
    "testWindowMode:切换到窗口模式:SystemNavigation"
    "testWifiConnect:连接WiFi:SystemNavigation"
    "testWhiteboard:打开白板:WhiteboardTools"
    "testSaveWhiteboard:保存白板:WhiteboardTools"
    "testRedPen:切换到红笔:WhiteboardTools"
    "testBluePen:切换到蓝笔:WhiteboardTools"
    "testWhitePen:切换到白笔:WhiteboardTools"
    "testBlackPen:切换到黑笔:WhiteboardTools"
    "testEraser:切换到橡皮擦:WhiteboardTools"
    "testDeleteAll:清空白板:WhiteboardTools"
    "testHighlightPen:切换到荧光笔:WhiteboardTools"
    "testFountainPen:切换到钢笔:WhiteboardTools"
    "testBrushPen:切换到毛笔:WhiteboardTools"
)

# 等待用户输入
wait_for_user_input() {
    local test_name=$1
    local test_desc=$2
    
    echo ""
    log_info "==============================================="
    log_info "测试用例: $test_name"
    log_info "描述: $test_desc"
    log_info "==============================================="
    echo ""
    log_info "请观察设备上的执行结果，然后输入："
    log_info "  1 = 测试成功，继续下一个"
    log_info "  0 = 测试失败，继续下一个"
    log_info "  q = 退出测试"
    echo ""
    
    while true; do
        read -p "请输入 (1/0/q): " user_input
        
        case "$user_input" in
            1)
                echo "✅ 用户确认: 测试成功"
                return 0
                ;;
            0)
                echo "❌ 用户确认: 测试失败"
                return 1
                ;;
            q|Q)
                echo "⏹️  用户退出测试"
                return 2
                ;;
            *)
                log_warning "无效输入，请输入 1、0 或 q"
                ;;
        esac
    done
}

# 运行单个测试用例
run_single_test_case() {
    local test_case=$1
    local test_method=$(echo "$test_case" | cut -d':' -f1)
    local test_desc=$(echo "$test_case" | cut -d':' -f2)
    local test_category=$(echo "$test_case" | cut -d':' -f3)
    
    log_info "执行测试: $test_method ($test_desc)"
    
    # 执行测试（忽略错误，因为我们需要用户确认）
    adb shell am instrument -w \
        -e class "com.ai.voice.skills.device_control.DeviceControlInstrumentationTest#$test_method" \
        com.ai.voice.test/com.ai.voice.CustomTestRunner > /dev/null 2>&1 || true
    
    # 等待用户输入
    wait_for_user_input "$test_method" "$test_desc"
    local user_result=$?
    
    # 根据用户输入记录结果
    local final_result="UNKNOWN"
    local status_icon="❓"
    
    if [ $user_result -eq 0 ]; then
        final_result="PASS"
        status_icon="✅"
    elif [ $user_result -eq 1 ]; then
        final_result="FAIL"
        status_icon="❌"
    else
        # 用户退出
        return 2
    fi
    
    # 记录结果
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] $status_icon [$test_category] $test_method ($test_desc): $final_result" >> "$SINGLE_TEST_RESULT_FILE"
    
    return $user_result
}

# 运行单个测试模式
run_single_test_mode() {
    if ! check_adb_connection; then
        log_error "无法运行单个测试模式：未检测到Android设备"
        return 1
    fi
    
    log_info "==============================================="
    log_info "启动单个测试模式（交互式）"
    log_info "==============================================="
    echo ""
    
    # 创建结果文件
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    SINGLE_TEST_RESULT_FILE="test_reports/single_test_results_$timestamp.txt"
    mkdir -p "$(dirname "$SINGLE_TEST_RESULT_FILE")"
    
    # 写入文件头
    cat > "$SINGLE_TEST_RESULT_FILE" << EOF
# DeviceControl 单个测试模式结果报告
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
# 测试模式: 交互式单个测试
# 
# 格式: [时间] [状态] [分类] 测试方法名 (描述): 结果
# 状态: ✅ = 用户确认成功, ❌ = 用户确认失败
# 结果: PASS = 通过, FAIL = 失败
#
# ================================================

EOF
    
    log_success "测试结果将保存到: $SINGLE_TEST_RESULT_FILE"
    echo ""
    
    local total_tests=${#TEST_CASES[@]}
    local current_test=0
    local passed_count=0
    local failed_count=0
    
    log_info "总共 $total_tests 个测试用例"
    echo ""
    
    # 逐个执行测试用例
    for test_case in "${TEST_CASES[@]}"; do
        current_test=$((current_test + 1))
        local test_method=$(echo "$test_case" | cut -d':' -f1)
        
        # 跳过被忽略的测试（如 testPowerOff）
        if [ "$test_method" = "testPowerOff" ]; then
            log_warning "跳过被忽略的测试用例: $test_method"
            continue
        fi
        
        log_info "[$current_test/$total_tests] 准备执行: $test_method"
        
        # 临时禁用 set -e，因为我们希望继续执行即使测试失败
        set +e
        run_single_test_case "$test_case"
        local result=$?
        set -e
        
        if [ $result -eq 0 ]; then
            passed_count=$((passed_count + 1))
            log_success "测试通过: $test_method"
        elif [ $result -eq 1 ]; then
            failed_count=$((failed_count + 1))
            log_error "测试失败: $test_method"
        else
            # 用户退出
            log_warning "用户中断测试"
            break
        fi
        
        echo ""
        sleep 1  # 短暂延迟，方便观察
    done
    
    # 生成总结
    local completed_tests=$((passed_count + failed_count))
    echo "" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# ================================================" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 测试总结" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# ================================================" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 完成时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 总测试数: $total_tests" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 完成测试: $completed_tests" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 通过数量: $passed_count" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 失败数量: $failed_count" >> "$SINGLE_TEST_RESULT_FILE"
    echo "# 通过率: $([ $completed_tests -gt 0 ] && echo "scale=2; $passed_count * 100 / $completed_tests" | bc || echo "0")%" >> "$SINGLE_TEST_RESULT_FILE"
    
    # 显示总结
    echo ""
    log_info "==============================================="
    log_info "单个测试模式完成"
    log_info "==============================================="
    log_info "总测试数: $total_tests"
    log_info "完成测试: $completed_tests"
    log_info "通过数量: $passed_count"
    log_info "失败数量: $failed_count"
    if [ $completed_tests -gt 0 ]; then
        local pass_rate=$(echo "scale=1; $passed_count * 100 / $completed_tests" | bc)
        log_info "通过率: ${pass_rate}%"
    fi
    log_success "测试结果已保存到: $SINGLE_TEST_RESULT_FILE"
    
    return $([ $failed_count -eq 0 ] && echo 0 || echo 1)
}

# 运行DeviceControl仪器测试
run_instrumentation_tests() {
    if ! check_adb_connection; then
        log_warning "跳过仪器测试（无设备连接）"
        return 1
    fi
    
    log_info "运行DeviceControl仪器测试（包含所有拆分后的技能）..."
    
    # 创建测试报告目录
    timestamp=$(date '+%Y%m%d_%H%M%S')
    report_dir="test_reports/device_control_$timestamp"
    mkdir -p "$report_dir"
    
    # 启动logcat监控
    log_info "启动logcat监控..."
    adb logcat -c  # 清空logcat
    adb logcat > "$report_dir/logcat_output.txt" &
    logcat_pid=$!
    
    # 运行测试（测试所有拆分后的技能命令）
    log_info "执行DeviceControl测试（包含PowerControl、InputSourceControl、AppLauncher、WhiteboardTools、SystemNavigation）..."
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
- **测试类型**: DeviceControl仪器测试（包含所有拆分后的技能）
- **测试范围**: PowerControl, InputSourceControl, AppLauncher, WhiteboardTools, SystemNavigation
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
        grep -i "devicecontrol\|BaseDeviceControlSkill\|PowerControl\|InputSourceControl\|AppLauncher\|WhiteboardTools\|SystemNavigation\|test\|error\|exception" "$report_dir/logcat_output.txt" | tail -20 >> "$summary_file" 2>/dev/null || echo "无关键日志信息" >> "$summary_file"
        echo '```' >> "$summary_file"
    fi
    
    log_success "测试总结报告生成完成: $summary_file"
}

# 主函数
main() {
    # 检查是否使用单个测试模式
    local SINGLE_MODE=false
    if [ "$1" = "--single" ] || [ "$1" = "-s" ]; then
        SINGLE_MODE=true
    fi
    
    if [ "$SINGLE_MODE" = true ]; then
        log_info "开始DeviceControl单个测试模式（交互式）..."
        
        # 检查环境
        check_java_environment
        
        # 编译阶段
        clean_build
        compile_project
        run_unit_tests
        
        # 安装APK（如果有设备连接）
        if install_apks; then
            run_single_test_mode
            test_result=$?
        else
            log_error "无法运行单个测试模式：未检测到Android设备"
            test_result=1
        fi
        
        exit $test_result
    else
        log_info "开始DeviceControl编译和测试流程（批量模式，包含所有拆分后的技能）..."
        
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
    fi
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi