#!/bin/bash

# 测试技能匹配调试日志
echo "🧪 测试技能匹配调试功能"
echo "========================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📱 检查设备连接...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 没有检测到Android设备${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 设备连接正常${NC}"

echo -e "\n${BLUE}🏗️ 编译noModels变体...${NC}"
if ./gradlew assembleNoModelsDebug; then
    echo -e "${GREEN}✅ 编译成功${NC}"
else
    echo -e "${RED}❌ 编译失败${NC}"
    exit 1
fi

echo -e "\n${BLUE}📦 安装APK...${NC}"
APK_PATH="app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
if [ -f "$APK_PATH" ]; then
    if adb install -r "$APK_PATH"; then
        echo -e "${GREEN}✅ APK安装成功${NC}"
    else
        echo -e "${RED}❌ APK安装失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ 找不到APK文件${NC}"
    exit 1
fi

echo -e "\n${BLUE}🔍 清除日志并开始监听...${NC}"
adb logcat -c

echo -e "${YELLOW}请现在启动Dicio应用并说'天气怎么样'...${NC}"
echo -e "${BLUE}正在监听技能匹配调试日志...${NC}"

# 监听技能匹配相关的日志
adb logcat | grep -E "(SkillEvaluator|SkillRanker)" --line-buffered | while read line; do
    if echo "$line" | grep -q "🎯.*开始技能匹配"; then
        echo -e "${GREEN}$line${NC}"
    elif echo "$line" | grep -q "🔍.*尝试匹配输入"; then
        echo -e "${BLUE}$line${NC}"
    elif echo "$line" | grep -q "✅.*找到匹配技能"; then
        echo -e "${GREEN}$line${NC}"
    elif echo "$line" | grep -q "❌.*没有找到匹配"; then
        echo -e "${RED}$line${NC}"
    elif echo "$line" | grep -q "🔄.*使用fallback"; then
        echo -e "${YELLOW}$line${NC}"
    elif echo "$line" | grep -q "📊.*技能数量"; then
        echo -e "${BLUE}$line${NC}"
    elif echo "$line" | grep -q "🔴.*第一轮\|🟡.*第二轮\|🟢.*第三轮"; then
        echo -e "${YELLOW}$line${NC}"
    elif echo "$line" | grep -q "📝.*:"; then
        echo -e "  $line"
    elif echo "$line" | grep -q "🏆.*最佳技能"; then
        echo -e "${GREEN}$line${NC}"
    else
        echo "$line"
    fi
done
