#!/bin/bash

# 分析状态卡住问题的诊断脚本
# 抓取完整日志并分析状态转换链

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

LOG_FILE="/tmp/state_analysis_$(date +%Y%m%d_%H%M%S).log"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  状态卡住问题诊断${NC}"
echo -e "${BLUE}========================================${NC}"

# 抓取最近的日志
echo -e "\n${YELLOW}抓取日志中...${NC}"
adb logcat -d > "$LOG_FILE"

echo -e "${GREEN}日志保存到: $LOG_FILE${NC}"

# 分析
echo -e "\n${BLUE}=== 状态转换分析 ===${NC}"
echo -e "\n${YELLOW}1. LISTENING状态变化:${NC}"
grep -E "LISTENING|Listening" "$LOG_FILE" | grep -E "AsrHandler|VoiceAssistant|FloatingOrb" | tail -20

echo -e "\n${YELLOW}2. IDLE状态变化:${NC}"
grep -E "IDLE|setIdle|已停止" "$LOG_FILE" | grep -E "AsrHandler|VoiceAssistant|FloatingOrb" | tail -20

echo -e "\n${YELLOW}3. AsrHandler start/stop:${NC}"
grep -E "AsrHandler" "$LOG_FILE" | grep -E "启动|停止|start|stop|isStarted" | tail -20

echo -e "\n${YELLOW}4. 静音超时检测:${NC}"
grep -E "silenceTimeout|静音超时|SILENCE_TIMEOUT" "$LOG_FILE" | tail -10

echo -e "\n${YELLOW}5. Channel/协程相关:${NC}"
grep -E "channel|Channel|coroutine|Job|cancel" "$LOG_FILE" | grep -E "AsrHandler" | tail -10

echo -e "\n${YELLOW}6. 错误和异常:${NC}"
grep -iE "error|exception|crash|fail" "$LOG_FILE" | grep -E "AsrHandler|VoiceAssistant|Wake" | tail -10

echo -e "\n${BLUE}=== 状态时间线 ===${NC}"
echo -e "${YELLOW}最近50条状态相关日志:${NC}"
grep -E "LISTENING|IDLE|Wake|ASR|唤醒|停止|启动" "$LOG_FILE" | \
    grep -E "AsrHandler|VoiceAssistant|EnhancedFloating" | \
    tail -50

echo -e "\n${BLUE}=== isStarted标志检查 ===${NC}"
grep "isStarted" "$LOG_FILE" | tail -10

echo -e "\n${BLUE}诊断完成。完整日志: $LOG_FILE${NC}"

