#!/bin/bash

# 测试并发音频访问
echo "🔍 测试Android AudioRecord并发访问..."

# 构建应用
echo "📦 构建应用..."
./gradlew assembleWithModelsDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

# 安装应用
echo "📱 安装应用..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

# 启动应用
echo "🚀 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

# 等待应用启动
sleep 3

# 启动悬浮窗（这会启动WakeService）
echo "🎈 启动悬浮窗..."
adb shell am start -n org.stypox.dicio/.ui.floating.FloatingWindowService

sleep 2

# 检查WakeService是否运行
echo "🔍 检查WakeService状态..."
adb shell "ps | grep org.stypox.dicio"

# 模拟点击能量球启动ASR（这会启动SenseVoiceInputDevice的AudioRecord）
echo "🎤 模拟启动ASR..."
adb shell input tap 500 500  # 假设能量球在这个位置

# 监控日志，查看是否有AudioRecord冲突
echo "📊 监控音频冲突日志..."
timeout 10 adb logcat -s "WakeService:*" "SenseVoiceInputDevice:*" "AudioRecord:*" | grep -E "(冲突|conflict|busy|already|failed|error)" || echo "✅ 未发现明显的AudioRecord冲突"

echo "🏁 测试完成"
