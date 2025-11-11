#!/bin/bash

# STT预加载测试脚本
# 验证SenseVoice在应用启动时是否正确预加载

echo "=========================================="
echo "🧪 STT预加载测试"
echo "=========================================="
echo ""

# 检查是否连接设备
if ! adb devices | grep -q "device$"; then
    echo "❌ 未检测到Android设备"
    exit 1
fi

echo "✅ 检测到Android设备"
echo ""

# 清除之前的日志
adb logcat -c

echo "📱 启动应用..."
adb shell am start -n org.stypox.dicio.debug/com.ai.voice.ui.floating.FloatingLauncherActivity

echo ""
echo "⏳ 等待5秒，让应用完成启动和预加载..."
sleep 5

echo ""
echo "=========================================="
echo "📋 检查预加载日志"
echo "=========================================="
echo ""

# 检查预加载相关日志
echo "1️⃣ 检查SttPreloader日志:"
adb logcat -d | grep "SttPreloader" | tail -10
echo ""

echo "2️⃣ 检查SenseVoice初始化日志:"
adb logcat -d | grep "SenseVoice输入设备" | tail -10
echo ""

echo "3️⃣ 检查初始化完成状态:"
adb logcat -d | grep "SenseVoice初始化完成" | tail -5
echo ""

echo "=========================================="
echo "🎯 测试唤醒词触发"
echo "=========================================="
echo ""

echo "请说唤醒词，然后观察日志..."
echo "按 Ctrl+C 停止日志监控"
echo ""

# 实时监控相关日志
adb logcat | grep -E "SttPreloader|SenseVoice|WakeService|tryLoad"

