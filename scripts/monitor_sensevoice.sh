#!/bin/bash

# SenseVoice功能监控脚本
# 用于确认SenseVoice初始化和双识别功能的工作状态

echo "🔍 SenseVoice功能监控启动..."
echo "📱 请在Dicio中选择 '两阶段识别' 并进行语音测试"
echo "----------------------------------------"

# 清空logcat
adb logcat -c

# 监控相关标签的日志
echo "🎯 监控中... (按Ctrl+C停止)"
echo ""

adb logcat -s \
    TwoPassInputDevice \
    SenseVoiceRecognizer \
    SenseVoiceModelManager \
    AssetModelManager \
    AudioBuffer \
    VoskInputDevice \
    SttInputDeviceWrapper \
    DebugLogger | \
while IFS= read -r line; do
    # 添加时间戳
    timestamp=$(date '+%H:%M:%S')
    
    # 根据日志级别添加颜色
    if [[ $line == *" E "* ]]; then
        echo -e "[$timestamp] \033[0;31m$line\033[0m"  # 红色错误
    elif [[ $line == *" W "* ]]; then
        echo -e "[$timestamp] \033[0;33m$line\033[0m"  # 黄色警告
    elif [[ $line == *"✅"* ]] || [[ $line == *"🎉"* ]]; then
        echo -e "[$timestamp] \033[0;32m$line\033[0m"  # 绿色成功
    elif [[ $line == *"🔧"* ]] || [[ $line == *"📋"* ]]; then
        echo -e "[$timestamp] \033[0;36m$line\033[0m"  # 青色配置
    elif [[ $line == *"🎯"* ]] || [[ $line == *"🚀"* ]]; then
        echo -e "[$timestamp] \033[0;35m$line\033[0m"  # 紫色识别
    else
        echo "[$timestamp] $line"
    fi
done
