#!/bin/bash

# Dicio Android 测试启动脚本
# 使用正确的Android Instrumented Test方式运行测试

set -e  # 遇到错误时退出

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

# 设置正确的Java版本
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home

# 检查Java版本
check_java() {
    log_info "检查Java版本..."
    if [ ! -d "$JAVA_HOME" ]; then
        log_error "Java 17未找到，请确保已安装Java 17"
        exit 1
    fi
    
    java_version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
    log_success "使用Java版本: $java_version"
}

# 检查设备连接
check_device() {
    log_info "检查Android设备连接..."
    
    if ! command -v adb &> /dev/null; then
        log_error "adb命令未找到，请确保Android SDK已安装"
        exit 1
    fi
    
    devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    if [ "$devices" -eq 0 ]; then
        log_error "未找到连接的Android设备"
        log_info "请确保设备已连接并启用USB调试"
        exit 1
    fi
    
    log_success "找到 $devices 个连接的设备"
}

# 启动logcat监控
start_logcat() {
    log_info "启动logcat监控..."
    
    # 清除之前的日志
    adb logcat -c
    
    # 启动后台logcat监控
    adb logcat -v time | grep -E "(SimpleAdvancedTestSuiteTest|ScreenshotTakerTest|🚀|✅|❌|📱|🧪|🔧|📊|🎵|🔄|🎯)" --line-buffered > test_logs.txt &
    LOGCAT_PID=$!
    
    log_success "logcat监控已启动 (PID: $LOGCAT_PID)"
}

# 停止logcat监控
stop_logcat() {
    if [ ! -z "$LOGCAT_PID" ]; then
        kill $LOGCAT_PID 2>/dev/null || true
        log_info "logcat监控已停止"
    fi
}

# 运行简单测试
run_simple_test() {
    log_info "🚀 运行SimpleAdvancedTestSuite测试..."
    
    ./gradlew connectedAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=org.stypox.dicio.test.SimpleAdvancedTestSuiteTest \
        --info
    
    if [ $? -eq 0 ]; then
        log_success "✅ SimpleAdvancedTestSuite测试完成"
    else
        log_error "❌ SimpleAdvancedTestSuite测试失败"
        return 1
    fi
}

# 运行截图测试
run_screenshot_test() {
    log_info "📸 运行ScreenshotTaker测试..."
    
    ./gradlew connectedAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=org.stypox.dicio.screenshot.ScreenshotTakerTest \
        --info
    
    if [ $? -eq 0 ]; then
        log_success "✅ ScreenshotTaker测试完成"
    else
        log_warning "⚠️ ScreenshotTaker测试失败（可能需要特殊配置）"
    fi
}

# 运行所有测试
run_all_tests() {
    log_info "🎯 运行所有Android测试..."
    
    ./gradlew connectedAndroidTest --info
    
    if [ $? -eq 0 ]; then
        log_success "✅ 所有测试完成"
    else
        log_error "❌ 部分测试失败"
        return 1
    fi
}

# 显示测试结果
show_results() {
    log_info "📊 测试结果摘要:"
    
    # 显示最近的测试日志
    if [ -f "test_logs.txt" ]; then
        echo ""
        echo "=== 最近的测试日志 ==="
        tail -20 test_logs.txt
        echo ""
    fi
    
    # 显示测试报告位置
    log_info "详细测试报告位置:"
    echo "  - HTML报告: file://$(pwd)/app/build/reports/androidTests/connected/debug/index.html"
    echo "  - 日志文件: $(pwd)/test_logs.txt"
}

# 清理函数
cleanup() {
    stop_logcat
    log_info "清理完成"
}

# 设置退出时清理
trap cleanup EXIT

# 显示帮助信息
show_help() {
    echo "Dicio Android 测试启动脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  simple     运行SimpleAdvancedTestSuite测试"
    echo "  screenshot 运行ScreenshotTaker测试"
    echo "  all        运行所有测试"
    echo "  help       显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 simple      # 运行基础测试"
    echo "  $0 all         # 运行所有测试"
}

# 主函数
main() {
    log_info "🎯 Dicio Android 测试启动器"
    echo "=================================="
    
    # 检查环境
    check_java
    check_device
    
    # 启动监控
    start_logcat
    
    # 根据参数运行测试
    case "${1:-simple}" in
        "simple")
            run_simple_test
            ;;
        "screenshot")
            run_screenshot_test
            ;;
        "all")
            run_all_tests
            ;;
        "help"|"-h"|"--help")
            show_help
            exit 0
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
    
    # 显示结果
    show_results
}

# 运行主函数
main "$@"
