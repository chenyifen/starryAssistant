#!/bin/bash

# 测试WakeService和ASR的独立运行
echo "🔍 测试WakeService和ASR独立运行..."

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

echo "📊 开始监控日志..."
echo "请说唤醒词测试唤醒功能..."

# 监控日志，查看WakeService和ASR的独立运行情况
timeout 30 adb logcat -s "WakeService:*" "SenseVoiceInputDevice:*" "FloatingWindowViewModel:*" | grep -E "(Wake word detected|Starting STT|AudioRecord|监听|唤醒|识别)" || echo "✅ 监控完成"

echo "🏁 测试完成"
echo ""
echo "📋 测试要点："
echo "1. WakeService应该持续在后台监听"
echo "2. 检测到唤醒词后自动启动ASR"
echo "3. ASR和WakeService使用独立的AudioRecord实例"
echo "4. 两者互不干扰，可以并行运行"
