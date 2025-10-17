#!/bin/bash

# 下载真正的SenseVoice多语言模型（支持中英日韩粤）
# 官方模型：FunAudioLLM/SenseVoiceSmall

set -e

echo "======================================"
echo "下载 SenseVoice 多语言 ASR 模型"
echo "支持语言：中文(zh)、英文(en)、日文(ja)、韩文(ko)、粤语(yue)"
echo "======================================"

# 模型下载URL
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2"

# 目标目录
ASSETS_DIR="app/src/main/assets"
TARGET_DIR="$ASSETS_DIR"
MODEL_NAME="sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"

echo ""
echo "📥 步骤1：下载模型..."
echo "URL: $MODEL_URL"

# 创建临时目录
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

# 下载模型
curl -L -o model.tar.bz2 "$MODEL_URL"

echo ""
echo "📦 步骤2：解压模型..."
tar -xjf model.tar.bz2

echo ""
echo "🗑️  步骤3：删除旧模型..."
cd -
rm -rf "$ASSETS_DIR/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"

echo ""
echo "📁 步骤4：移动新模型到assets..."
mv "$TEMP_DIR/$MODEL_NAME" "$TARGET_DIR/"

echo ""
echo "🧹 步骤5：清理临时文件..."
rm -rf "$TEMP_DIR"

echo ""
echo "✅ SenseVoice多语言模型下载完成！"
echo ""
echo "模型信息："
echo "  位置: $TARGET_DIR/$MODEL_NAME"
echo "  支持语言: 中文、英文、日文、韩文、粤语"
echo "  模型类型: SenseVoice (自动语言检测)"
echo ""
echo "📝 接下来需要修改代码中的模型路径："
echo "  文件: app/src/main/kotlin/org/stypox/dicio/io/input/sherpa_simulate/SherpaOnnxManager.kt"
echo "  修改: val modelDir = \"$MODEL_NAME\""
echo ""

