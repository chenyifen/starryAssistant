#!/bin/bash

# SenseVoice功能测试验证脚本

echo "🧪 SenseVoice功能测试开始..."
echo "================================"

# 1. 启动应用
echo "1️⃣ 启动Dicio应用..."
adb shell am start -n org.stypox.dicio.master/org.stypox.dicio.MainActivity

sleep 3

# 2. 清空日志
echo "2️⃣ 清空日志缓冲区..."
adb logcat -c

# 3. 检查应用是否运行
echo "3️⃣ 检查应用运行状态..."
app_running=$(adb shell pidof org.stypox.dicio.master)
if [ -z "$app_running" ]; then
    echo "❌ 应用未运行，请手动启动Dicio"
    exit 1
else
    echo "✅ 应用正在运行 (PID: $app_running)"
fi

# 4. 等待并监控初始化日志
echo "4️⃣ 监控SenseVoice初始化..."
echo "📍 正在等待SenseVoice初始化日志 (超时30秒)..."

timeout 30 adb logcat -s TwoPassInputDevice SenseVoiceRecognizer SenseVoiceModelManager | while IFS= read -r line; do
    echo "   $line"
    
    # 检查关键日志
    if [[ $line == *"SenseVoice识别器初始化成功"* ]]; then
        echo ""
        echo "🎉 SenseVoice初始化成功!"
        break
    elif [[ $line == *"SenseVoice模型不可用"* ]]; then
        echo ""
        echo "❌ SenseVoice模型不可用"
        break
    elif [[ $line == *"SenseVoice识别器创建失败"* ]]; then
        echo ""
        echo "❌ SenseVoice创建失败"
        break
    fi
done

echo ""
echo "📋 测试说明:"
echo "1. 请在Dicio设置中选择 '两阶段识别(Two-Pass Recognition)' 作为输入方法"
echo "2. 进行语音输入测试，观察是否有两阶段识别日志"
echo "3. 运行 './monitor_sensevoice.sh' 来实时监控详细日志"
echo ""
echo "🔍 快速检查命令:"
echo "   adb logcat -s TwoPassInputDevice | grep -E '✅|❌|🎯|🚀'"
echo ""
