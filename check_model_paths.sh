#!/bin/bash

# 模型路径检查脚本
# 显示应用使用的模型路径和状态

echo "=========================================="
echo "📂 模型路径检查工具"
echo "=========================================="
echo ""

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 未检测到Android设备"
    echo ""
    echo "💡 提示：请连接设备后运行此脚本"
    exit 1
fi

echo "✅ 检测到Android设备"
echo ""

# 获取应用包名
PACKAGE_NAME="org.stypox.dicio.debug"

echo "📋 检查模型路径..."
echo ""

# 候选路径列表
PATHS=(
    "/sdcard/Android/data/com.ai.voice/files/models"
    "/storage/emulated/0/VoiceAssistant/models"
)

# 检查每个候选路径
for path in "${PATHS[@]}"; do
    echo "🔍 检查路径: $path"
    
    # 检查路径是否存在
    if adb shell "[ -d '$path' ]" 2>/dev/null; then
        echo "   ✅ 路径存在"
        
        # 检查SenseVoice模型
        sensevoice_path="$path/asr/sensevoice"
        if adb shell "[ -d '$sensevoice_path' ]" 2>/dev/null; then
            echo "   📁 SenseVoice目录: ✅"
            
            # 列出文件
            echo "   📄 文件列表:"
            adb shell "ls -lh '$sensevoice_path/'" 2>/dev/null | while read line; do
                echo "      $line"
            done
        else
            echo "   📁 SenseVoice目录: ❌ 不存在"
        fi
        
        # 检查VAD模型
        vad_path="$path/vad"
        if adb shell "[ -d '$vad_path' ]" 2>/dev/null; then
            echo "   📁 VAD目录: ✅"
            adb shell "ls -lh '$vad_path/'" 2>/dev/null | head -5
        else
            echo "   📁 VAD目录: ❌ 不存在"
        fi
        
        # 检查唤醒词模型
        kws_path="$path/sherpa_onnx_kws"
        if adb shell "[ -d '$kws_path' ]" 2>/dev/null; then
            echo "   📁 唤醒词目录: ✅"
        else
            echo "   📁 唤醒词目录: ❌ 不存在"
        fi
    else
        echo "   ❌ 路径不存在"
    fi
    echo ""
done

echo "=========================================="
echo "💡 推荐的模型路径设置"
echo "=========================================="
echo ""

# 推荐路径
RECOMMENDED_PATH="/sdcard/Android/data/com.ai.voice/files/models"

echo "📍 推荐使用路径: $RECOMMENDED_PATH"
echo ""
echo "📋 SenseVoice模型应放置在:"
echo "   $RECOMMENDED_PATH/asr/sensevoice/"
echo ""
echo "   需要的文件:"
echo "   - model.onnx 或 model.int8.onnx (模型文件)"
echo "   - tokens.txt (词表文件)"
echo ""

echo "=========================================="
echo "🔧 如何推送模型到设备"
echo "=========================================="
echo ""

echo "1️⃣ 创建目录:"
echo "   adb shell mkdir -p \"$RECOMMENDED_PATH/asr/sensevoice\""
echo ""

echo "2️⃣ 推送模型文件:"
echo "   adb push /path/to/model.onnx \"$RECOMMENDED_PATH/asr/sensevoice/\""
echo "   adb push /path/to/tokens.txt \"$RECOMMENDED_PATH/asr/sensevoice/\""
echo ""

echo "3️⃣ 验证推送:"
echo "   adb shell ls -lh \"$RECOMMENDED_PATH/asr/sensevoice/\""
echo ""

echo "=========================================="
echo "📱 从应用日志查看路径信息"
echo "=========================================="
echo ""

echo "运行以下命令查看应用选择的路径:"
echo "   adb logcat | grep -E \"ModelPathManager|SenseVoiceModelManager\""
echo ""

