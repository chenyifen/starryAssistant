#!/bin/bash

# 手动复制韩语Vosk模型脚本

echo "🔍 搜索韩语Vosk模型..."

# 搜索可能的韩语模型文件
KOREAN_FILES=$(find ~/Downloads -name "*vosk*" -o -name "*ko*" | grep -i vosk | head -10)

if [ -z "$KOREAN_FILES" ]; then
    echo "❌ 未找到韩语Vosk模型文件"
    echo "请确认文件名包含 'vosk' 和 'ko' 关键字"
    echo ""
    echo "📁 Downloads目录中包含vosk的文件:"
    find ~/Downloads -name "*vosk*" 2>/dev/null | head -5
    echo ""
    echo "📁 Downloads目录中包含ko的文件:"
    find ~/Downloads -name "*ko*" 2>/dev/null | head -5
    exit 1
fi

echo "🎯 找到可能的韩语模型文件:"
echo "$KOREAN_FILES"
echo ""

# 让用户选择文件
echo "请输入完整的文件路径，或者直接告诉我文件名："
read -p "文件路径: " KOREAN_FILE

if [ ! -f "$KOREAN_FILE" ]; then
    echo "❌ 文件不存在: $KOREAN_FILE"
    exit 1
fi

echo "📦 处理韩语模型: $KOREAN_FILE"

# 目标目录
ASSETS_TARGET="app/src/withModels/assets/models/vosk/vosk-model-small-ko-0.22"
EXTERNAL_TARGET="external_models/vosk/vosk-model-small-ko-0.22"

# 创建目录
mkdir -p "$ASSETS_TARGET"
mkdir -p "$EXTERNAL_TARGET"

# 检查文件类型并解压
if [[ "$KOREAN_FILE" == *.zip ]]; then
    echo "📦 解压zip文件到assets..."
    unzip -q "$KOREAN_FILE" -d "$ASSETS_TARGET"
    
    # 如果解压后有子目录，将内容移到根目录
    extracted_dir=$(find "$ASSETS_TARGET" -mindepth 1 -maxdepth 1 -type d | head -1)
    if [ -n "$extracted_dir" ]; then
        mv "$extracted_dir"/* "$ASSETS_TARGET/"
        rmdir "$extracted_dir"
    fi
    
elif [[ "$KOREAN_FILE" == *.tar.* ]] || [[ "$KOREAN_FILE" == *.tgz ]]; then
    echo "📦 解压tar文件到assets..."
    tar -xf "$KOREAN_FILE" -C "$ASSETS_TARGET" --strip-components=1
    
else
    echo "❓ 未知文件格式，尝试直接复制..."
    cp -r "$KOREAN_FILE" "$ASSETS_TARGET/"
fi

# 复制到外部存储
echo "📋 复制到外部存储..."
cp -r "$ASSETS_TARGET" "$EXTERNAL_TARGET"

# 验证
if [ -f "$ASSETS_TARGET/README" ] || [ -f "$ASSETS_TARGET/conf/model.conf" ]; then
    echo "✅ 韩语Vosk模型安装成功！"
    echo "   Assets: $ASSETS_TARGET"
    echo "   External: $EXTERNAL_TARGET"
else
    echo "⚠️  模型可能未正确安装，请检查文件结构"
    echo "   预期文件: README 或 conf/model.conf"
    echo "   实际内容:"
    ls -la "$ASSETS_TARGET"
fi
