#!/bin/bash

# 压力测试脚本：测试LISTENING状态卡住问题
# 通过快速连续发送唤醒/点击广播，检测UI状态是否正确切换

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
PACKAGE_NAME="com.ai.voice"
TEST_ACTION="${PACKAGE_NAME}.AUTO_TEST_START"
LOG_TAG="AutoTest|AsrHandler|VoiceAssistantStateProvider|EnhancedFloatingWindowService"
STATE_TIMEOUT_SEC=30  # 状态超时检测时间

# 测试参数
TEST_ROUNDS=${1:-10}  # 默认10轮
INTERVAL_MS=${2:-3000}  # 默认3秒间隔

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  LISTENING状态卡住压力测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "测试轮数: ${GREEN}$TEST_ROUNDS${NC}"
echo -e "间隔时间: ${GREEN}${INTERVAL_MS}ms${NC}"
echo ""

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}错误: 未检测到设备${NC}"
    exit 1
fi

# 清空日志
echo -e "${YELLOW}清空logcat日志...${NC}"
adb logcat -c

# 启动日志监控（后台）
LOG_FILE="/tmp/stress_test_$(date +%Y%m%d_%H%M%S).log"
echo -e "${YELLOW}启动日志监控: $LOG_FILE${NC}"
adb logcat -v time "$LOG_TAG:I" "*:S" > "$LOG_FILE" 2>&1 &
LOGCAT_PID=$!

# 清理函数
cleanup() {
    echo -e "\n${YELLOW}清理中...${NC}"
    kill $LOGCAT_PID 2>/dev/null || true
    echo -e "${GREEN}日志已保存: $LOG_FILE${NC}"
}
trap cleanup EXIT

# 状态检测函数
check_state_stuck() {
    local round=$1
    local start_time=$(date +%s)
    local state_changed=false
    
    # 等待最多STATE_TIMEOUT_SEC秒
    while [ $(($(date +%s) - start_time)) -lt $STATE_TIMEOUT_SEC ]; do
        # 检查是否有IDLE状态变化
        if tail -20 "$LOG_FILE" 2>/dev/null | grep -q "IDLE\|ASR停止\|setIdle"; then
            state_changed=true
            break
        fi
        sleep 1
    done
    
    if [ "$state_changed" = false ]; then
        echo -e "${RED}[轮次 $round] 警告: ${STATE_TIMEOUT_SEC}秒内未检测到状态切换回IDLE!${NC}"
        return 1
    fi
    return 0
}

# 主测试循环
echo -e "\n${GREEN}开始压力测试...${NC}\n"

stuck_count=0
for i in $(seq 1 $TEST_ROUNDS); do
    echo -e "${BLUE}[轮次 $i/$TEST_ROUNDS]${NC} 发送唤醒广播..."
    
    # 发送测试广播
    adb shell am broadcast -a "$TEST_ACTION" 2>/dev/null || {
        echo -e "${RED}发送广播失败${NC}"
        continue
    }
    
    # 记录时间戳
    echo "[$(date '+%H:%M:%S')] Round $i started" >> "$LOG_FILE"
    
    # 等待间隔时间（模拟用户操作间隔）
    sleep $(echo "scale=3; $INTERVAL_MS/1000" | bc)
    
    # 检查状态（非阻塞，仅在最后几轮检查）
    if [ $i -gt $((TEST_ROUNDS - 3)) ]; then
        echo -e "${YELLOW}  检查状态...${NC}"
        if ! check_state_stuck $i; then
            ((stuck_count++))
        fi
    fi
done

echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}  测试完成${NC}"
echo -e "${BLUE}========================================${NC}"

# 等待最后的状态变化
echo -e "\n${YELLOW}等待最终状态检查 (${STATE_TIMEOUT_SEC}秒)...${NC}"
sleep $STATE_TIMEOUT_SEC

# 分析日志
echo -e "\n${BLUE}日志分析:${NC}"
echo -e "  LISTENING状态次数: $(grep -c "LISTENING" "$LOG_FILE" 2>/dev/null || echo "0")"
echo -e "  IDLE状态次数: $(grep -c "IDLE\|setIdle" "$LOG_FILE" 2>/dev/null || echo "0")"
echo -e "  错误次数: $(grep -c "ERROR\|Exception\|error" "$LOG_FILE" 2>/dev/null || echo "0")"
echo -e "  AsrHandler停止次数: $(grep -c "AsrHandler 已停止\|ASR停止" "$LOG_FILE" 2>/dev/null || echo "0")"

# 检查卡住状态
echo -e "\n${BLUE}状态卡住检测:${NC}"
if grep -q "LISTENING" "$LOG_FILE" 2>/dev/null; then
    last_listening=$(grep "LISTENING" "$LOG_FILE" 2>/dev/null | tail -1)
    last_idle=$(grep "IDLE\|setIdle" "$LOG_FILE" 2>/dev/null | tail -1)
    
    echo -e "  最后LISTENING: $last_listening"
    echo -e "  最后IDLE: $last_idle"
fi

if [ $stuck_count -gt 0 ]; then
    echo -e "\n${RED}警告: 检测到 $stuck_count 次状态卡住!${NC}"
    echo -e "${YELLOW}请查看日志文件进行详细分析: $LOG_FILE${NC}"
else
    echo -e "\n${GREEN}测试通过: 未检测到状态卡住问题${NC}"
fi

echo -e "\n${BLUE}完整日志: $LOG_FILE${NC}"

