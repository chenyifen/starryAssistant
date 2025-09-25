#!/bin/bash

# 测试ASR和WakeService音频协调的脚本
# 通过模拟点击麦克风按钮来触发ASR，观察音频协调机制

echo "🧪 测试ASR和WakeService音频协调"
echo "=================================="

# 清空日志
echo "📝 清空日志..."
adb logcat -c

# 确保应用在前台
echo "🚀 确保应用在前台..."
adb shell am start -n org.stypox.dicio.master/org.stypox.dicio.MainActivity

sleep 3

echo ""
echo "🔍 检查WakeService当前状态..."
wake_status=$(adb logcat -d | grep -E "(WakeService.*AudioRecord.*started|WakeService.*listening)" | tail -1)
if [ -n "$wake_status" ]; then
    echo "✅ WakeService状态: $wake_status"
else
    echo "❌ WakeService可能未运行"
    exit 1
fi

echo ""
echo "🎤 模拟点击麦克风按钮启动ASR..."
# 模拟点击屏幕中央（通常是麦克风按钮的位置）
adb shell input tap 540 960

echo "⏳ 等待ASR启动和音频协调..."
sleep 2

echo ""
echo "🔍 检查音频协调日志..."
# 查找音频协调相关的日志
coordination_logs=$(adb logcat -d | grep -E "(⏸️|▶️|Pausing.*AudioRecord|Resuming.*AudioRecord|audioRecordPaused)" | tail -10)
if [ -n "$coordination_logs" ]; then
    echo "✅ 找到音频协调日志:"
    echo "$coordination_logs"
else
    echo "ℹ️ 未找到音频协调日志"
fi

echo ""
echo "🔍 检查ASR启动日志..."
asr_logs=$(adb logcat -d | grep -E "(SenseVoice.*开始|STT.*start|🎤.*Starting)" | tail -5)
if [ -n "$asr_logs" ]; then
    echo "✅ ASR启动日志:"
    echo "$asr_logs"
else
    echo "ℹ️ 未找到ASR启动日志"
fi

echo ""
echo "🔍 检查AudioRecord冲突..."
audio_conflicts=$(adb logcat -d | grep -E "(AudioFlinger.*status.*-1|AudioRecord.*status.*-1|AudioRecord.*initialization.*failed)" | tail -3)
if [ -n "$audio_conflicts" ]; then
    echo "❌ 发现AudioRecord冲突:"
    echo "$audio_conflicts"
else
    echo "✅ 未发现AudioRecord冲突"
fi

echo ""
echo "⏳ 等待ASR完成和WakeService恢复..."
sleep 5

echo ""
echo "🔍 检查WakeService恢复状态..."
resume_logs=$(adb logcat -d | grep -E "(▶️|Resuming.*AudioRecord|AudioRecord.*resumed)" | tail -3)
if [ -n "$resume_logs" ]; then
    echo "✅ WakeService恢复日志:"
    echo "$resume_logs"
else
    echo "ℹ️ 未找到WakeService恢复日志"
fi

echo ""
echo "📊 最终状态检查..."
final_wake_status=$(adb logcat -d | grep -E "(WakeService.*frames|WakeService.*listening)" | tail -1)
if [ -n "$final_wake_status" ]; then
    echo "✅ WakeService最终状态: $final_wake_status"
else
    echo "⚠️ WakeService状态不明"
fi

echo ""
echo "🏁 测试完成！"
echo ""
echo "📝 结果分析:"
echo "1. 如果看到暂停和恢复日志，说明音频协调机制工作正常"
echo "2. 如果没有AudioRecord冲突，说明资源管理有效"
echo "3. 如果WakeService能够恢复，说明整个流程正常"
echo ""
echo "💡 提示: 如果没有看到协调日志，可能是因为:"
echo "   - 麦克风按钮位置不对，需要手动点击"
echo "   - ASR没有启动"
echo "   - 音频协调逻辑没有触发"

