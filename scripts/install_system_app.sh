#!/bin/bash
# 系统应用安装脚本 - 将APK安装到系统分区
# 用于factory reset后验证首次启动效果

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "系统应用安装脚本"
echo "=========================================="

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 未检测到设备，请连接设备并启用USB调试${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 设备已连接${NC}"

# 检查root权限
if ! adb shell su -c "echo test" > /dev/null 2>&1; then
    echo -e "${RED}❌ 设备需要root权限${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Root权限已确认${NC}"

# 查找APK文件（优先使用Release版本）
APK_DIR="app/build/outputs/apk/release"
APK_PATH=$(find "$APK_DIR" -name "VoiceAssistant-*-Release.apk" -type f | head -1)

if [ -z "$APK_PATH" ]; then
    # 如果没有Release版本，使用Debug版本
    APK_DIR="app/build/outputs/apk/debug"
    APK_PATH=$(find "$APK_DIR" -name "VoiceAssistant-*-Debug.apk" -type f | head -1)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ 未找到APK文件${NC}"
    echo "查找目录: $APK_DIR"
    echo "请先编译APK: ./run_release.sh 或 ./run.sh"
    exit 1
fi

APK_NAME=$(basename "$APK_PATH")
APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')

echo "=========================================="
echo "APK信息:"
echo "  文件: $APK_NAME"
echo "  大小: $APK_SIZE"
echo "  路径: $APK_PATH"
echo "=========================================="

# 获取版本信息
VERSION_NAME=$(echo "$APK_NAME" | grep -oP 'VoiceAssistant-\K[^-]+' | head -1)
echo "版本号: $VERSION_NAME"

# 确认安装
read -p "是否继续安装到系统分区？(y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消安装"
    exit 0
fi

# 1. 卸载旧版本（如果存在）
echo ""
echo "步骤1: 卸载旧版本..."
adb shell pm uninstall com.ai.voice 2>/dev/null || echo "  未找到已安装版本"

# 2. 挂载系统分区为可写
echo ""
echo "步骤2: 挂载系统分区..."
adb shell su -c "mount -o rw,remount /system" || adb shell su -c "mount -o rw,remount /"

# 3. 创建系统应用目录
echo ""
echo "步骤3: 创建系统应用目录..."
adb shell su -c "mkdir -p /system/priv-app/VoiceAssistant"

# 4. 推送APK到系统分区
echo ""
echo "步骤4: 推送APK到系统分区..."
adb push "$APK_PATH" /sdcard/VoiceAssistant.apk
adb shell su -c "cp /sdcard/VoiceAssistant.apk /system/priv-app/VoiceAssistant/VoiceAssistant.apk"
adb shell su -c "rm /sdcard/VoiceAssistant.apk"

# 5. 设置权限
echo ""
echo "步骤5: 设置文件权限..."
adb shell su -c "chmod 644 /system/priv-app/VoiceAssistant/VoiceAssistant.apk"
adb shell su -c "chown root:root /system/priv-app/VoiceAssistant/VoiceAssistant.apk"

# 6. 重新挂载系统分区为只读
echo ""
echo "步骤6: 重新挂载系统分区为只读..."
adb shell su -c "mount -o ro,remount /system" || adb shell su -c "mount -o ro,remount /"

# 7. 安装APK
echo ""
echo "步骤7: 安装APK..."
adb shell pm install -r /system/priv-app/VoiceAssistant/VoiceAssistant.apk

# 8. 验证安装
echo ""
echo "步骤8: 验证安装..."
INSTALLED_VERSION=$(adb shell dumpsys package com.ai.voice | grep versionName | head -1 | awk -F'=' '{print $2}' | tr -d '\r')
if [ -n "$INSTALLED_VERSION" ]; then
    echo -e "${GREEN}✅ 安装成功！${NC}"
    echo "  已安装版本: $INSTALLED_VERSION"
    echo "  应用包名: com.ai.voice"
    echo "  安装位置: /system/priv-app/VoiceAssistant/VoiceAssistant.apk"
else
    echo -e "${RED}❌ 安装验证失败${NC}"
    exit 1
fi

echo ""
echo "=========================================="
echo "安装完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 重启设备: adb reboot"
echo "2. 或进行factory reset验证首次启动效果"
echo "3. 查看启动日志: adb logcat | grep -E 'WakeService|FloatingWindow|BootBroadcast'"
echo ""

