#!/bin/bash

# 推送韩语唤醒词模型到Android设备的外部存储
# 目标路径: /storage/emulated/0/Dicio/models/openWakeWord/

set -e

echo "🇰🇷 推送韩语唤醒词模型到外部存储..."

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有找到连接的Android设备"
    exit 1
fi

echo "📱 检测到Android设备"

# 检查本地模型文件
KOREAN_MODEL_DIR="models/openwakeword_korean_minimal"
if [ ! -d "$KOREAN_MODEL_DIR" ]; then
    echo "❌ 本地韩语模型目录不存在: $KOREAN_MODEL_DIR"
    exit 1
fi

echo "📂 检查本地韩语模型文件..."
for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
    if [ ! -f "$KOREAN_MODEL_DIR/$file" ]; then
        echo "❌ 模型文件不存在: $KOREAN_MODEL_DIR/$file"
        exit 1
    fi
    echo "✅ $file ($(du -h "$KOREAN_MODEL_DIR/$file" | cut -f1))"
done

# 创建设备上的目标目录
TARGET_DIR="/storage/emulated/0/Dicio/models/openWakeWord"
echo "📁 创建设备上的目标目录: $TARGET_DIR"
adb shell "mkdir -p '$TARGET_DIR'" || {
    echo "❌ 无法创建目录，可能需要存储权限"
    echo "💡 请在应用中授予存储权限，或手动创建目录"
}

# 推送模型文件
echo "📤 推送韩语唤醒词模型文件..."

echo "  📤 推送 melspectrogram.tflite..."
adb push "$KOREAN_MODEL_DIR/melspectrogram.tflite" "$TARGET_DIR/melspectrogram.tflite"

echo "  📤 推送 embedding.tflite..."
adb push "$KOREAN_MODEL_DIR/embedding.tflite" "$TARGET_DIR/embedding.tflite"

echo "  📤 推送 wake.tflite..."
adb push "$KOREAN_MODEL_DIR/wake.tflite" "$TARGET_DIR/wake.tflite"

# 推送模型元数据
if [ -f "$KOREAN_MODEL_DIR/model_metadata.json" ]; then
    echo "  📤 推送 model_metadata.json..."
    adb push "$KOREAN_MODEL_DIR/model_metadata.json" "$TARGET_DIR/model_metadata.json"
fi

if [ -f "$KOREAN_MODEL_DIR/README.md" ]; then
    echo "  📤 推送 README.md..."
    adb push "$KOREAN_MODEL_DIR/README.md" "$TARGET_DIR/README.md"
fi

# 验证推送结果
echo "🔍 验证推送结果..."
adb shell "ls -la '$TARGET_DIR/'" || {
    echo "⚠️ 无法列出目录内容，可能权限不足"
}

# 检查文件大小
echo "📊 检查推送的文件大小..."
for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
    local_size=$(stat -f%z "$KOREAN_MODEL_DIR/$file" 2>/dev/null || stat -c%s "$KOREAN_MODEL_DIR/$file" 2>/dev/null || echo "unknown")
    remote_size=$(adb shell "stat -c%s '$TARGET_DIR/$file'" 2>/dev/null || echo "unknown")
    
    if [ "$local_size" = "$remote_size" ]; then
        echo "  ✅ $file: $local_size bytes (匹配)"
    else
        echo "  ⚠️ $file: 本地=$local_size, 远程=$remote_size (不匹配)"
    fi
done

echo ""
echo "✅ 韩语唤醒词模型推送完成！"
echo ""
echo "📋 推送的文件："
echo "  • $TARGET_DIR/melspectrogram.tflite"
echo "  • $TARGET_DIR/embedding.tflite"
echo "  • $TARGET_DIR/wake.tflite"
echo ""
echo "🔄 现在应用会优先使用外部存储中的模型文件"
echo "💡 如果要使用assets中的模型，请删除外部存储中的文件："
echo "   adb shell \"rm -rf '$TARGET_DIR'\""
echo ""
echo "🧪 测试步骤："
echo "1. 构建并安装应用: ./gradlew assembleWithModelsDebug && adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk"
echo "2. 切换语言到韩语"
echo "3. 启用OpenWakeWord唤醒"
echo "4. 说'하이넛지'测试唤醒"
echo ""
echo "📊 监控日志: adb logcat | grep -E '(LanguageWakeWordManager|External.*Korean)'"
