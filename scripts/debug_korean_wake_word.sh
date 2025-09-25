#!/bin/bash

# 韩语唤醒词调试脚本
# 用于分析韩语唤醒词无法响应的问题

set -e

echo "🔍 韩语唤醒词调试分析..."

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有找到连接的Android设备"
    exit 1
fi

echo "📱 检测到Android设备"

# 检查外部存储中的模型文件
TARGET_DIR="/storage/emulated/0/Dicio/models/openWakeWord"
echo ""
echo "🔍 1. 检查外部存储中的韩语模型文件..."
if adb shell "test -f '$TARGET_DIR/wake.tflite'"; then
    echo "✅ 外部存储中存在韩语唤醒词模型"
    adb shell "ls -la '$TARGET_DIR/'"
    
    # 检查文件大小和完整性
    echo ""
    echo "📊 文件完整性检查："
    for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
        size=$(adb shell "stat -c%s '$TARGET_DIR/$file'" 2>/dev/null || echo "0")
        echo "  • $file: $size bytes"
    done
else
    echo "❌ 外部存储中不存在韩语唤醒词模型"
    echo "💡 请先运行: ./push_korean_models.sh"
    exit 1
fi

# 检查应用内部的模型文件
echo ""
echo "🔍 2. 检查应用内部的模型文件..."
INTERNAL_DIR="/data/data/org.stypox.dicio/files/openWakeWord"
if adb shell "test -f '$INTERNAL_DIR/userwake.tflite'"; then
    echo "✅ 应用内部存在用户自定义唤醒词模型"
    adb shell "ls -la '$INTERNAL_DIR/'" 2>/dev/null || echo "⚠️ 无法访问应用内部目录（需要root权限）"
else
    echo "❌ 应用内部不存在用户自定义唤醒词模型"
    echo "💡 可能需要重新切换语言或重启应用"
fi

# 检查应用是否运行
echo ""
echo "🔍 3. 检查应用状态..."
if adb shell "pgrep -f org.stypox.dicio" > /dev/null; then
    echo "✅ Dicio应用正在运行"
else
    echo "❌ Dicio应用未运行"
    echo "🚀 启动应用..."
    adb shell am start -n org.stypox.dicio/.ui.main.MainActivity
    sleep 3
fi

# 检查麦克风权限
echo ""
echo "🔍 4. 检查麦克风权限..."
mic_permission=$(adb shell "dumpsys package org.stypox.dicio | grep 'android.permission.RECORD_AUDIO'" | grep -o "granted=true\|granted=false" || echo "unknown")
if [[ "$mic_permission" == "granted=true" ]]; then
    echo "✅ 麦克风权限已授予"
else
    echo "❌ 麦克风权限未授予或检查失败"
    echo "💡 请在应用设置中授予麦克风权限"
fi

# 清除旧日志并开始监控
echo ""
echo "🔍 5. 开始实时调试监控..."
echo "请按以下步骤操作："
echo "1. 在应用中切换语言到韩语（한국어）"
echo "2. 启用OpenWakeWord唤醒功能"
echo "3. 播放韩语唤醒训练音频"
echo ""

# 清除logcat缓冲区
adb logcat -c

echo "📊 启动实时日志监控（按Ctrl+C停止）..."
echo "关键调试信息："
echo "  🔍 模型加载: 'Korean wake word copied from external storage'"
echo "  🎵 音频处理: 'Audio frame stats'"
echo "  🎯 置信度: 'Confidence=' 和 'Threshold='"
echo "  ⚠️ 错误信息: 'Error' 或 'Failed'"
echo ""

# 提供调试选项
echo "选择调试模式："
echo "1) 完整日志监控（推荐）"
echo "2) 仅韩语唤醒相关日志"
echo "3) 音频处理详细日志"
echo "4) 模型加载和验证日志"
echo "5) 手动测试指导"
read -p "请选择 (1-5): " debug_choice

case $debug_choice in
    1)
        echo "🔍 启动完整日志监控..."
        adb logcat | grep -E "(LanguageWakeWordManager|OpenWakeWordDevice|Korean|wake.*word|Audio.*frame|Confidence|Threshold|DETECTED)"
        ;;
    2)
        echo "🔍 启动韩语唤醒相关日志监控..."
        adb logcat | grep -E "(Korean|하이넛지|wake.*word|DETECTED)"
        ;;
    3)
        echo "🔍 启动音频处理详细日志监控..."
        adb logcat | grep -E "(Audio.*frame|amplitude|rms|Confidence|processFrame)"
        ;;
    4)
        echo "🔍 启动模型加载和验证日志监控..."
        adb logcat | grep -E "(LanguageWakeWordManager|copied.*from.*external|TensorFlow.*Lite|validation)"
        ;;
    5)
        echo ""
        echo "📝 手动测试指导："
        echo ""
        echo "🔧 调试步骤："
        echo "1. 确认语言已切换到韩语"
        echo "2. 确认唤醒方法设置为OpenWakeWord"
        echo "3. 播放韩语训练音频并观察日志"
        echo ""
        echo "🔍 关键检查点："
        echo "• 模型是否正确加载？"
        echo "• 音频是否被正确处理？（非零样本数 > 0）"
        echo "• 置信度分数是多少？"
        echo "• 阈值设置是否合适？"
        echo ""
        echo "🎯 预期日志模式："
        echo "• 语言切换: 'Found Korean wake word in external storage'"
        echo "• 模型加载: 'Korean wake word copied from external storage'"
        echo "• 音频处理: 'Audio frame stats: amplitude=X.XXXX, rms=X.XXXX'"
        echo "• 检测结果: 'Confidence=X.XXXXXX, Threshold=X.XXXXXX'"
        echo ""
        echo "🚨 常见问题："
        echo "• 如果amplitude=0.0000 → 麦克风无音频输入"
        echo "• 如果confidence始终很低 → 模型可能不匹配或音频质量问题"
        echo "• 如果没有'copied from external storage' → 模型加载失败"
        echo ""
        echo "🔧 调试命令："
        echo "adb logcat | grep -E '(Korean|Audio.*frame|Confidence)'"
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo ""
echo "✅ 韩语唤醒词调试分析完成！"
