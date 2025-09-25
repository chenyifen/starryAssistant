#!/bin/bash

# 韩语唤醒词集成测试脚本
# 测试语言切换时的自动唤醒词切换功能

set -e

echo "🇰🇷 韩语唤醒词集成测试开始..."

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有找到连接的Android设备"
    exit 1
fi

echo "📱 检测到Android设备"

# 构建应用
echo "🔨 构建Dicio应用..."
./gradlew assembleWithModelsDebug

# 安装应用
echo "📦 安装应用..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

# 启动应用
echo "🚀 启动应用..."
adb shell am start -n org.stypox.dicio/.ui.main.MainActivity

# 等待应用启动
sleep 3

echo "🧪 开始测试韩语唤醒词功能..."

# 测试1: 检查韩语模型文件是否正确复制
echo "📋 测试1: 检查韩语模型文件..."
adb shell "ls -la /data/data/org.stypox.dicio/files/openWakeWord/" || echo "⚠️ OpenWakeWord目录不存在"

# 测试2: 检查assets中的韩语模型
echo "📋 测试2: 验证assets中的韩语模型..."
if [ -f "app/src/withModels/assets/models/openWakeWord/wake.tflite" ]; then
    echo "✅ 韩语唤醒模型文件存在于assets中"
    ls -la app/src/withModels/assets/models/openWakeWord/
else
    echo "❌ 韩语唤醒模型文件不存在于assets中"
fi

# 测试3: 监控日志中的语言切换和唤醒词设置
echo "📋 测试3: 监控语言切换日志..."
echo "请在应用中切换到韩语，然后按Enter继续..."
read -p "按Enter继续..."

# 启动日志监控
echo "📊 监控相关日志（10秒）..."
timeout 10s adb logcat -s "LocaleManager:D" "LanguageWakeWordManager:D" "OpenWakeWordDevice:D" "WakeService:D" || true

echo ""
echo "🎯 测试指南："
echo "1. 在应用设置中切换语言到韩语"
echo "2. 检查唤醒词设置是否自动切换到'하이넛지'"
echo "3. 启用唤醒词检测功能"
echo "4. 尝试说'하이넛지'来测试唤醒功能"
echo ""
echo "📝 验证步骤："
echo "• 设置 → 语言 → 한국어"
echo "• 设置 → 唤醒方法 → OpenWakeWord offline audio processing"
echo "• 说'하이넛지'测试唤醒"
echo ""
echo "🔍 调试命令："
echo "• 查看日志: adb logcat | grep -E '(WakeWord|Korean|하이넛지)'"
echo "• 查看模型文件: adb shell ls -la /data/data/org.stypox.dicio/files/openWakeWord/"
echo "• 查看语言设置: adb logcat -s LocaleManager:D"
echo ""

# 提供实时日志监控选项
echo "是否要启动实时日志监控？(y/n)"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
    echo "🔍 启动实时日志监控..."
    echo "按Ctrl+C停止监控"
    adb logcat | grep -E "(WakeWord|Korean|하이넛지|LocaleManager|LanguageWakeWordManager)"
fi

echo "✅ 韩语唤醒词集成测试完成！"
