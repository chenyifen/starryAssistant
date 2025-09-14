#!/bin/bash

echo "🔄 复制HandsFree兼容模型文件到Dicio项目"
echo "============================================="

# 源路径和目标路径
HANDSFREE_MODELS="/Users/user/AndroidStudioProjects/HandsFree/app/src/withModels/assets/models/kws"
DICIO_MODELS="app/src/withModels/assets/models/sherpa_onnx_kws"

# 检查HandsFree模型文件是否存在
if [ ! -d "$HANDSFREE_MODELS" ]; then
    echo "❌ HandsFree模型文件不存在: $HANDSFREE_MODELS"
    echo "请确保HandsFree项目已正确设置"
    exit 1
fi

echo "📂 源路径: $HANDSFREE_MODELS"
echo "📂 目标路径: $DICIO_MODELS"

# 创建目标目录
mkdir -p "$DICIO_MODELS"

# 复制所有模型文件
echo ""
echo "📦 复制模型文件..."
cp -v "$HANDSFREE_MODELS"/* "$DICIO_MODELS/"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 模型文件复制成功！"
    echo ""
    echo "📄 复制的文件列表:"
    ls -la "$DICIO_MODELS"
    
    echo ""
    echo "🔍 验证关键文件:"
    
    # 验证keywords.txt内容
    if [ -f "$DICIO_MODELS/keywords.txt" ]; then
        echo "📝 keywords.txt 内容:"
        cat "$DICIO_MODELS/keywords.txt"
        echo ""
    fi
    
    # 验证tokens.txt前几行
    if [ -f "$DICIO_MODELS/tokens.txt" ]; then
        echo "📝 tokens.txt 前10行:"
        head -10 "$DICIO_MODELS/tokens.txt"
        echo ""
    fi
    
    echo "🎉 现在可以使用withModels.sh构建包含兼容模型的APK了！"
else
    echo "❌ 模型文件复制失败"
    exit 1
fi
