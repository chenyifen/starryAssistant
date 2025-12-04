#!/bin/bash

# 快速唤醒测试 - 模拟快速连续唤醒场景
# 这个场景最容易触发状态卡住问题

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}快速连续唤醒测试${NC}"
echo -e "模拟用户快速连续唤醒的场景"
echo ""

# 清空日志
adb logcat -c

echo -e "${YELLOW}发送5次快速唤醒 (间隔500ms)...${NC}"
for i in {1..5}; do
    echo -e "  发送第 $i 次..."
    adb shell am broadcast -a com.ai.voice.AUTO_TEST_START
    sleep 0.5
done

echo -e "\n${YELLOW}等待10秒观察状态...${NC}"
sleep 10

echo -e "\n${BLUE}检查最终状态:${NC}"
adb logcat -d -s "AsrHandler:I" "VoiceAssistantStateProvider:I" | tail -30

echo -e "\n${BLUE}检查是否有错误:${NC}"
adb logcat -d | grep -iE "error|exception|stuck" | tail -10 || echo "无明显错误"

echo -e "\n${BLUE}检查isStarted状态:${NC}"
adb logcat -d -s "AsrHandler:*" | grep -E "isStarted|已启动|已停止" | tail -5

