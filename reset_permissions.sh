#!/bin/bash

echo "🔄 AI Voice Assistant - 重置权限工具"
echo "========================================"
echo ""
echo "此工具会清除应用的所有数据和权限历史，"
echo "让您可以重新授予权限。"
echo ""

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
    echo "⚠️  应用未安装，无需重置"
    exit 0
fi
echo "✅ 应用已安装"

# 显示当前权限状态
echo ""
echo "📋 3. 当前权限状态："
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
adb shell dumpsys package $package_name | grep -A 20 "runtime permissions:" | head -25
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 确认操作
echo ""
read -p "⚠️  确定要清除应用数据吗？这会删除所有设置和权限历史 (y/N): " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "❌ 操作已取消"
    exit 0
fi

# 清除应用数据
echo ""
echo "🗑️  4. 清除应用数据..."
adb shell pm clear $package_name

if [ $? -eq 0 ]; then
    echo "✅ 应用数据已清除"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 重置完成！"
    echo ""
    echo "现在您可以："
    echo "1. 重新启动应用（点击应用图标或运行 ./run.sh）"
    echo "2. 应用会重新弹出权限请求对话框"
    echo "3. 点击\"允许\"授予所有必要权限"
    echo ""
    echo "💡 提示：如果仍然无法授予权限，请尝试："
    echo "   - 卸载应用：adb uninstall $package_name"
    echo "   - 重新安装：./run.sh"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo "❌ 清除应用数据失败"
    exit 1
fi

