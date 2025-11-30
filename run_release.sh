#!/bin/bash

echo "🚀 Dicio Android 构建、安装和启动脚本"
echo "========================================"

# 设置Java 17环境
echo "☕ 1. 设置Java 17环境..."
export JAVA_HOME="/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"


./gradlew clean assembleNormalRelease

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 查找APK文件（普通版本：VoiceAssistant-版本号-Release.apk）
# normal flavor的release构建输出路径：app/build/outputs/apk/normal/release/
apk_dir="app/build/outputs/apk/normal/release"
apk_path=$(find "$apk_dir" -name "VoiceAssistant-*-Release.apk" -type f | head -1)

if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "⚠️  未找到预期格式的APK文件，尝试查找所有APK..."
    apk_path=$(find "$apk_dir" -name "*.apk" -type f | head -1)
fi

# 如果还是找不到，尝试在release目录查找（兼容旧版本）
if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    apk_dir="app/build/outputs/apk/release"
    apk_path=$(find "$apk_dir" -name "VoiceAssistant-*-Release.apk" -type f | head -1)
fi

if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "❌ APK文件不存在，查找目录: $apk_dir"
    echo "   尝试查找的文件模式: VoiceAssistant-*-Release.apk"
    ls -la "$apk_dir" 2>/dev/null || echo "   目录不存在"
    exit 1
fi

# 获取APK信息
apk_name=$(basename "$apk_path")
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK文件: $apk_name"
echo "📦 APK大小: $apk_size"


# 安装应用
echo ""
echo "📲 5. 安装应用到设备..."
adb install -r "$apk_path"

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"
echo "💡 普通版本特点："
echo "   - 需要申请录音和通知权限"
echo "   - 使用普通应用UID运行"

# 启动应用（启动悬浮球启动器）
echo ""
echo "🎯 4. 启动应用（悬浮球启动器）..."
package_name="com.ai.voice"
activity_name="com.ai.voice.ui.floating.FloatingLauncherActivity"

adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败"
    exit 1
fi

echo "✅ 应用已启动"

# 获取应用进程ID
echo ""
echo "🔍 5. 获取应用进程信息..."
sleep 2  # 等待应用完全启动

app_pid=$(adb shell ps | grep "$package_name" | awk '{print $2}' | head -1)
if [ -n "$app_pid" ]; then
    echo "📱 应用进程ID: $app_pid"
    echo "💡 可以使用以下命令监控特定进程:"
    echo "   adb shell top -p $app_pid"
    echo "   adb shell dumpsys meminfo $app_pid"
else
    echo "⚠️ 未能获取应用进程ID，应用可能未完全启动"
fi

if [ -n "$app_pid" ]; then
    echo "📋 显示应用日志（按Ctrl+C退出）:"
    adb logcat | grep -- "$app_pid"
else
    echo "📋 显示应用日志（按Ctrl+C退出）:"
    adb logcat | grep -i "com.ai.voice"
fi
