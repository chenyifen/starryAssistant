#!/bin/bash

# 测试外部存储Vosk模型自动检查和拷贝功能
# 此脚本用于验证noModels变体下的外部模型检查功能

set -e

echo "🧪 测试外部存储Vosk模型自动检查功能"
echo "=================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
EXTERNAL_MODELS_PATH="/storage/emulated/0/Dicio/models/vosk"
TEST_LANGUAGES=("en" "cn" "ko")

# 语言代码到完整模型名称的映射
declare -A LANGUAGE_TO_MODEL_NAME=(
    ["en"]="vosk-model-small-en-us-0.15"
    ["cn"]="vosk-model-small-cn-0.22"
    ["ko"]="vosk-model-small-ko-0.22"
)

echo -e "${BLUE}📱 检查设备连接...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 没有检测到Android设备，请确保设备已连接并启用USB调试${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 设备连接正常${NC}"

echo -e "\n${BLUE}🏗️ 编译noModels变体...${NC}"
if ./gradlew assembleNoModelsDebug; then
    echo -e "${GREEN}✅ noModels变体编译成功${NC}"
else
    echo -e "${RED}❌ noModels变体编译失败${NC}"
    exit 1
fi

echo -e "\n${BLUE}📂 检查外部存储模型目录结构...${NC}"
for lang in "${TEST_LANGUAGES[@]}"; do
    echo -e "${YELLOW}检查语言: $lang${NC}"
    
    # 获取完整模型名称
    model_name="${LANGUAGE_TO_MODEL_NAME[$lang]}"
    
    # 检查多种可能的路径
    paths_to_check=(
        "$EXTERNAL_MODELS_PATH/$lang"
        "$EXTERNAL_MODELS_PATH/$model_name"
    )
    
    found_model=false
    for path in "${paths_to_check[@]}"; do
        if adb shell "test -d $path" 2>/dev/null; then
            echo -e "  ${GREEN}✅ 外部存储中存在模型目录: $path${NC}"
            found_model=true
            
            # 检查关键文件
            if adb shell "test -f $path/ivector" 2>/dev/null; then
                echo -e "  ${GREEN}✅ 发现关键文件 ivector${NC}"
            else
                echo -e "  ${YELLOW}⚠️  缺少关键文件 ivector${NC}"
            fi
            
            # 列出模型文件
            echo -e "  ${BLUE}📄 模型文件列表:${NC}"
            adb shell "ls -la $path" 2>/dev/null | head -10
            break
        fi
    done
    
    if [ "$found_model" = false ]; then
        echo -e "  ${YELLOW}⚠️  外部存储中不存在 $lang 模型（检查了语言代码和完整模型名称）${NC}"
        echo -e "  ${BLUE}💡 请将模型放置在以下任一路径:${NC}"
        for path in "${paths_to_check[@]}"; do
            echo -e "    - $path"
        done
    fi
done

echo -e "\n${BLUE}📦 安装测试APK...${NC}"
APK_PATH="app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
if [ -f "$APK_PATH" ]; then
    if adb install -r "$APK_PATH"; then
        echo -e "${GREEN}✅ APK安装成功${NC}"
    else
        echo -e "${RED}❌ APK安装失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ 找不到APK文件: $APK_PATH${NC}"
    exit 1
fi

echo -e "\n${BLUE}🔍 启动应用并检查日志...${NC}"
echo -e "${YELLOW}请手动启动Dicio应用，然后观察以下日志输出...${NC}"
echo -e "${BLUE}查找关键日志标签: AssetModelManager, VoskInputDevice${NC}"

# 清除旧日志
adb logcat -c

echo -e "\n${YELLOW}正在监听日志... (按Ctrl+C停止)${NC}"
echo -e "${BLUE}关键日志模式:${NC}"
echo -e "  - ${GREEN}✅ External Vosk model check for [lang]: true${NC}"
echo -e "  - ${GREEN}✅ Auto-copying Vosk model from external storage${NC}"
echo -e "  - ${GREEN}✅ Successfully copied Vosk model for [lang] from external storage${NC}"

# 监听相关日志
adb logcat | grep -E "(AssetModelManager|VoskInputDevice)" --line-buffered | while read line; do
    if echo "$line" | grep -q "External Vosk model check"; then
        echo -e "${BLUE}🔍 $line${NC}"
    elif echo "$line" | grep -q "Auto-copying.*external storage"; then
        echo -e "${GREEN}📥 $line${NC}"
    elif echo "$line" | grep -q "Successfully copied.*external storage"; then
        echo -e "${GREEN}✅ $line${NC}"
    elif echo "$line" | grep -q "Failed to copy.*external storage"; then
        echo -e "${RED}❌ $line${NC}"
    elif echo "$line" | grep -q "External Vosk model not found"; then
        echo -e "${YELLOW}⚠️  $line${NC}"
    else
        echo "$line"
    fi
done
