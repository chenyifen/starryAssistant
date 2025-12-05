#!/bin/bash

# 自动化测试脚本 - 通过HTTP接口控制语音助手
# 用于压测和复现状态卡住问题
# 结合logcat输出判断测试结果

# ========== 配置 ==========
DEVICE_IP="${DEVICE_IP:-192.168.1.100}"
PORT="${PORT:-8765}"
BASE_URL="http://${DEVICE_IP}:${PORT}"

# 测试间隔(秒)
WAKE_INTERVAL="${WAKE_INTERVAL:-3}"
ASR_DELAY="${ASR_DELAY:-1}"
SKILL_TIMEOUT="${SKILL_TIMEOUT:-5}"

# 日志文件
LOG_DIR="${LOG_DIR:-./autotest_logs}"
LOGCAT_FILE=""

# ========== 颜色输出 ==========
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_debug() { [[ -n "$DEBUG" ]] && echo -e "${CYAN}[DEBUG]${NC} $1"; }

# ========== Logcat管理 ==========
start_logcat() {
    mkdir -p "$LOG_DIR"
    LOGCAT_FILE="${LOG_DIR}/logcat_$(date '+%Y%m%d_%H%M%S').log"
    adb logcat -c 2>/dev/null
    adb logcat -v time AutoTest:I SkillEvaluator:I AsrHandler:I *:S > "$LOGCAT_FILE" 2>/dev/null &
    LOGCAT_PID=$!
    sleep 0.5
    log_info "Logcat started -> $LOGCAT_FILE (PID: $LOGCAT_PID)"
}

stop_logcat() {
    if [[ -n "$LOGCAT_PID" ]]; then
        kill $LOGCAT_PID 2>/dev/null
        log_info "Logcat stopped"
    fi
}

wait_for_log() {
    local pattern="$1"
    local timeout="${2:-$SKILL_TIMEOUT}"
    local start_line="${3:-0}"
    
    local count=0
    while [[ $count -lt $((timeout * 10)) ]]; do
        if tail -n +$start_line "$LOGCAT_FILE" 2>/dev/null | grep -q "$pattern"; then
            return 0
        fi
        sleep 0.1
        ((count++))
    done
    return 1
}

get_log_line_count() {
    wc -l < "$LOGCAT_FILE" 2>/dev/null | tr -d ' '
}

check_skill_executed() {
    local start_line="$1"
    local timeout="${2:-$SKILL_TIMEOUT}"
    
    local count=0
    while [[ $count -lt $((timeout * 10)) ]]; do
        local result=$(tail -n +$start_line "$LOGCAT_FILE" 2>/dev/null | grep -E "技能执行:|[SKILL]")
        if [[ -n "$result" ]]; then
            echo "$result"
            return 0
        fi
        sleep 0.1
        ((count++))
    done
    return 1
}

check_state_stuck() {
    local start_line="$1"
    sleep 2
    
    local status_resp=$(status)
    local asr_running=$(echo "$status_resp" | grep -o '"asr_running":[^,}]*' | cut -d: -f2)
    
    if [[ "$asr_running" == "true" ]]; then
        log_warn "ASR still running after command - possible stuck state"
        return 1
    fi
    return 0
}

check_problems() {
    local start_line="$1"
    local timeout="${2:-15}"
    
    local count=0
    while [[ $count -lt $((timeout * 10)) ]]; do
        local stuck=$(tail -n +$start_line "$LOGCAT_FILE" 2>/dev/null | grep -E "\[STUCK\]|\[SILENCE_TIMEOUT_ABNORMAL\]|\[ASR_NO_AUDIO\]" | tail -1)
        if [[ -n "$stuck" ]]; then
            log_error "检测到问题: $stuck"
            return 1
        fi
        sleep 0.1
        ((count++))
    done
    return 0
}

# ========== 基础命令 ==========
wake() {
    curl -s --connect-timeout 3 "${BASE_URL}/wake" 2>/dev/null
}

asr() {
    local text="$1"
    curl -s --connect-timeout 3 "${BASE_URL}/asr?text=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$text'))")" 2>/dev/null
}

status() {
    curl -s --connect-timeout 3 "${BASE_URL}/status" 2>/dev/null
}

# ========== 测试用例 ==========
test_wake_only() {
    log_info "测试: 仅唤醒"
    local result=$(wake)
    echo "  Response: $result"
    sleep $WAKE_INTERVAL
}

test_wake_and_asr() {
    local text="${1:-打开相机}"
    log_info "测试: 唤醒 + ASR($text)"
    
    wake
    sleep $ASR_DELAY
    local result=$(asr "$text")
    echo "  ASR Response: $result"
    sleep $WAKE_INTERVAL
}

test_status() {
    log_info "查询状态"
    local result=$(status)
    echo "  Status: $result"
}

# ========== 压测场景 ==========
stress_wake_loop() {
    local count="${1:-100}"
    log_info "压测: 连续唤醒 ${count} 次"
    
    for i in $(seq 1 $count); do
        echo -n "[$i/$count] "
        local result=$(wake)
        if [[ "$result" == *"ok"* ]]; then
            echo -e "${GREEN}OK${NC}"
        else
            echo -e "${RED}FAIL: $result${NC}"
        fi
        sleep $WAKE_INTERVAL
    done
}

stress_full_flow() {
    local count="${1:-50}"
    local commands=("打开相机" "现在几点" "今天天气怎么样" "打开设置" "播放音乐")
    
    log_info "压测: 完整流程(唤醒+ASR) ${count} 次"
    start_logcat
    trap stop_logcat EXIT
    
    local success=0
    local fail=0
    local stuck=0
    
    for i in $(seq 1 $count); do
        local cmd="${commands[$((RANDOM % ${#commands[@]}))]}"
        local start_line=$(get_log_line_count)
        echo -n "[$i/$count] $cmd ... "
        
        # 发送唤醒
        local wake_resp=$(wake)
        if [[ -z "$wake_resp" || "$wake_resp" != *"ok"* ]]; then
            echo -e "${RED}WAKE_FAIL${NC}"
            ((fail++))
            continue
        fi
        
        sleep $ASR_DELAY
        
        # 发送ASR
        local asr_resp=$(asr "$cmd")
        if [[ -z "$asr_resp" || "$asr_resp" != *"ok"* ]]; then
            echo -e "${RED}ASR_FAIL${NC}"
            ((fail++))
            continue
        fi
        
        # 等待技能执行
        local skill_result=$(check_skill_executed $start_line)
        if [[ -n "$skill_result" ]]; then
            echo -e "${GREEN}OK${NC} -> $(echo $skill_result | head -1)"
            ((success++))
        else
            echo -e "${YELLOW}NO_SKILL${NC}"
        fi
        
        # 检查状态是否卡住
        if ! check_state_stuck $start_line; then
            ((stuck++))
            log_error "检测到状态卡住! (第 $i 次)"
            # 保存当前日志
            cp "$LOGCAT_FILE" "${LOG_DIR}/stuck_${i}_$(date '+%H%M%S').log"
        fi
        
        sleep $WAKE_INTERVAL
    done
    
    stop_logcat
    echo ""
    echo "========================================"
    log_info "测试结果:"
    echo "  成功: $success"
    echo "  失败: $fail"
    echo "  卡住: $stuck"
    echo "  日志: $LOGCAT_FILE"
    echo "========================================"
}

test_mode_11() {
    local count="${1:-100}"
    local commands=("打开相机" "现在几点" "今天天气怎么样" "打开设置" "播放音乐")
    
    log_info "测试模式11: 发现问题后立即停止 (${count} 次)"
    start_logcat
    trap stop_logcat EXIT
    
    local success=0
    local fail=0
    local problems=0
    
    for i in $(seq 1 $count); do
        local cmd="${commands[$((RANDOM % ${#commands[@]}))]}"
        local start_line=$(get_log_line_count)
        echo -n "[$i/$count] $cmd ... "
        
        # 发送唤醒
        local wake_resp=$(wake)
        if [[ -z "$wake_resp" || "$wake_resp" != *"ok"* ]]; then
            echo -e "${RED}WAKE_FAIL${NC}"
            ((fail++))
            continue
        fi
        
        sleep $ASR_DELAY
        
        # 发送ASR
        local asr_resp=$(asr "$cmd")
        if [[ -z "$asr_resp" || "$asr_resp" != *"ok"* ]]; then
            echo -e "${RED}ASR_FAIL${NC}"
            ((fail++))
            continue
        fi
        
        # 等待技能执行
        local skill_result=$(check_skill_executed $start_line)
        if [[ -n "$skill_result" ]]; then
            echo -e "${GREEN}OK${NC} -> $(echo $skill_result | head -1)"
            ((success++))
        else
            echo -e "${YELLOW}NO_SKILL${NC}"
        fi
        
        # 测试模式11: 检查问题（卡住、静音超时异常、无音频数据）
        if ! check_problems $start_line 15; then
            ((problems++))
            log_error "检测到问题! (第 $i 次) - 立即停止测试"
            cp "$LOGCAT_FILE" "${LOG_DIR}/problem_${i}_$(date '+%H%M%S').log"
            stop_logcat
            echo ""
            echo "========================================"
            log_error "测试模式11: 发现问题后立即停止"
            echo "  成功: $success"
            echo "  失败: $fail"
            echo "  问题: $problems"
            echo "  日志: $LOGCAT_FILE"
            echo "========================================"
            exit 1
        fi
        
        sleep $WAKE_INTERVAL
    done
    
    stop_logcat
    echo ""
    echo "========================================"
    log_info "测试模式11完成:"
    echo "  成功: $success"
    echo "  失败: $fail"
    echo "  问题: $problems"
    echo "  日志: $LOGCAT_FILE"
    echo "========================================"
}

stress_rapid_wake() {
    local count="${1:-20}"
    log_info "压测: 快速连续唤醒(无间隔) ${count} 次"
    
    for i in $(seq 1 $count); do
        echo -n "[$i] "
        wake
        echo ""
        sleep 0.5
    done
    
    sleep 2
    log_info "检查最终状态:"
    status
}

stress_interrupt() {
    local count="${1:-30}"
    log_info "压测: 中断场景(唤醒后立即再唤醒) ${count} 次"
    
    for i in $(seq 1 $count); do
        echo -n "[$i/$count] "
        
        wake > /dev/null
        sleep 0.3
        
        # 模拟用户说了一半被打断
        asr "打开" > /dev/null
        sleep 0.2
        
        # 立即再次唤醒
        local result=$(wake)
        if [[ "$result" == *"ok"* ]]; then
            echo -e "${GREEN}OK${NC}"
        else
            echo -e "${RED}FAIL: $result${NC}"
        fi
        
        sleep $WAKE_INTERVAL
    done
}

monitor_status() {
    local interval="${1:-2}"
    log_info "持续监控状态 (Ctrl+C 停止)"
    
    while true; do
        local st=$(status 2>/dev/null)
        local ts=$(date '+%H:%M:%S')
        echo "[$ts] $st"
        sleep $interval
    done
}

# ========== 主菜单 ==========
show_help() {
    echo "语音助手自动化测试脚本"
    echo ""
    echo "用法: $0 <command> [args]"
    echo ""
    echo "配置环境变量:"
    echo "  DEVICE_IP=192.168.1.100  设备IP地址"
    echo "  PORT=8765                HTTP端口"
    echo "  WAKE_INTERVAL=3          唤醒间隔(秒)"
    echo "  ASR_DELAY=1              唤醒后ASR延迟(秒)"
    echo ""
    echo "基础命令:"
    echo "  wake                     模拟唤醒"
    echo "  asr <text>               模拟ASR识别结果"
    echo "  status                   查询状态"
    echo ""
    echo "测试用例:"
    echo "  test-wake                测试唤醒"
    echo "  test-asr <text>          测试唤醒+ASR"
    echo ""
    echo "压测场景:"
    echo "  stress-wake [count]      连续唤醒压测 (默认100次)"
    echo "  stress-full [count]      完整流程压测 (默认50次)"
    echo "  stress-rapid [count]     快速唤醒压测 (默认20次)"
    echo "  stress-interrupt [count] 中断场景压测 (默认30次)"
    echo "  test-11 [count]         测试模式11: 发现问题后立即停止 (默认100次)"
    echo ""
    echo "监控:"
    echo "  monitor [interval]       持续监控状态 (默认2秒)"
    echo ""
    echo "示例:"
    echo "  DEVICE_IP=192.168.1.50 $0 wake"
    echo "  DEVICE_IP=192.168.1.50 $0 asr '打开相机'"
    echo "  DEVICE_IP=192.168.1.50 $0 stress-full 100"
}

# ========== 入口 ==========
case "${1:-help}" in
    wake)
        wake
        echo ""
        ;;
    asr)
        asr "${2:-测试}"
        echo ""
        ;;
    status)
        status
        echo ""
        ;;
    test-wake)
        test_wake_only
        ;;
    test-asr)
        test_wake_and_asr "$2"
        ;;
    stress-wake)
        stress_wake_loop "${2:-100}"
        ;;
    stress-full)
        stress_full_flow "${2:-50}"
        ;;
    stress-rapid)
        stress_rapid_wake "${2:-20}"
        ;;
    stress-interrupt)
        stress_interrupt "${2:-30}"
        ;;
    test-11)
        test_mode_11 "${2:-100}"
        ;;
    monitor)
        monitor_status "${2:-2}"
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "未知命令: $1"
        show_help
        exit 1
        ;;
esac

