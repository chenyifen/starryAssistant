#!/bin/bash

echo "🚀 Dicio Android 系统预装版本构建脚本"
echo "========================================"
echo "⚠️  注意：此版本为系统预装版本，使用系统UID，不需要申请录音和通知权限"
echo ""

# 设置Java 17环境
echo "☕ 1. 设置Java 17环境..."
export JAVA_HOME="/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 验证Java版本
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "✅ Java版本: $java_version"

if [[ ! "$java_version" =~ ^17\. ]]; then
    echo "⚠️  警告: 检测到的Java版本不是17，可能会导致构建问题"
    echo "请确保已安装Java 17并正确设置JAVA_HOME路径"
fi

# 检查设备连接
echo ""
echo "📱 2. 检查Android设备连接..."
device_count=$(adb devices | grep -v "List of devices" | grep -c "device")
if [ $device_count -eq 0 ]; then
    echo "❌ 未检测到连接的Android设备"
    echo "请确保："
    echo "- 设备已连接并启用USB调试"
    echo "- 运行 'adb devices' 确认设备可见"
    exit 1
fi
echo "✅ 检测到 $device_count 个设备"

# 构建系统预装版本（system flavor + release buildType）
echo ""
echo "🔨 3. 构建系统预装版本 (systemRelease)..."
echo "   - Flavor: system"
echo "   - BuildType: release"
echo "   - 签名: starry"
echo "   - sharedUserId: android.uid.system"
echo "   - 权限: 移除RECORD_AUDIO和POST_NOTIFICATIONS"
./gradlew assembleSystemRelease

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 查找APK文件（新格式：VoiceAssistant-版本号-System-Release.apk）
apk_dir="app/build/outputs/apk/systemRelease"
apk_path=$(find "$apk_dir" -name "VoiceAssistant-*-System-Release.apk" -type f | head -1)

if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "⚠️  未找到预期格式的APK文件，尝试查找所有APK..."
    apk_path=$(find "$apk_dir" -name "*.apk" -type f | head -1)
    if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
        echo "❌ APK文件不存在，查找目录: $apk_dir"
        echo "   尝试查找的文件模式: VoiceAssistant-*-System-Release.apk"
        ls -la "$apk_dir" 2>/dev/null || echo "   目录不存在"
        exit 1
    fi
fi

# 获取APK信息
apk_name=$(basename "$apk_path")
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK文件: $apk_name"
echo "📦 APK大小: $apk_size"

# 验证APK签名（系统版本需要使用系统签名）
echo ""
echo "🔐 4. 验证APK签名..."
aapt_output=$(aapt dump badging "$apk_path" 2>/dev/null | grep "package:")
if echo "$aapt_output" | grep -q "sharedUserId.*system"; then
    echo "✅ APK包含系统UID (android.uid.system)"
else
    echo "⚠️  警告: APK可能未正确设置系统UID"
fi

# 安装应用（系统应用需要root权限或通过系统方式安装）
echo ""
echo "📲 5. 安装应用到设备..."
echo "⚠️  注意：系统预装应用通常需要root权限或通过系统方式安装"
echo "   如果普通安装失败，请使用以下方式之一："
echo "   1. adb root && adb remount && adb push $apk_name /system/priv-app/"
echo "   2. 使用系统签名后通过系统更新包安装"

adb install -r "$apk_path"

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 普通安装失败（这是正常的，系统应用需要特殊安装方式）"
    echo ""
    echo "💡 系统应用安装方法："
    echo "   1. 确保设备已root"
    echo "   2. 运行以下命令："
    echo "      adb root"
    echo "      adb remount"
    echo "      adb push $apk_path /system/priv-app/VoiceAssistant/"
    echo "      adb shell chmod 644 /system/priv-app/VoiceAssistant/$apk_name"
    echo "      adb reboot"
    echo ""
    echo "   或者使用系统签名后通过系统更新包安装"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用（启动悬浮球启动器）
echo ""
echo "🎯 6. 启动应用（悬浮球启动器）..."
package_name="com.ai.voice"
activity_name="com.ai.voice.ui.floating.FloatingLauncherActivity"

adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败"
    exit 1
fi

echo "✅ 应用已启动"
echo "💡 系统版本特点："
echo "   - 不需要申请录音和通知权限"
echo "   - 使用系统UID运行"
echo "   - 自动拥有系统级权限"

# 获取应用进程ID
echo ""
echo "🔍 7. 获取应用进程信息..."
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

