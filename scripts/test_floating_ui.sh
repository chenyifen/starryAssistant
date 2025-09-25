#!/bin/bash

# 测试新的悬浮窗UI功能
echo "🚀 开始测试悬浮窗UI功能..."

# 检查是否连接了设备
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有检测到Android设备，请连接设备后重试"
    exit 1
fi

echo "📱 检测到Android设备"

# 构建应用
echo "🔨 构建应用..."
./gradlew assembleWithModelsDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 安装应用
echo "📦 安装应用..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用
echo "🎯 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

# 等待应用启动
sleep 3

# 检查悬浮窗权限
echo "🔍 检查悬浮窗权限..."
adb shell dumpsys package org.stypox.dicio | grep -i "overlay"

echo "📋 测试步骤："
echo "1. 应用应该会自动请求悬浮窗权限"
echo "2. 授予权限后，应该会看到中央能量球悬浮窗"
echo "3. 能量球应该有呼吸动画和极光纹理"
echo "4. 点击能量球应该展开命令建议"
echo "5. 右上角应该有设置图标"
echo "6. 说\"小艺小艺\"应该触发唤醒动画"

echo "🎉 测试脚本执行完成！请手动验证上述功能。"
