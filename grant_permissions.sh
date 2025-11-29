#!/bin/bash

echo "🔐 AI Voice Assistant - 权限授予工具"
echo "========================================"

package_name="com.ai.voice"

# 检查设备连接
echo "📱 1. 检查设备连接..."
device_count=$(adb devices | grep -v "List of devices" | grep -c "device")
if [ $device_count -eq 0 ]; then
    echo "❌ 未检测到连接的Android设备"
    echo "请确保设备已连接并启用USB调试"
    exit 1
fi
echo "✅ 检测到 $device_count 个设备"

# 检查应用是否已安装
echo ""
echo "📦 2. 检查应用安装状态..."
installed=$(adb shell pm list packages | grep -c "$package_name")
if [ $installed -eq 0 ]; then
    echo "❌ 应用未安装"
    echo "请先运行 ./run.sh 安装应用"
    exit 1
fi
echo "✅ 应用已安装"

# 授予所有必需权限
echo ""
echo "🔐 3. 授予所有必需权限..."
echo ""

# 1. 录音权限（必需）
echo "   [1/4] 录音权限 (RECORD_AUDIO)..."
adb shell pm grant $package_name android.permission.RECORD_AUDIO 2>/dev/null
if [ $? -eq 0 ]; then
    echo "         ✅ 已授予"
else
    echo "         ⚠️  授予失败或已授予"
fi

# 2. 通知权限（必需，Android 13+）
echo "   [2/4] 通知权限 (POST_NOTIFICATIONS)..."
adb shell pm grant $package_name android.permission.POST_NOTIFICATIONS 2>/dev/null
if [ $? -eq 0 ]; then
    echo "         ✅ 已授予"
else
    echo "         ⚠️  授予失败（可能是Android 12及以下版本）"
fi

# 3. 位置权限（用于获取MAC地址）
echo "   [3/4] 位置权限 (ACCESS_FINE_LOCATION)..."
adb shell pm grant $package_name android.permission.ACCESS_FINE_LOCATION 2>/dev/null
adb shell pm grant $package_name android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
if [ $? -eq 0 ]; then
    echo "         ✅ 已授予"
else
    echo "         ⚠️  授予失败或已授予"
fi

# 4. 悬浮窗权限（必需）
echo "   [4/4] 悬浮窗权限 (SYSTEM_ALERT_WINDOW)..."
adb shell appops set $package_name SYSTEM_ALERT_WINDOW allow 2>/dev/null
if [ $? -eq 0 ]; then
    echo "         ✅ 已授予"
else
    echo "         ⚠️  授予失败或已授予"
fi

echo ""
echo "✅ 权限授予完成"

# 显示当前权限状态
echo ""
echo "📋 4. 查看当前权限状态..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
adb shell dumpsys package $package_name | grep -A 1 "runtime permissions:" | head -20
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
echo "💡 提示："
echo "   - 如果权限授予失败，请在设备上手动授予"
echo "   - 可以运行 'adb shell pm grant $package_name <权限名>' 单独授予权限"
echo "   - 查看所有权限: adb shell dumpsys package $package_name | grep permission"
echo ""

