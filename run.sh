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

# 构建应用（不依赖设备连接）
echo ""
echo "🔨 3. 构建 Debug 版本（normal flavor）..."
# 先清理旧的构建，确保完整构建
echo "   清理旧的构建文件..."
./gradlew clean > /dev/null 2>&1
echo "   开始构建..."
./gradlew assembleNormalDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 读取版本号
VERSION=$(cat VERSION 2>/dev/null | tr -d '\n\r' || echo "3.19.43")
echo "📦 当前版本号: $VERSION"

# 查找 normal flavor 的 Debug APK（排除 system 版本）
echo ""
echo "🔎 4. 查找 Normal Debug APK..."

# 方法1：优先使用 normal/debug 目录（使用版本号）
apk_path="app/build/outputs/apk/normal/debug/VoiceAssistant-${VERSION}-Debug.apk"

# 方法2：如果固定路径不存在，使用通配符查找
if [ ! -f "$apk_path" ]; then
    apk_path=$(ls app/build/outputs/apk/normal/debug/VoiceAssistant-*-Debug.apk 2>/dev/null | head -1)
fi

# 方法3：在整个构建目录中查找，但排除 system 版本
if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "⚠️  未找到 normal/debug 目录，尝试查找排除 System 版本的 APK..."
    apk_path=$(find app/build/outputs/apk -type f -name "VoiceAssistant-*-Debug.apk" ! -name "*-System-*" ! -path "*/system/*" | head -1)
fi

if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "❌ Normal Debug APK 文件未找到"
    echo "   查找路径："
    echo "   1. app/build/outputs/apk/normal/debug/VoiceAssistant-*-Debug.apk"
    echo "   2. 排除 System 版本的所有 Debug APK"
    echo ""
    echo "   现有APK文件:"
    find app/build/outputs/apk -name "*.apk" -type f 2>/dev/null || echo "   未找到任何APK"
    echo ""
    echo "💡 提示: 请确保已构建 Normal Debug 版本："
    echo "   ./gradlew assembleNormalDebug"
    exit 1
fi

# 验证不是 System 版本
if echo "$apk_path" | grep -qi "system"; then
    echo "❌ 错误: 找到的是 System 版本 APK，而不是 Normal 版本"
    echo "   文件: $apk_path"
    echo ""
    echo "💡 System 版本会移除关键权限声明，导致权限请求失败"
    echo "   请重新构建 Normal 版本："
    echo "   ./gradlew assembleNormalDebug"
    exit 1
fi

apk_name=$(basename "$apk_path")
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK文件: $apk_name"
echo "📦 APK大小: $apk_size"

# 检查设备连接（用于安装和运行）
echo ""
echo "📱 5. 检查Android设备连接..."
device_count=$(adb devices 2>/dev/null | grep -v "List of devices" | grep -c "device" || echo "0")
if [ $device_count -eq 0 ]; then
    echo "⚠️  未检测到连接的Android设备"
    echo "   跳过安装和运行步骤"
    echo "   APK文件位置: $apk_path"
    echo ""
    echo "💡 提示: 如需安装和运行，请："
    echo "   - 连接Android设备并启用USB调试"
    echo "   - 运行 'adb devices' 确认设备可见"
    echo "   - 然后手动安装: adb install -r \"$apk_path\""
    exit 0
fi
echo "✅ 检测到 $device_count 个设备"

# 安装应用
echo ""
echo "📲 6. 安装应用到设备..."
apk_dir=$(dirname "$apk_path")
split_apks=("$apk_dir"/*.apk)
if [ ${#split_apks[@]} -gt 1 ]; then
  echo "🔧 检测到${#split_apks[@]}个APK拆分文件，使用多APK安装"
  printf "   -> %s\n" "${split_apks[@]}"
  adb install-multiple -r "${split_apks[@]}"
else
  adb install -r "$apk_path"
fi

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用（启动悬浮球启动器）
echo ""
echo "🎯 7. 启动应用（悬浮球启动器）..."
package_name="com.ai.voice"
activity_name="com.ai.voice.ui.floating.FloatingLauncherActivity"

echo "💡 提示: 应用启动后会自动请求必需权限，请在弹窗中授予："
echo "   - 录音权限（必需）"
echo "   - 通知权限（必需）"
echo "   - 位置权限（必需，用于设备激活）"
echo "   - 悬浮窗权限（必需）"
echo ""

adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败"
    exit 1
fi

echo "✅ 应用已启动"

# 获取应用进程ID
echo ""
echo "🔍 8. 获取应用进程信息..."
sleep 2  # 等待应用完全启动

# 先尝试pidof，失败则回退到ps
app_pid=$(adb shell pidof $package_name 2>/dev/null | tr -d '\r')
if [ -z "$app_pid" ]; then
    app_pid=$(adb shell ps | grep "$package_name" | awk '{print $2}' | head -1)
fi
if [ -n "$app_pid" ]; then
    echo "📱 应用进程ID: $app_pid"
    echo "💡 可以使用以下命令监控特定进程:"
    echo "   adb shell top -p $app_pid"
    echo "   adb shell dumpsys meminfo $app_pid"
else
    echo "⚠️ 未能获取应用进程ID，应用可能未完全启动"
fi

echo ""
echo "📋 9. 显示关键调试日志（按Ctrl+C退出）:"
if [ -n "$app_pid" ]; then
    # 通过PID与关键模块标签联合过滤，确保看到激活与授权相关日志
    adb logcat | grep --line-buffered -E "($app_pid|Activation|LicenseActivation|ActivationChecker)"
else
    # 无PID时按包名与标签过滤
    adb logcat | grep -i -E "(com\.ai\.voice|Activation|LicenseActivation|ActivationChecker)"
fi
