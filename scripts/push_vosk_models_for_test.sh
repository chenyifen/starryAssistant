#!/bin/bash

# 推送Vosk模型到外部存储进行测试
# 此脚本用于将现有的Vosk模型推送到外部存储，测试自动检查和拷贝功能

set -e

echo "📦 推送Vosk模型到外部存储进行测试"
echo "=================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
EXTERNAL_MODELS_PATH="/storage/emulated/0/Dicio/models/vosk"
LOCAL_MODELS_PATH="external_models/vosk"

echo -e "${BLUE}📱 检查设备连接...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 没有检测到Android设备，请确保设备已连接并启用USB调试${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 设备连接正常${NC}"

echo -e "\n${BLUE}📂 检查本地模型文件...${NC}"
if [ ! -d "$LOCAL_MODELS_PATH" ]; then
    echo -e "${YELLOW}⚠️  本地模型目录不存在: $LOCAL_MODELS_PATH${NC}"
    echo -e "${BLUE}💡 请先下载模型文件到本地，或者手动创建测试模型目录${NC}"
    echo -e "${BLUE}   示例：从withModels变体的assets中提取模型文件${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 发现本地模型目录${NC}"

echo -e "\n${BLUE}🗂️  创建外部存储目录结构...${NC}"
adb shell "mkdir -p $EXTERNAL_MODELS_PATH" 2>/dev/null || true

echo -e "\n${BLUE}📤 推送模型文件...${NC}"
for model_dir in "$LOCAL_MODELS_PATH"/*; do
    if [ -d "$model_dir" ]; then
        model_name=$(basename "$model_dir")
        echo -e "${YELLOW}推送模型: $model_name${NC}"
        
        # 检查模型是否包含关键文件
        if [ -f "$model_dir/ivector" ]; then
            echo -e "  ${GREEN}✅ 发现关键文件 ivector${NC}"
            
            # 推送整个模型目录
            echo -e "  ${BLUE}📤 推送到设备...${NC}"
            adb push "$model_dir" "$EXTERNAL_MODELS_PATH/" 2>/dev/null
            
            # 验证推送结果
            if adb shell "test -d $EXTERNAL_MODELS_PATH/$model_name" 2>/dev/null; then
                echo -e "  ${GREEN}✅ 推送成功${NC}"
            else
                echo -e "  ${RED}❌ 推送失败${NC}"
            fi
        else
            echo -e "  ${YELLOW}⚠️  跳过（缺少关键文件 ivector）${NC}"
        fi
    fi
done

echo -e "\n${BLUE}🔍 验证外部存储模型...${NC}"
adb shell "ls -la $EXTERNAL_MODELS_PATH" 2>/dev/null || echo -e "${YELLOW}⚠️  无法列出外部存储目录${NC}"

echo -e "\n${GREEN}✅ 模型推送完成！${NC}"
echo -e "${BLUE}💡 现在可以运行测试脚本验证自动检查功能:${NC}"
echo -e "   ./test_external_vosk_models.sh"

echo -e "\n${YELLOW}📝 测试说明:${NC}"
echo -e "1. 确保使用 noModels 变体编译应用"
echo -e "2. 启动应用并切换到支持的语言"
echo -e "3. 观察日志输出，查看自动检查和拷贝过程"
echo -e "4. 验证模型是否成功从外部存储拷贝到应用内部存储"
