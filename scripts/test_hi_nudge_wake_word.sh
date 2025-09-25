#!/bin/bash

# HiNudge韩语唤醒词测试脚本
# 测试独立的HiNudgeOpenWakeWordDevice设备

set -e

echo "🇰🇷 HiNudge韩语唤醒词测试脚本"
echo "================================"

# 检查ADB连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 未检测到Android设备，请确保设备已连接并启用USB调试"
    exit 1
fi

echo "📱 检测到Android设备"

# 1. 推送韩语模型到外部存储
echo ""
echo "📦 步骤1: 推送韩语模型到外部存储..."
EXTERNAL_MODEL_DIR="/storage/emulated/0/Dicio/models/openWakeWord"

# 创建目录
adb shell "mkdir -p $EXTERNAL_MODEL_DIR" || true

# 检查本地模型文件
LOCAL_MODEL_DIR="./models/openwakeword_korean_minimal"
if [ ! -d "$LOCAL_MODEL_DIR" ]; then
    echo "❌ 本地韩语模型目录不存在: $LOCAL_MODEL_DIR"
    echo "💡 请确保韩语模型文件存在于该目录"
    exit 1
fi

# 推送模型文件
echo "📤 推送melspectrogram.tflite..."
adb push "$LOCAL_MODEL_DIR/melspectrogram.tflite" "$EXTERNAL_MODEL_DIR/"

echo "📤 推送embedding.tflite..."
adb push "$LOCAL_MODEL_DIR/embedding.tflite" "$EXTERNAL_MODEL_DIR/"

echo "📤 推送wake.tflite..."
adb push "$LOCAL_MODEL_DIR/wake.tflite" "$EXTERNAL_MODEL_DIR/"

echo "✅ 韩语模型推送完成"

# 2. 验证模型文件
echo ""
echo "🔍 步骤2: 验证外部存储中的模型文件..."
adb shell "ls -la $EXTERNAL_MODEL_DIR/"

# 3. 编译并安装应用
echo ""
echo "🔨 步骤3: 编译并安装应用..."
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
./gradlew assembleWithModelsDebug
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

echo "✅ 应用安装完成"

# 4. 启动应用
echo ""
echo "🚀 步骤4: 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

echo "✅ 应用已启动"

# 5. 监控日志
echo ""
echo "📊 步骤5: 监控HiNudge设备日志..."
echo "请按照以下步骤进行测试:"
echo ""
echo "1. 在应用设置中选择唤醒方法为 '하이넛지 (Hi Nudge Korean)'"
echo "2. 播放韩语训练音频或说出韩语唤醒词"
echo "3. 观察下方日志输出"
echo ""
echo "🔍 监控关键日志标签:"
echo "  - HiNudgeOpenWakeWordDevice: HiNudge设备相关日志"
echo "  - WakeDeviceWrapper: 设备切换日志"
echo "  - WakeService: 唤醒服务日志"
echo ""
echo "按 Ctrl+C 停止监控"
echo "================================"

# 清除旧日志并开始监控
adb logcat -c
adb logcat | grep -E "(HiNudgeOpenWakeWordDevice|WakeDeviceWrapper|WakeService|하이넛지|DETECTED)"
