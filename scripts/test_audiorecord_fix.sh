#!/bin/bash

# 测试AudioRecord修复的脚本
# Test script for AudioRecord fix

echo "🔧 测试AudioRecord读取失败修复..."
echo "🔧 Testing AudioRecord read failure fix..."

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有找到连接的Android设备"
    echo "❌ No connected Android device found"
    echo "请连接设备并启用USB调试"
    echo "Please connect device and enable USB debugging"
    exit 1
fi

# 安装修复后的应用
echo "📱 安装修复后的应用..."
echo "📱 Installing fixed app..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

if [ $? -ne 0 ]; then
    echo "❌ 应用安装失败"
    echo "❌ App installation failed"
    exit 1
fi

# 授予麦克风权限
echo "🎤 授予麦克风权限..."
echo "🎤 Granting microphone permission..."
adb shell pm grant org.stypox.dicio.master android.permission.RECORD_AUDIO

# 清除日志
echo "🧹 清除旧日志..."
echo "🧹 Clearing old logs..."
adb logcat -c

# 启动应用
echo "🚀 启动应用..."
echo "🚀 Starting app..."
adb shell am start -n org.stypox.dicio.master/org.stypox.dicio.MainActivity

sleep 3

# 启动WakeService
echo "🎵 启动WakeService..."
echo "🎵 Starting WakeService..."
adb shell am startservice org.stypox.dicio.master/com.ai.voice.WakeService

sleep 5

echo "📊 监控AudioRecord相关日志 (30秒)..."
echo "📊 Monitoring AudioRecord related logs (30 seconds)..."
echo "查找以下关键信息："
echo "Looking for these key indicators:"
echo "  ✅ AudioRecord started successfully"
echo "  🔄 Frame processing"
echo "  ❌ AudioRecord read failed (应该减少或消失)"
echo "  ⏸️ AudioRecord paused for ASR"
echo "  ▶️ AudioRecord resumed"
echo ""

# 监控关键日志
timeout 30s adb logcat | grep -E "(AudioRecord|WakeService|🔄.*Frame|❌.*read failed|⏸️|▶️)" --line-buffered || true

echo ""
echo "🔍 检查最近的AudioRecord错误..."
echo "🔍 Checking recent AudioRecord errors..."
adb logcat -d | grep -E "❌.*AudioRecord read failed" | tail -5

echo ""
echo "📈 统计AudioRecord读取失败次数..."
echo "📈 Counting AudioRecord read failures..."
FAILURE_COUNT=$(adb logcat -d | grep -c "❌.*AudioRecord read failed" || echo "0")
echo "AudioRecord读取失败次数: $FAILURE_COUNT"
echo "AudioRecord read failure count: $FAILURE_COUNT"

if [ "$FAILURE_COUNT" -lt 10 ]; then
    echo "✅ AudioRecord读取失败显著减少，修复生效！"
    echo "✅ AudioRecord read failures significantly reduced, fix is working!"
else
    echo "⚠️ AudioRecord读取失败仍然较多，可能需要进一步调试"
    echo "⚠️ AudioRecord read failures still high, may need further debugging"
fi

echo ""
echo "🎯 测试完成！请查看上述日志分析结果。"
echo "🎯 Test completed! Please review the log analysis above."

