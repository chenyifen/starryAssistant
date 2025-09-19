#!/bin/bash

# Vosk ASR模型下载脚本
# 支持中文、韩语、英文Vosk模型
# 兼容withModels和noModels两种部署方式

set -e

echo "🎤 Vosk ASR模型下载脚本"
echo "==============================="

# 创建模型目录
ASSETS_MODELS_DIR="app/src/withModels/assets/models/vosk"
EXTERNAL_MODELS_DIR="external_models/vosk"

mkdir -p "$ASSETS_MODELS_DIR"
mkdir -p "$EXTERNAL_MODELS_DIR"

# Vosk模型信息（使用索引数组）
LANGUAGES=("cn" "ko" "en")
MODEL_URLS=(
    "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip" 
    "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
)

# 获取语言对应的URL
get_vosk_url() {
    local lang=$1
    case $lang in
        "cn") echo "${MODEL_URLS[0]}" ;;
        "ko") echo "${MODEL_URLS[1]}" ;;
        "en") echo "${MODEL_URLS[2]}" ;;
        *) echo "" ;;
    esac
}

# 下载并解压Vosk模型
download_vosk_model() {
    local lang=$1
    local url=$(get_vosk_url "$lang")
    
    if [ -z "$url" ]; then
        echo "❌ 不支持的语言: $lang"
        return 1
    fi
    
    echo "📥 下载 $lang Vosk模型..."
    echo "   URL: $url"
    
    # 获取文件名
    local filename=$(basename "$url")
    local model_name=$(basename "$filename" .zip)
    
    # 下载到临时目录
    local temp_file="/tmp/$filename"
    
    # 检查模型是否已存在
    local assets_target="$ASSETS_MODELS_DIR/$model_name"
    local external_target="$EXTERNAL_MODELS_DIR/$model_name"
    local assets_exists=false
    local external_exists=false
    
    if [ -d "$assets_target" ] && [ -f "$assets_target/README" ]; then
        echo "   ✅ Assets模型已存在: $lang"
        assets_exists=true
    fi
    
    if [ -d "$external_target" ] && [ -f "$external_target/README" ]; then
        echo "   ✅ 外部存储模型已存在: $lang"
        external_exists=true
    fi
    
    # 如果两个目录都存在，跳过下载
    if [ "$assets_exists" = true ] && [ "$external_exists" = true ]; then
        echo "   ⏭️  跳过下载，模型已完整: $lang"
        return 0
    fi
    
    # 下载模型文件（如果需要）
    if [ "$assets_exists" = false ]; then
        # 检查并清理可能损坏的文件
        if [ -f "$temp_file" ]; then
            if ! unzip -t "$temp_file" >/dev/null 2>&1; then
                echo "   🗑️  删除损坏的文件: $temp_file"
                rm -f "$temp_file"
            fi
        fi
        
        if [ ! -f "$temp_file" ]; then
            echo "   📥 下载模型文件..."
            wget --timeout=30 --tries=3 -O "$temp_file" "$url" || {
                echo "   ❌ 下载失败: $lang"
                rm -f "$temp_file"
                return 1
            }
            
            # 验证下载的文件
            if ! unzip -t "$temp_file" >/dev/null 2>&1; then
                echo "   ❌ 下载的文件损坏: $lang"
                rm -f "$temp_file"
                return 1
            fi
            echo "   ✅ 文件下载并验证成功"
        fi
        
        echo "   📦 解压到assets: $assets_target"
        mkdir -p "$assets_target"
        unzip -q "$temp_file" -d "$assets_target"
        
        # 如果解压后有子目录，将内容移到根目录
        local extracted_dir=$(find "$assets_target" -mindepth 1 -maxdepth 1 -type d | head -1)
        if [ -n "$extracted_dir" ]; then
            mv "$extracted_dir"/* "$assets_target/"
            rmdir "$extracted_dir"
        fi
        
        echo "   ✅ Assets模型准备完成: $lang"
    fi
    
    # 复制到外部存储（如果需要）
    if [ "$external_exists" = false ]; then
        echo "   📋 复制到外部存储模拟目录: $external_target"
        cp -r "$assets_target" "$external_target"
        echo "   ✅ 外部存储模型准备完成: $lang"
    fi
    
    # 清理临时文件
    rm -f "$temp_file"
    
    echo "   🎉 $lang Vosk模型下载完成"
    echo ""
}

# 显示当前支持的语言
echo "📚 支持的Vosk语言:"
for i in "${!LANGUAGES[@]}"; do
    lang="${LANGUAGES[$i]}"
    url="${MODEL_URLS[$i]}"
    echo "   - $lang: $url"
done
echo ""

# 下载所有模型
echo "🚀 开始下载Vosk模型..."
echo ""

for lang in cn ko en; do
    download_vosk_model "$lang"
done

echo "🎊 所有Vosk模型下载完成！"
echo ""
echo "📁 模型位置:"
echo "   - withModels变体: $ASSETS_MODELS_DIR"
echo "   - noModels变体: $EXTERNAL_MODELS_DIR"
echo ""
echo "📋 下一步:"
echo "   1. 编译withModels变体: ./gradlew assembleWithModelsDebug"
echo "   2. 编译noModels变体: ./gradlew assembleNoModelsDebug"
echo "   3. 使用./withModels.sh或./run.sh测试"
