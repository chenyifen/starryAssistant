#!/bin/bash

# 音频资源协调修复测试脚本
# 测试WakeService在STT停止后是否正确重新启动监听

set -e

echo "🔧 音频资源协调修复测试"
echo "========================"

# 检查ADB连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 未检测到Android设备，请确保设备已连接并启用USB调试"
    exit 1
fi

echo "📱 检测到Android设备"

# 1. 编译并安装应用
echo ""
echo "🔨 步骤1: 编译并安装应用..."
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
./gradlew assembleWithModelsDebug
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

echo "✅ 应用安装完成"

# 2. 启动应用
echo ""
echo "🚀 步骤2: 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

echo "✅ 应用已启动"

# 3. 启动悬浮窗服务
echo ""
echo "🪟 步骤3: 启动悬浮窗服务..."
adb shell am startservice -n org.stypox.dicio/.ui.floating.FloatingWindowService

echo "✅ 悬浮窗服务已启动"

# 4. 监控关键日志
echo ""
echo "📊 步骤4: 监控音频资源协调日志..."
echo ""
echo "测试步骤:"
echo "1. 点击悬浮窗能量球开始监听"
echo "2. 说话或播放音频"
echo "3. 再次点击能量球停止监听"
echo "4. 观察WakeService是否重新启动监听"
echo ""
echo "关键日志标签:"
echo "  - AudioPipelineCoordinator: 音频Pipeline状态转换"
echo "  - WakeService: 唤醒服务状态"
echo "  - SenseVoiceInputDevice: STT设备状态"
echo "  - FloatingWindowViewModel: 悬浮窗状态"
echo ""
echo "期望行为:"
echo "  ✅ STT停止后，Pipeline应该从AsrListening转回WakeListening"
echo "  ✅ WakeService应该重新开始监听唤醒词"
echo "  ❌ 不应该出现JobCancellationException错误"
echo ""
echo "按 Ctrl+C 停止监控"
echo "========================"

# 清除旧日志并开始监控
adb logcat -c
adb logcat | grep -E "(AudioPipelineCoordinator|WakeService|SenseVoiceInputDevice|FloatingWindowViewModel|Pipeline状态转换|音频处理协程被取消|WakeListening|AsrListening)"
