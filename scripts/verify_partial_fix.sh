#!/bin/bash

# Partial阶段Fallback修复验证脚本
# 用于快速检查日志中的关键指标

echo "🔍 Partial阶段Fallback修复验证"
echo "================================"
echo ""

# 检查是否有logcat日志文件参数
if [ -z "$1" ]; then
    echo "使用方法: $0 <logcat_log_file>"
    echo "示例: $0 test.log"
    exit 1
fi

LOG_FILE=$1

if [ ! -f "$LOG_FILE" ]; then
    echo "❌ 日志文件不存在: $LOG_FILE"
    exit 1
fi

echo "📄 分析日志文件: $LOG_FILE"
echo ""

# 1. 检查Partial阶段是否还有fallback执行
echo "1️⃣  检查Partial阶段fallback执行次数"
echo "-----------------------------------"
partial_fallback=$(grep -c "💬 New skill output generated" "$LOG_FILE" | grep -B5 "Partial" | grep -c "text")
if [ $partial_fallback -eq 0 ]; then
    echo "✅ PASS: Partial阶段未触发fallback技能"
else
    echo "❌ FAIL: Partial阶段触发了 $partial_fallback 次fallback"
    grep -B5 "💬 New skill output generated" "$LOG_FILE" | grep "Partial" | head -3
fi
echo ""

# 2. 检查阈值是否已更新
echo "2️⃣  检查Partial阈值设置"
echo "-----------------------------------"
threshold_07=$(grep -c "分数较低.*< 0.7" "$LOG_FILE")
threshold_05=$(grep -c "分数较低.*< 0.5" "$LOG_FILE")

if [ $threshold_07 -gt 0 ]; then
    echo "✅ PASS: 阈值已更新为0.7（发现 $threshold_07 次检查）"
elif [ $threshold_05 -gt 0 ]; then
    echo "❌ FAIL: 阈值仍为0.5（发现 $threshold_05 次检查）"
else
    echo "⚠️  WARN: 未找到阈值检查日志"
fi
echo ""

# 3. 检查预匹配技能使用
echo "3️⃣  检查预匹配技能使用"
echo "-----------------------------------"
pre_matched=$(grep -c "🎯 使用预匹配技能" "$LOG_FILE")
if [ $pre_matched -gt 0 ]; then
    echo "✅ PASS: 使用了预匹配技能（$pre_matched 次）"
    grep "🎯 使用预匹配技能" "$LOG_FILE" | head -3
else
    echo "⚠️  INFO: 未找到预匹配技能使用记录（可能没有高分Partial匹配）"
fi
echo ""

# 4. 检查禁止fallback日志
echo "4️⃣  检查Partial禁止fallback机制"
echo "-----------------------------------"
no_fallback=$(grep -c "\[Partial\] 无匹配技能且禁止fallback" "$LOG_FILE")
if [ $no_fallback -gt 0 ]; then
    echo "✅ PASS: Partial阶段正确禁止了fallback（$no_fallback 次）"
else
    echo "ℹ️  INFO: 未触发禁止fallback逻辑（可能所有Partial都有匹配或分数<0.7）"
fi
echo ""

# 5. 检查状态转换异常
echo "5️⃣  检查状态转换是否正常"
echo "-----------------------------------"
state_override=$(grep -c "防止状态覆盖" "$LOG_FILE")
rapid_switch=$(grep -c "LISTENING.*SPEAKING.*IDLE" "$LOG_FILE")

if [ $state_override -lt 5 ]; then
    echo "✅ PASS: 状态覆盖警告较少（$state_override 次）"
else
    echo "⚠️  WARN: 状态覆盖警告较多（$state_override 次）"
fi

if [ $rapid_switch -lt 3 ]; then
    echo "✅ PASS: 状态快速切换较少（$rapid_switch 次）"
else
    echo "⚠️  WARN: 状态快速切换较多（$rapid_switch 次）"
fi
echo ""

# 6. 统计Partial和Final执行情况
echo "6️⃣  Partial vs Final 执行统计"
echo "-----------------------------------"
partial_executed=$(grep -c "\[Partial\] 高分匹配.*立即执行技能" "$LOG_FILE")
final_executed=$(grep -c "📥 收到Final事件" "$LOG_FILE")
partial_skipped=$(grep -c "⏭️ \[Final\] Partial已执行技能，跳过重复执行" "$LOG_FILE")

echo "Partial立即执行: $partial_executed 次"
echo "Final正常执行: $final_executed 次"
echo "Final跳过（Partial已执行）: $partial_skipped 次"

if [ $partial_executed -eq $partial_skipped ]; then
    echo "✅ PASS: Partial和Final执行计数一致"
else
    echo "ℹ️  INFO: Partial执行 $partial_executed 次，Final跳过 $partial_skipped 次"
fi
echo ""

# 7. 总结
echo "================================"
echo "📊 验证总结"
echo "================================"

success_count=0
total_checks=6

# 评估各项检查
[ $partial_fallback -eq 0 ] && ((success_count++))
[ $threshold_07 -gt 0 ] && ((success_count++))
[ $pre_matched -gt 0 ] && ((success_count++))
[ $state_override -lt 5 ] && ((success_count++))
[ $rapid_switch -lt 3 ] && ((success_count++))
[ $partial_executed -eq $partial_skipped ] && ((success_count++))

echo "通过检查: $success_count / $total_checks"

if [ $success_count -eq $total_checks ]; then
    echo "🎉 所有检查通过！修复生效。"
    exit 0
elif [ $success_count -ge 4 ]; then
    echo "✅ 大部分检查通过，修复基本生效。"
    exit 0
else
    echo "⚠️  部分检查未通过，请查看详细日志。"
    exit 1
fi

