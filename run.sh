#!/bin/bash

echo "🚀 Dicio Android 构建、安装和启动脚本"
echo "========================================"

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

# 构建应用
echo ""
echo "🔨 3. 构建hyundaiit变体..."
./gradlew assembleHyundaiitDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 检查APK文件是否存在
apk_path="app/build/outputs/apk/hyundaiit/debug/app-hyundaiit-debug.apk"
if [ ! -f "$apk_path" ]; then
    echo "❌ APK文件不存在: $apk_path"
    exit 1
fi

# 获取APK信息
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK大小: $apk_size"

# 安装应用
echo ""
echo "📲 4. 安装应用到设备..."
adb install -r "$apk_path"

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用（启动悬浮球启动器）
echo ""
echo "🎯 5. 启动应用（悬浮球启动器）..."
package_name="com.ai.voice"
activity_name="com.ai.voice.ui.floating.FloatingLauncherActivity"

echo "📱 执行命令: adb shell am start -n $package_name/$activity_name"
adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败 - 检查设备连接和权限"
    exit 1
fi

echo "✅ 应用启动命令执行成功"

# 获取应用进程ID
echo ""
echo "🔍 获取应用进程信息..."
sleep 3  # 等待应用完全启动

echo "🔍 检查进程列表..."
adb shell ps | grep -i "$package_name" || echo "⚠️  在进程列表中未找到应用"

# 尝试多种方式获取进程ID
echo "🔍 尝试获取进程ID..."
app_pid=$(adb shell ps 2>/dev/null | grep "$package_name" | grep -v grep | awk '{print $2}' | head -1)

# 如果上面的命令失败，尝试另一种方式
if [ -z "$app_pid" ]; then
    echo "🔄 尝试第二种方法获取进程ID..."
    app_pid=$(adb shell ps 2>/dev/null | grep "$package_name" | awk 'NR==1{print $2}')
fi

# 最后尝试通过pidof命令（如果设备支持）
if [ -z "$app_pid" ]; then
    echo "🔄 尝试pidof命令..."
    app_pid=$(adb shell pidof "$package_name" 2>/dev/null)
fi

if [ -n "$app_pid" ]; then
    echo "📱 应用进程ID: $app_pid"
    echo "💡 可以使用以下命令监控特定进程:"
    echo "   adb shell top -p $app_pid"
    echo "   adb shell dumpsys meminfo $app_pid"

    # 启动日志监控（只过滤特定进程的日志）
    echo ""
    echo "📋 启动应用日志监控..."
    echo "💡 按Ctrl+C停止日志监控"
    echo "🔍 过滤进程ID: $app_pid"
    adb logcat | grep "$app_pid"
else
    echo "⚠️ 未能获取应用进程ID，应用可能启动失败或已退出"
    echo "🔍 调试信息:"

    # 检查应用是否已安装
    echo "📦 检查应用安装状态..."
    adb shell pm list packages | grep "$package_name" || echo "❌ 应用未安装"

    # 检查应用启动日志
    echo "📋 检查最近的应用启动日志..."
    adb logcat -d | grep -i "$package_name" | tail -10

    echo ""
    echo "💡 建议检查步骤:"
    echo "1. 确认设备已连接: adb devices"
    echo "2. 检查应用是否正确安装"
    echo "3. 检查设备权限设置"
    echo "4. 手动启动日志监控: adb logcat | grep \"$package_name\""
fi
