#!/bin/bash

# 检查HiNudge模型的完整性和加载状态
echo "🔍 检查HiNudge模型状态..."

# 检查外部存储中的模型文件
EXTERNAL_MODEL_DIR="/storage/emulated/0/Dicio/models/openWakeWord"

echo "📱 检查外部存储模型文件..."
adb shell "ls -la $EXTERNAL_MODEL_DIR/" 2>/dev/null || echo "❌ 外部存储目录不存在或无法访问"

echo ""
echo "📄 检查必需的模型文件:"
for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
    if adb shell "test -f $EXTERNAL_MODEL_DIR/$file && echo 'exists'" | grep -q "exists"; then
        size=$(adb shell "stat -c%s $EXTERNAL_MODEL_DIR/$file" 2>/dev/null)
        echo "✅ $file (${size} bytes)"
    else
        echo "❌ $file - 文件不存在"
    fi
done

echo ""
echo "🏗️ 安装应用并测试..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

echo ""
echo "🚀 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

sleep 2

echo ""
echo "🎈 启动悬浮窗..."
adb shell am start -n org.stypox.dicio/.ui.floating.FloatingWindowService

sleep 2

echo ""
echo "📊 监控HiNudge模型加载日志..."
echo "请在设置中选择'하이넛지 (Hi Nudge Korean)'唤醒设备"
echo ""

# 监控相关日志
timeout 20 adb logcat -s "HiNudgeOpenWakeWordDevice:*" "WakeService:*" | grep -E "(HiNudge|模型|Model|Loading|Loaded|Error|❌|✅|🔄)" || echo "监控完成"

echo ""
echo "🏁 检查完成"
echo ""
echo "📋 如果模型加载失败，请确保:"
echo "1. 外部存储目录存在: $EXTERNAL_MODEL_DIR"
echo "2. 三个模型文件都存在且大小正确"
echo "3. 在设置中选择了'하이넛지 (Hi Nudge Korean)'唤醒设备"
