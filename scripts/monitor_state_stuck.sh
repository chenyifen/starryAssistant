#!/bin/bash

# 实时监控状态卡住问题
# 检测LISTENING状态持续时间，超过阈值时报警

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

STUCK_THRESHOLD=${1:-15}  # 默认15秒视为卡住

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  状态卡住实时监控${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "卡住阈值: ${GREEN}${STUCK_THRESHOLD}秒${NC}"
echo -e "按 Ctrl+C 退出"
echo ""

last_state=""
state_start_time=0
listening_count=0
stuck_count=0

# 实时解析日志
adb logcat -v time -s "AsrHandler:*" "VoiceAssistantStateProvider:*" "EnhancedFloatingWindowService:*" "AutoTest:*" 2>/dev/null | while read -r line; do
    timestamp=$(echo "$line" | grep -oE "^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}" || echo "")
    current_time=$(date +%s)
    
    # 检测状态变化
    if echo "$line" | grep -qE "LISTENING|setActive.*LISTENING"; then
        if [ "$last_state" != "LISTENING" ]; then
            last_state="LISTENING"
            state_start_time=$current_time
            ((listening_count++))
            echo -e "${CYAN}[$timestamp]${NC} ${YELLOW}>>> LISTENING 开始${NC} (第${listening_count}次)"
        fi
    elif echo "$line" | grep -qE "IDLE|setIdle|AsrHandler 已停止"; then
        if [ "$last_state" = "LISTENING" ]; then
            duration=$((current_time - state_start_time))
            last_state="IDLE"
            if [ $duration -ge $STUCK_THRESHOLD ]; then
                ((stuck_count++))
                echo -e "${CYAN}[$timestamp]${NC} ${RED}<<< LISTENING 结束 (持续${duration}秒 - 超时!)${NC} [卡住次数:$stuck_count]"
            else
                echo -e "${CYAN}[$timestamp]${NC} ${GREEN}<<< LISTENING 结束 (持续${duration}秒)${NC}"
            fi
        fi
    fi
    
    # 实时检测卡住
    if [ "$last_state" = "LISTENING" ] && [ $state_start_time -gt 0 ]; then
        duration=$((current_time - state_start_time))
        if [ $duration -ge $STUCK_THRESHOLD ]; then
            echo -e "${RED}!!! 警告: LISTENING状态已持续${duration}秒，可能卡住 !!!${NC}"
        fi
    fi
    
    # 显示关键日志
    if echo "$line" | grep -qE "唤醒词检测|Wake word detected|Final识别|silenceTimeout|错误|Error|Exception"; then
        echo -e "${CYAN}[$timestamp]${NC} $line"
    fi
done

