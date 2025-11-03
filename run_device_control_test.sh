#!/bin/bash

# DeviceControl仪器测试运行脚本
# 用于运行DeviceControl技能的仪器测试并收集报告
# 
# 注意：DeviceControl已拆分为5个技能，此脚本测试所有拆分后的技能命令

set -e

echo "🚀 开始DeviceControl仪器测试..."
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# 检查ADB连接
echo "📱 检查设备连接..."
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 没有找到连接的Android设备"
    echo "请确保:"
    echo "1. 设备已连接并开启USB调试"
    echo "2. 已授权此计算机进行调试"
    exit 1
fi

DEVICE_INFO=$(adb shell getprop ro.product.model)
echo "✅ 设备已连接: $DEVICE_INFO"

# 设置Java环境
echo "☕ 设置Java环境..."
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo "Java版本: $JAVA_VERSION"

# 清理之前的构建
echo "🧹 清理之前的构建..."
./gradlew clean

# 构建测试APK
echo "🔨 构建应用和测试APK..."
./gradlew assembleNoModelsDebug assembleNoModelsDebugAndroidTest

# 安装应用APK
echo "📦 安装应用APK..."
./gradlew installNoModelsDebug

# 运行DeviceControl仪器测试
echo "🧪 运行DeviceControl仪器测试..."
TEST_START_TIME=$(date '+%Y%m%d_%H%M%S')

# 创建日志目录
LOG_DIR="./test_logs"
mkdir -p "$LOG_DIR"

# 运行测试并捕获输出
TEST_LOG="$LOG_DIR/device_control_test_$TEST_START_TIME.log"
echo "测试日志将保存到: $TEST_LOG"

# 启动logcat监控
adb logcat -c  # 清空logcat
adb logcat -s "TestRunner" "DeviceControlInstrumentationTest" "BaseDeviceControlSkill" "PowerControl" "InputSourceControl" "AppLauncher" "WhiteboardTools" "SystemNavigation" "AutoTest" > "$LOG_DIR/logcat_$TEST_START_TIME.log" &
LOGCAT_PID=$!

# 运行测试
echo "开始执行测试..."
if ./gradlew connectedNoModelsDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ai.voice.skills.device_control.DeviceControlInstrumentationTest 2>&1 | tee "$TEST_LOG"; then
    echo "✅ 测试执行完成"
    TEST_SUCCESS=true
else
    echo "❌ 测试执行失败"
    TEST_SUCCESS=false
fi

# 停止logcat监控
kill $LOGCAT_PID 2>/dev/null || true

# 等待一下确保文件写入完成
sleep 2

# 拉取设备上的测试报告
echo "📄 拉取测试报告..."
REPORT_DIR="./test_reports"
mkdir -p "$REPORT_DIR"

# 拉取应用外部存储中的测试报告
adb shell "find /sdcard/Android/data/com.ai.voice/files -name 'device_control_test_report_*.txt' -type f" | while read -r file; do
    if [ -n "$file" ]; then
        local_file="$REPORT_DIR/$(basename "$file")"
        adb pull "$file" "$local_file"
        echo "✅ 报告已拉取: $local_file"
    fi
done

# 拉取Gradle测试报告
if [ -d "app/build/reports/androidTests/connected" ]; then
    cp -r app/build/reports/androidTests/connected "$REPORT_DIR/gradle_reports_$TEST_START_TIME"
    echo "✅ Gradle测试报告已复制"
fi

# 生成测试总结
SUMMARY_FILE="$REPORT_DIR/test_summary_$TEST_START_TIME.txt"
echo "📊 生成测试总结..."

cat > "$SUMMARY_FILE" << EOF
DeviceControl仪器测试总结报告
===============================
测试时间: $(date '+%Y-%m-%d %H:%M:%S')
设备信息: $DEVICE_INFO
测试类型: DeviceControl仪器测试（包含所有拆分后的技能）
测试范围: PowerControl, InputSourceControl, AppLauncher, WhiteboardTools, SystemNavigation
测试状态: $(if [ "$TEST_SUCCESS" = true ]; then echo "✅ 成功"; else echo "❌ 失败"; fi)

文件位置:
- 测试日志: $TEST_LOG
- Logcat日志: $LOG_DIR/logcat_$TEST_START_TIME.log
- 测试报告目录: $REPORT_DIR

EOF

# 如果有测试报告文件，添加内容摘要
for report in "$REPORT_DIR"/device_control_test_report_*.txt; do
    if [ -f "$report" ]; then
        echo "测试报告摘要:" >> "$SUMMARY_FILE"
        echo "=============" >> "$SUMMARY_FILE"
        tail -20 "$report" >> "$SUMMARY_FILE"
        break
    fi
done

echo "📋 测试总结已保存到: $SUMMARY_FILE"

# 显示测试结果
echo ""
echo "🎯 测试完成总结:"
echo "================"
if [ "$TEST_SUCCESS" = true ]; then
    echo "✅ DeviceControl仪器测试执行成功"
else
    echo "❌ DeviceControl仪器测试执行失败"
fi

echo "📁 相关文件:"
echo "   - 测试总结: $SUMMARY_FILE"
echo "   - 测试日志: $TEST_LOG"
echo "   - 报告目录: $REPORT_DIR"

# 如果测试失败，显示错误信息
if [ "$TEST_SUCCESS" = false ]; then
    echo ""
    echo "❌ 错误信息:"
    echo "============"
    tail -20 "$TEST_LOG"
    exit 1
fi

echo ""
echo "🎉 DeviceControl仪器测试完成!"