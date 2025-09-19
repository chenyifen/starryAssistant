#!/bin/bash

echo "🧪 测试SherpaOnnx唤醒词集成"
echo "================================"

echo "📱 1. 构建并安装应用..."
./gradlew assembleWithModelsDebug
if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "📦 2. 安装APK到设备..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk
if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "🔍 3. 检查应用设置..."
echo "请手动验证以下内容："
echo "- 打开Dicio应用"
echo "- 进入Settings"
echo "- 检查'Wake word recognition method'是否显示为'SherpaOnnx KWS'"
echo "- 检查描述是否不再提及'Hey Dicio'"

echo ""
echo "🎤 4. 测试唤醒词功能..."
echo "请测试以下唤醒词："
echo "- 하이넛지 (韩语)"
echo "- 小艺小艺 (中文)"
echo "- hey dicio (英文)"

echo ""
echo "📊 5. 查看日志..."
echo "运行以下命令查看实时日志："
echo "adb logcat | grep -E 'SherpaOnnx|🎯|🎉|STT'"

echo ""
echo "✅ 测试完成！"
echo "如果唤醒词检测成功，应该能看到："
echo "1. SherpaOnnx检测到关键词的日志"
echo "2. 应用界面打开并开始STT"
echo "3. 能够正常进行语音识别"
