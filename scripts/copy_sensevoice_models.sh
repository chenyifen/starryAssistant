#!/bin/bash

# 复制SenseVoice模型从HandsFree项目到Dicio项目
# 用于两阶段语音识别

set -e

# 项目路径
HANDSFREE_PROJECT="/Users/user/AndroidStudioProjects/HandsFree"
DICIO_PROJECT="/Users/user/AndroidStudioProjects/dicio-android"

# 模型源路径 (HandsFree)
HANDSFREE_MODEL_SOURCE="$HANDSFREE_PROJECT/app/src/withModels/assets/models/asr/multilingual"

# 模型目标路径 (Dicio)
DICIO_MODEL_TARGET="$DICIO_PROJECT/app/src/withModels/assets/models/asr/sensevoice"

echo "🔄 复制SenseVoice模型到Dicio..."
echo "源路径: $HANDSFREE_MODEL_SOURCE"
echo "目标路径: $DICIO_MODEL_TARGET"

# 检查源路径是否存在
if [ ! -d "$HANDSFREE_MODEL_SOURCE" ]; then
    echo "❌ 错误: HandsFree模型源路径不存在: $HANDSFREE_MODEL_SOURCE"
    exit 1
fi

# 创建目标目录
mkdir -p "$DICIO_MODEL_TARGET"

# 复制模型文件
echo "📁 创建目标目录: $DICIO_MODEL_TARGET"

# 复制所有模型文件
echo "📋 复制模型文件..."
if cp -r "$HANDSFREE_MODEL_SOURCE"/* "$DICIO_MODEL_TARGET/"; then
    echo "✅ 模型文件复制完成"
else
    echo "❌ 模型文件复制失败"
    exit 1
fi

# 列出复制的文件
echo "📄 已复制的文件:"
ls -la "$DICIO_MODEL_TARGET"

# 检查关键文件
required_files=("model.onnx" "tokens.txt")
missing_files=()

for file in "${required_files[@]}"; do
    if [ ! -f "$DICIO_MODEL_TARGET/$file" ]; then
        if [ -f "$DICIO_MODEL_TARGET/model.int8.onnx" ] && [ "$file" = "model.onnx" ]; then
            echo "ℹ️  找到量化模型: model.int8.onnx (代替 $file)"
        else
            missing_files+=("$file")
        fi
    else
        echo "✅ 发现必需文件: $file"
    fi
done

if [ ${#missing_files[@]} -ne 0 ]; then
    echo "⚠️  警告: 缺少以下必需文件:"
    for file in "${missing_files[@]}"; do
        echo "   - $file"
    done
fi

# 计算模型文件大小
total_size=$(du -sh "$DICIO_MODEL_TARGET" | cut -f1)
echo "📊 模型总大小: $total_size"

# 验证tokens文件
if [ -f "$DICIO_MODEL_TARGET/tokens.txt" ]; then
    token_count=$(wc -l < "$DICIO_MODEL_TARGET/tokens.txt")
    echo "🔤 Tokens数量: $token_count"
fi

echo ""
echo "🎉 SenseVoice模型复制完成!"
echo "💡 现在可以在Dicio中使用两阶段语音识别了"
echo ""
echo "📱 使用方法:"
echo "1. 在Dicio设置中选择 '两阶段识别' 作为输入方法"
echo "2. 享受更高精度的语音识别体验"
echo ""
echo "🔧 模型路径:"
echo "   withModels变体: $DICIO_MODEL_TARGET"
echo "   noModels变体: /storage/emulated/0/Dicio/models/sensevoice"
