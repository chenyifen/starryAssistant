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
echo "🔨 3. 构建noModels变体..."
./gradlew assembleNoModelsDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 检查APK文件是否存在
apk_path="app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
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

# 启动应用
echo ""
echo "🎯 5. 启动应用..."
package_name="org.stypox.dicio.master"
activity_name="org.stypox.dicio.MainActivity"

adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败"
    exit 1
fi

echo "✅ 应用已启动"
