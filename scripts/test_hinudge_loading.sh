#!/bin/bash

# 测试HiNudge模型加载修复
echo "🔍 测试HiNudge模型加载修复..."

# 安装应用
echo "📱 安装应用..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

# 启动应用
echo "🚀 启动应用..."
adb shell am start -n org.stypox.dicio/.MainActivity

sleep 3

# 启动悬浮窗
echo "🎈 启动悬浮窗..."
adb shell am start -n org.stypox.dicio/.ui.floating.FloatingWindowService

sleep 2

echo ""
echo "📊 监控HiNudge模型加载日志..."
echo "应该看到："
echo "1. 🚀 Local models found, loading immediately..."
echo "2. ✅ HiNudge model loaded successfully"
echo "3. 状态应该变为 Loaded"
echo ""

# 监控模型加载日志
timeout 15 adb logcat -s "HiNudgeOpenWakeWordDevice:*" "WakeService:*" | grep -E "(HiNudge|Loading|Loaded|Error|❌|✅|🚀|🔄)" || echo "监控完成"

echo ""
echo "🏁 测试完成"
echo ""
echo "📋 预期结果："
echo "✅ 应该看到模型在初始化时直接加载"
echo "✅ 不应该有无限循环的加载尝试"
echo "✅ 状态应该从 NotLoaded → Loading → Loaded"
