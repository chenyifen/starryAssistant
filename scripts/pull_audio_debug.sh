#!/bin/bash

# Dicio 音频调试文件拉取脚本
# 使用方法: ./scripts/pull_audio_debug.sh

set -e

# 配置
PACKAGE_NAME="org.stypox.dicio.master"
REMOTE_DIR="/data/data/$PACKAGE_NAME/files/audio_debug"
LOCAL_DIR="$HOME/dicio_audio_debug"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo "🎵 Dicio 音频调试文件拉取工具"
echo "=================================="

# 检查 ADB 连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 未检测到 Android 设备连接"
    echo "请确保:"
    echo "  1. 设备已连接并开启 USB 调试"
    echo "  2. 已授权此计算机的 ADB 访问"
    exit 1
fi

echo "✅ 检测到 Android 设备连接"

# 检查应用是否已安装
if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    echo "❌ 错误: 未找到 Dicio 应用 ($PACKAGE_NAME)"
    echo "请确保 Dicio 应用已安装"
    exit 1
fi

echo "✅ 检测到 Dicio 应用"

# 检查音频调试目录是否存在
if ! adb shell "test -d $REMOTE_DIR" 2>/dev/null; then
    echo "❌ 错误: 音频调试目录不存在"
    echo "请确保:"
    echo "  1. 已启用音频调试功能 (DEBUG_SAVE_AUDIO = true)"
    echo "  2. 已运行过唤醒功能生成音频文件"
    exit 1
fi

echo "✅ 找到音频调试目录"

# 创建本地目录
mkdir -p "$LOCAL_DIR/backup_$TIMESTAMP"
LOCAL_BACKUP_DIR="$LOCAL_DIR/backup_$TIMESTAMP"

echo "📁 创建本地备份目录: $LOCAL_BACKUP_DIR"

# 获取文件列表和统计信息
echo "📊 获取音频文件统计信息..."

WAKE_COUNT=$(adb shell "find $REMOTE_DIR/wake_audio -name '*.pcm' 2>/dev/null | wc -l" 2>/dev/null || echo "0")
ASR_COUNT=$(adb shell "find $REMOTE_DIR/asr_audio -name '*.pcm' 2>/dev/null | wc -l" 2>/dev/null || echo "0")

echo "  - 唤醒音频文件: $WAKE_COUNT 个"
echo "  - ASR音频文件: $ASR_COUNT 个"

if [ "$WAKE_COUNT" -eq 0 ] && [ "$ASR_COUNT" -eq 0 ]; then
    echo "⚠️  警告: 未找到音频文件"
    echo "请确保已触发唤醒功能并生成了音频数据"
    exit 0
fi

# 拉取文件
echo "📥 开始拉取音频文件..."

# 拉取唤醒音频
if [ "$WAKE_COUNT" -gt 0 ]; then
    echo "  📥 拉取唤醒音频文件..."
    mkdir -p "$LOCAL_BACKUP_DIR/wake_audio"
    adb pull "$REMOTE_DIR/wake_audio/" "$LOCAL_BACKUP_DIR/wake_audio/" 2>/dev/null || true
fi

# 拉取ASR音频
if [ "$ASR_COUNT" -gt 0 ]; then
    echo "  📥 拉取ASR音频文件..."
    mkdir -p "$LOCAL_BACKUP_DIR/asr_audio"
    adb pull "$REMOTE_DIR/asr_audio/" "$LOCAL_BACKUP_DIR/asr_audio/" 2>/dev/null || true
fi

# 生成分析报告
echo "📋 生成分析报告..."
REPORT_FILE="$LOCAL_BACKUP_DIR/audio_analysis_report.txt"

cat > "$REPORT_FILE" << EOF
Dicio 音频调试文件分析报告
========================

生成时间: $(date)
设备信息: $(adb shell getprop ro.product.model) ($(adb shell getprop ro.build.version.release))
应用版本: $(adb shell dumpsys package $PACKAGE_NAME | grep versionName | head -1 | cut -d'=' -f2)

文件统计:
- 唤醒音频文件: $WAKE_COUNT 个
- ASR音频文件: $ASR_COUNT 个

音频格式:
- 采样率: 16000 Hz
- 位深度: 16 bit
- 声道: 单声道 (Mono)
- 格式: PCM (Little Endian)

分析工具推荐:
1. Audacity: 导入 → 原始数据 → 16位PCM，16000Hz，单声道
2. SoX: sox -r 16000 -e signed-integer -b 16 -c 1 input.pcm output.wav
3. FFmpeg: ffmpeg -f s16le -ar 16000 -ac 1 -i input.pcm output.wav

文件详情:
EOF

# 添加文件详情到报告
if [ "$WAKE_COUNT" -gt 0 ]; then
    echo "" >> "$REPORT_FILE"
    echo "唤醒音频文件:" >> "$REPORT_FILE"
    find "$LOCAL_BACKUP_DIR/wake_audio" -name "*.pcm" -exec ls -lh {} \; | while read line; do
        echo "  $line" >> "$REPORT_FILE"
    done
fi

if [ "$ASR_COUNT" -gt 0 ]; then
    echo "" >> "$REPORT_FILE"
    echo "ASR音频文件:" >> "$REPORT_FILE"
    find "$LOCAL_BACKUP_DIR/asr_audio" -name "*.pcm" -exec ls -lh {} \; | while read line; do
        echo "  $line" >> "$REPORT_FILE"
    done
fi

# 创建转换脚本
echo "🔧 创建音频转换脚本..."
CONVERT_SCRIPT="$LOCAL_BACKUP_DIR/convert_to_wav.sh"

cat > "$CONVERT_SCRIPT" << 'EOF'
#!/bin/bash
# 音频格式转换脚本
# 将PCM文件转换为WAV格式以便分析

echo "🔄 转换PCM文件为WAV格式..."

# 转换唤醒音频
if [ -d "wake_audio" ]; then
    mkdir -p "wake_audio_wav"
    for pcm_file in wake_audio/*.pcm; do
        if [ -f "$pcm_file" ]; then
            base_name=$(basename "$pcm_file" .pcm)
            wav_file="wake_audio_wav/${base_name}.wav"
            echo "  转换: $pcm_file -> $wav_file"
            
            # 使用 SoX 转换 (如果可用)
            if command -v sox >/dev/null 2>&1; then
                sox -r 16000 -e signed-integer -b 16 -c 1 "$pcm_file" "$wav_file"
            # 使用 FFmpeg 转换 (如果可用)
            elif command -v ffmpeg >/dev/null 2>&1; then
                ffmpeg -f s16le -ar 16000 -ac 1 -i "$pcm_file" "$wav_file" -y >/dev/null 2>&1
            else
                echo "    ⚠️  警告: 未找到 sox 或 ffmpeg，跳过转换"
            fi
        fi
    done
fi

# 转换ASR音频
if [ -d "asr_audio" ]; then
    mkdir -p "asr_audio_wav"
    for pcm_file in asr_audio/*.pcm; do
        if [ -f "$pcm_file" ]; then
            base_name=$(basename "$pcm_file" .pcm)
            wav_file="asr_audio_wav/${base_name}.wav"
            echo "  转换: $pcm_file -> $wav_file"
            
            if command -v sox >/dev/null 2>&1; then
                sox -r 16000 -e signed-integer -b 16 -c 1 "$pcm_file" "$wav_file"
            elif command -v ffmpeg >/dev/null 2>&1; then
                ffmpeg -f s16le -ar 16000 -ac 1 -i "$pcm_file" "$wav_file" -y >/dev/null 2>&1
            else
                echo "    ⚠️  警告: 未找到 sox 或 ffmpeg，跳过转换"
            fi
        fi
    done
fi

echo "✅ 转换完成"
EOF

chmod +x "$CONVERT_SCRIPT"

# 完成
echo ""
echo "🎉 音频文件拉取完成!"
echo "📁 文件保存位置: $LOCAL_BACKUP_DIR"
echo "📋 分析报告: $REPORT_FILE"
echo "🔧 转换脚本: $CONVERT_SCRIPT"
echo ""
echo "📖 下一步操作:"
echo "  1. 查看分析报告: cat '$REPORT_FILE'"
echo "  2. 转换为WAV格式: cd '$LOCAL_BACKUP_DIR' && ./convert_to_wav.sh"
echo "  3. 使用Audacity分析: 导入 → 原始数据 → 16位PCM，16000Hz，单声道"
echo ""
echo "🔍 快速检查音频文件:"
echo "  ls -lh '$LOCAL_BACKUP_DIR/wake_audio/'"
echo "  ls -lh '$LOCAL_BACKUP_DIR/asr_audio/'"
