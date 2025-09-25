#!/bin/bash

# 测试音频协调机制的脚本
# 验证WakeService和ASR之间的音频资源协调是否正常工作

echo "🧪 测试音频协调机制"
echo "===================="

# 清空日志
echo "📝 清空日志..."
adb logcat -c

# 启动应用
echo "🚀 启动Dicio应用..."
adb shell am start -n org.stypox.dicio.master/org.stypox.dicio.MainActivity

# 等待应用启动
echo "⏳ 等待应用启动和WakeService初始化..."
sleep 5

echo ""
echo "🔍 检查WakeService状态..."
# 检查WakeService是否正在运行
wake_service_logs=$(adb logcat -d | grep -E "(WakeService.*Starting|WakeService.*AudioRecord)" | tail -5)
if [ -n "$wake_service_logs" ]; then
    echo "✅ WakeService日志:"
    echo "$wake_service_logs"
else
    echo "❌ 未找到WakeService日志"
fi

echo ""
echo "🔍 检查HiNudge模型状态..."
# 检查HiNudge模型是否加载成功
hinudge_logs=$(adb logcat -d | grep -E "(HiNudge.*loaded|HiNudge.*Loading)" | tail -3)
if [ -n "$hinudge_logs" ]; then
    echo "✅ HiNudge模型日志:"
    echo "$hinudge_logs"
else
    echo "❌ 未找到HiNudge模型日志"
fi

echo ""
echo "🔍 检查AudioRecord初始化..."
# 检查是否有AudioRecord初始化错误
audio_errors=$(adb logcat -d | grep -E "(AudioFlinger.*status.*-1|AudioRecord.*status.*-1)" | tail -3)
if [ -n "$audio_errors" ]; then
    echo "❌ 发现AudioRecord错误:"
    echo "$audio_errors"
else
    echo "✅ 未发现AudioRecord初始化错误"
fi

echo ""
echo "🎤 模拟语音交互测试..."
echo "请在应用中点击麦克风按钮开始语音识别..."
echo "观察是否出现音频协调日志..."

# 监控10秒的实时日志，查找音频协调相关的日志
echo "📊 监控音频协调日志 (10秒)..."
timeout 10s adb logcat | grep -E "(⏸️|▶️|Pausing.*AudioRecord|Resuming.*AudioRecord|AudioRecord.*paused)" --line-buffered || true

echo ""
echo "📋 最终状态检查..."

# 检查最近的音频协调日志
coordination_logs=$(adb logcat -d | grep -E "(⏸️|▶️|Pausing.*AudioRecord|Resuming.*AudioRecord)" | tail -5)
if [ -n "$coordination_logs" ]; then
    echo "✅ 找到音频协调日志:"
    echo "$coordination_logs"
else
    echo "ℹ️ 未找到音频协调日志 (可能未触发语音识别)"
fi

# 检查当前WakeService状态
current_wake_logs=$(adb logcat -d | grep -E "(WakeService.*listening|WakeService.*AudioRecord)" | tail -3)
if [ -n "$current_wake_logs" ]; then
    echo "✅ 当前WakeService状态:"
    echo "$current_wake_logs"
fi

echo ""
echo "🏁 测试完成！"
echo ""
echo "📝 测试说明:"
echo "1. 如果看到 '⏸️ Pausing WakeService AudioRecord for ASR' 说明暂停机制工作"
echo "2. 如果看到 '▶️ Resuming WakeService AudioRecord after ASR' 说明恢复机制工作"
echo "3. 如果没有AudioRecord初始化错误，说明音频冲突已解决"
echo "4. 可以尝试说韩语唤醒词来测试完整流程"

