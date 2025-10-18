#!/bin/bash
# 模型管理脚本 - 统一管理和推送所有AI模型到设备
# 作者: AI Assistant
# 日期: 2025-10-18

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS_DIR="$PROJECT_ROOT/models"
DEVICE_MODELS_PATH="/storage/emulated/0/Android/data/com.ai.voice/files/models"
TEMP_PUSH_DIR="/sdcard/temp_models"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查设备连接
check_device() {
    log_info "检查设备连接..."
    if ! adb devices | grep -q "device$"; then
        log_error "没有检测到Android设备，请确保设备已连接并开启USB调试"
        exit 1
    fi
    log_success "设备已连接"
}

# 检查并创建设备目录
prepare_device_directories() {
    log_info "准备设备目录..."
    
    # 创建临时目录
    adb shell "mkdir -p $TEMP_PUSH_DIR" 2>/dev/null || true
    
    # 创建模型目录结构
    adb shell "mkdir -p $DEVICE_MODELS_PATH/wake" 2>/dev/null || true
    adb shell "mkdir -p $DEVICE_MODELS_PATH/tts" 2>/dev/null || true
    adb shell "mkdir -p $DEVICE_MODELS_PATH/asr" 2>/dev/null || true
    adb shell "mkdir -p $DEVICE_MODELS_PATH/vad" 2>/dev/null || true
    
    log_success "设备目录准备完成"
}

# 推送唤醒词模型
push_wake_models() {
    log_info "========== 推送唤醒词模型 =========="
    
    # HiNudge V8 韩语唤醒词（默认）
    if [ -d "$MODELS_DIR/hiNudgeOpenWakeWord" ]; then
        log_info "推送 HiNudge OpenWakeWord (V8)..."
        adb push "$MODELS_DIR/hiNudgeOpenWakeWord" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/wake/hiNudgeOpenWakeWord && mv $TEMP_PUSH_DIR/hiNudgeOpenWakeWord $DEVICE_MODELS_PATH/wake/"
        log_success "HiNudge V8 已推送"
    fi
    
    # Korean minimal
    if [ -d "$MODELS_DIR/openwakeword_korean_minimal" ]; then
        log_info "推送 OpenWakeWord Korean Minimal..."
        adb push "$MODELS_DIR/openwakeword_korean_minimal" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/wake/openwakeword_korean_minimal && mv $TEMP_PUSH_DIR/openwakeword_korean_minimal $DEVICE_MODELS_PATH/wake/"
        log_success "Korean Minimal 已推送"
    fi
    
    # Korean V1
    if [ -d "$MODELS_DIR/openwakeword_korean_v1" ]; then
        log_info "推送 OpenWakeWord Korean V1..."
        adb push "$MODELS_DIR/openwakeword_korean_v1" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/wake/openwakeword_korean_v1 && mv $TEMP_PUSH_DIR/openwakeword_korean_v1 $DEVICE_MODELS_PATH/wake/"
        log_success "Korean V1 已推送"
    fi
}

# 推送TTS模型
push_tts_models() {
    log_info "========== 推送TTS模型 =========="
    
    local tts_src="$MODELS_DIR/tts"
    
    # 英语 TTS
    if [ -d "$tts_src/vits-piper-en_US-amy-low" ]; then
        log_info "推送英语TTS模型 (60MB)..."
        adb push "$tts_src/vits-piper-en_US-amy-low" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/tts/vits-piper-en_US-amy-low && mv $TEMP_PUSH_DIR/vits-piper-en_US-amy-low $DEVICE_MODELS_PATH/tts/"
        log_success "英语TTS已推送"
    fi
    
    # 韩语 TTS
    if [ -d "$tts_src/vits-mimic3-ko_KO-kss_low" ]; then
        log_info "推送韩语TTS模型..."
        adb push "$tts_src/vits-mimic3-ko_KO-kss_low" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/tts/vits-mimic3-ko_KO-kss_low && mv $TEMP_PUSH_DIR/vits-mimic3-ko_KO-kss_low $DEVICE_MODELS_PATH/tts/"
        log_success "韩语TTS已推送"
    fi
    
    # 中文 TTS
    if [ -d "$tts_src/vits-zh-hf-fanchen-C" ]; then
        log_info "推送中文TTS模型..."
        adb push "$tts_src/vits-zh-hf-fanchen-C" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/tts/vits-zh-hf-fanchen-C && mv $TEMP_PUSH_DIR/vits-zh-hf-fanchen-C $DEVICE_MODELS_PATH/tts/"
        log_success "中文TTS已推送"
    fi
}

# 推送ASR模型
push_asr_models() {
    log_info "========== 推送ASR模型 =========="
    
    # SenseVoice (需要单独下载，900MB+)
    local sensevoice_dir="$PROJECT_ROOT/../dicio-android_2/models/asr/sensevoice"
    if [ -d "$sensevoice_dir" ]; then
        log_info "推送 SenseVoice ASR模型 (900MB+, 需要时间)..."
        adb push "$sensevoice_dir" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/asr/sensevoice && mv $TEMP_PUSH_DIR/sensevoice $DEVICE_MODELS_PATH/asr/"
        log_success "SenseVoice ASR已推送"
    else
        log_warning "未找到SenseVoice模型，跳过"
    fi
}

# 推送VAD模型
push_vad_models() {
    log_info "========== 推送VAD模型 =========="
    
    if [ -d "$MODELS_DIR/vad" ]; then
        log_info "推送VAD模型..."
        # VAD模型通常很小
        adb push "$MODELS_DIR/vad" "$TEMP_PUSH_DIR/"
        adb shell "rm -rf $DEVICE_MODELS_PATH/vad/* && mv $TEMP_PUSH_DIR/vad/* $DEVICE_MODELS_PATH/vad/"
        log_success "VAD模型已推送"
    fi
}

# 清理临时文件
cleanup() {
    log_info "清理临时文件..."
    adb shell "rm -rf $TEMP_PUSH_DIR" 2>/dev/null || true
    log_success "清理完成"
}

# 显示设备上的模型信息
show_device_models() {
    log_info "========== 设备模型信息 =========="
    echo ""
    
    log_info "唤醒词模型："
    adb shell "ls -lh $DEVICE_MODELS_PATH/wake/ 2>/dev/null | tail -n +2" || log_warning "无唤醒词模型"
    echo ""
    
    log_info "TTS模型："
    adb shell "ls -lh $DEVICE_MODELS_PATH/tts/ 2>/dev/null | tail -n +2" || log_warning "无TTS模型"
    echo ""
    
    log_info "ASR模型："
    adb shell "ls -lh $DEVICE_MODELS_PATH/asr/ 2>/dev/null | tail -n +2" || log_warning "无ASR模型"
    echo ""
    
    log_info "VAD模型："
    adb shell "ls -lh $DEVICE_MODELS_PATH/vad/ 2>/dev/null | tail -n +2" || log_warning "无VAD模型"
    echo ""
}

# 从设备备份模型
backup_from_device() {
    log_info "========== 从设备备份模型 =========="
    
    local backup_dir="$PROJECT_ROOT/models_backup_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$backup_dir"
    
    log_info "备份到: $backup_dir"
    adb pull "$DEVICE_MODELS_PATH" "$backup_dir/"
    
    log_success "备份完成: $backup_dir"
}

# 清理设备上的所有模型
clean_device_models() {
    log_warning "即将清理设备上的所有模型，此操作不可恢复！"
    read -p "确认清理? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        log_info "清理设备模型..."
        adb shell "rm -rf $DEVICE_MODELS_PATH/*"
        log_success "设备模型已清理"
    else
        log_info "取消清理操作"
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
AI语音助手 - 模型管理工具

用法: $0 [命令]

命令:
  push-all          推送所有模型到设备（默认）
  push-wake         仅推送唤醒词模型
  push-tts          仅推送TTS模型
  push-asr          仅推送ASR模型
  push-vad          仅推送VAD模型
  
  show              显示设备上的模型信息
  backup            从设备备份所有模型
  clean             清理设备上的所有模型
  
  help              显示此帮助信息

示例:
  $0                # 推送所有模型
  $0 push-wake      # 仅推送唤醒词模型
  $0 show           # 查看设备模型
  $0 backup         # 备份设备模型到本地

EOF
}

# 主函数
main() {
    local command="${1:-push-all}"
    
    echo ""
    log_info "======================================"
    log_info "  AI语音助手 - 模型管理工具"
    log_info "======================================"
    echo ""
    
    case "$command" in
        push-all)
            check_device
            prepare_device_directories
            push_wake_models
            push_tts_models
            push_asr_models
            push_vad_models
            cleanup
            show_device_models
            log_success "所有模型推送完成！"
            ;;
        push-wake)
            check_device
            prepare_device_directories
            push_wake_models
            cleanup
            log_success "唤醒词模型推送完成！"
            ;;
        push-tts)
            check_device
            prepare_device_directories
            push_tts_models
            cleanup
            log_success "TTS模型推送完成！"
            ;;
        push-asr)
            check_device
            prepare_device_directories
            push_asr_models
            cleanup
            log_success "ASR模型推送完成！"
            ;;
        push-vad)
            check_device
            prepare_device_directories
            push_vad_models
            cleanup
            log_success "VAD模型推送完成！"
            ;;
        show)
            check_device
            show_device_models
            ;;
        backup)
            check_device
            backup_from_device
            ;;
        clean)
            check_device
            clean_device_models
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $command"
            show_help
            exit 1
            ;;
    esac
    
    echo ""
}

# 运行主函数
main "$@"

