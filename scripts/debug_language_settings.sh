#!/bin/bash

# 调试语言设置和技能可用性
echo "🔍 调试语言设置和技能可用性"
echo "=========================="

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

echo -e "\n${BLUE}🔍 清除日志...${NC}"
adb logcat -c

echo -e "\n${YELLOW}请启动Dicio应用，等待初始化完成...${NC}"
echo -e "${BLUE}正在监听语言和技能相关日志...${NC}"

# 监听语言和技能相关的日志
adb logcat | grep -E "(WeatherInfo|SkillHandler|LocaleManager.*语言|sentencesLanguage)" --line-buffered | while read line; do
    if echo "$line" | grep -q "🌤️.*Weather技能可用性"; then
        echo -e "${GREEN}$line${NC}"
    elif echo "$line" | grep -q "📚.*Weather支持的语言"; then
        echo -e "${BLUE}$line${NC}"
    elif echo "$line" | grep -q "🔧.*技能启用检查"; then
        echo -e "${YELLOW}$line${NC}"
    elif echo "$line" | grep -q "🔍.*技能可用性检查"; then
        if echo "$line" | grep -q "available=true"; then
            echo -e "${GREEN}$line${NC}"
        else
            echo -e "${RED}$line${NC}"
        fi
    elif echo "$line" | grep -q "sentencesLanguage"; then
        echo -e "${BLUE}$line${NC}"
    elif echo "$line" | grep -q "📚.*Sentences支持的语言"; then
        echo -e "${BLUE}$line${NC}"
    elif echo "$line" | grep -q "🎯.*最终传给应用的Locale"; then
        echo -e "${GREEN}$line${NC}"
    else
        echo "$line"
    fi
done
