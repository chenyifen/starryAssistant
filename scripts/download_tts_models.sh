#!/bin/bash

# SherpaOnnx TTS模型下载脚本
# 支持中文、韩语、英文TTS模型

set -e

echo "🔊 SherpaOnnx TTS模型下载脚本"
echo "==============================="

# 创建模型目录
MODELS_DIR="app/src/withModels/assets/models/tts"
mkdir -p "$MODELS_DIR"

# 模型信息数组（使用简单数组替代关联数组）
LANGUAGES=("zh" "en" "ko")
MODEL_URLS=(
    # 中文模型 - VITS中文多说话人模型
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-fanchen-C.tar.bz2"
    
    # 英文模型 - Piper英文女声模型
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2"
    
    # 韩语模型 - VITS Mimic3 韩语模型
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-ko_KO-kss_low.tar.bz2"
)

# 获取语言对应的URL索引
get_url_index() {
    local lang=$1
    case $lang in
        "zh") echo 0 ;;
        "en") echo 1 ;;
        "ko") echo 2 ;;
        *) echo -1 ;;
    esac
}

# 下载并解压模型
download_model() {
    local lang=$1
    local url_index=$(get_url_index "$lang")
    
    if [ $url_index -eq -1 ]; then
        echo "❌ 不支持的语言: $lang"
        return 1
    fi
    
    local url=${MODEL_URLS[$url_index]}
    local filename=$(basename "$url")
    local model_name=${filename%.tar.bz2}
    
    echo ""
    echo "📦 下载 $lang 语言模型: $model_name"
    
    # 检查是否已存在
    if [ -d "$MODELS_DIR/$model_name" ]; then
        echo "✅ 模型已存在: $MODELS_DIR/$model_name"
        return 0
    fi
    
    # 下载模型
    echo "⬇️ 正在下载: $url"
    if ! wget -q --show-progress -O "/tmp/$filename" "$url"; then
        echo "❌ 下载失败: $url"
        return 1
    fi
    
    # 解压模型
    echo "📂 正在解压到: $MODELS_DIR"
    if ! tar -xjf "/tmp/$filename" -C "$MODELS_DIR"; then
        echo "❌ 解压失败: $filename"
        rm -f "/tmp/$filename"
        return 1
    fi
    
    # 清理临时文件
    rm -f "/tmp/$filename"
    
    echo "✅ $lang 语言模型下载完成"
    
    # 显示模型信息
    if [ -d "$MODELS_DIR/$model_name" ]; then
        echo "📋 模型文件列表:"
        ls -la "$MODELS_DIR/$model_name" | head -10
        
        # 计算模型大小
        local size=$(du -sh "$MODELS_DIR/$model_name" | cut -f1)
        echo "💾 模型大小: $size"
    fi
}

# 检查依赖
check_dependencies() {
    echo "🔍 检查依赖工具..."
    
    if ! command -v wget &> /dev/null; then
        echo "❌ 需要安装 wget"
        echo "Ubuntu/Debian: sudo apt-get install wget"
        echo "macOS: brew install wget"
        exit 1
    fi
    
    if ! command -v tar &> /dev/null; then
        echo "❌ 需要安装 tar"
        exit 1
    fi
    
    echo "✅ 依赖检查通过"
}

# 主函数
main() {
    check_dependencies
    
    echo ""
    echo "🎯 开始下载TTS模型..."
    echo "目标目录: $MODELS_DIR"
    
    # 下载所有模型
    local success_count=0
    local total_count=${#LANGUAGES[@]}
    
    for lang in "${LANGUAGES[@]}"; do
        if download_model "$lang"; then
            ((success_count++))
        else
            echo "⚠️ $lang 语言模型下载失败，继续下载其他模型..."
        fi
    done
    
    echo ""
    echo "📊 下载完成统计:"
    echo "✅ 成功: $success_count/$total_count"
    
    if [ $success_count -eq $total_count ]; then
        echo "🎉 所有TTS模型下载完成！"
        
        # 显示总体信息
        echo ""
        echo "📁 模型目录结构:"
        tree "$MODELS_DIR" 2>/dev/null || ls -la "$MODELS_DIR"
        
        echo ""
        echo "💾 总模型大小:"
        du -sh "$MODELS_DIR"
        
        echo ""
        echo "🚀 现在可以使用 ./withModels.sh 构建包含TTS模型的应用"
        
    else
        echo "⚠️ 部分模型下载失败，请检查网络连接后重试"
        exit 1
    fi
}

# 显示帮助信息
show_help() {
    echo "SherpaOnnx TTS模型下载脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help     显示此帮助信息"
    echo "  -l, --list     列出支持的语言"
    echo "  -c, --clean    清理已下载的模型"
    echo ""
    echo "支持的语言:"
    echo "  zh - 中文 (VITS多说话人模型)"
    echo "  en - 英文 (Piper女声模型)"
    echo "  ko - 韩语 (Kokoro多语言模型)"
}

# 列出支持的语言
list_languages() {
    echo "支持的TTS语言:"
    echo "=============="
    for i in "${!LANGUAGES[@]}"; do
        local lang=${LANGUAGES[$i]}
        local url=${MODEL_URLS[$i]}
        echo "  $lang: $url"
    done
}

# 清理模型
clean_models() {
    echo "🧹 清理TTS模型..."
    if [ -d "$MODELS_DIR" ]; then
        rm -rf "$MODELS_DIR"
        echo "✅ 模型目录已清理: $MODELS_DIR"
    else
        echo "ℹ️ 模型目录不存在: $MODELS_DIR"
    fi
}

# 处理命令行参数
case "${1:-}" in
    -h|--help)
        show_help
        exit 0
        ;;
    -l|--list)
        list_languages
        exit 0
        ;;
    -c|--clean)
        clean_models
        exit 0
        ;;
    "")
        main
        ;;
    *)
        echo "❌ 未知选项: $1"
        echo "使用 $0 --help 查看帮助信息"
        exit 1
        ;;
esac
