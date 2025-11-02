#!/bin/bash

# DeviceControl测试报告拉取脚本
# 用于从设备拉取测试报告并生成分析

set -e

echo "📄 拉取DeviceControl测试报告..."
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "================================"

# 检查ADB连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 没有找到连接的Android设备"
    exit 1
fi

DEVICE_INFO=$(adb shell getprop ro.product.model)
echo "✅ 设备已连接: $DEVICE_INFO"

# 创建报告目录
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
REPORT_DIR="./test_reports_$TIMESTAMP"
mkdir -p "$REPORT_DIR"

echo "📁 报告目录: $REPORT_DIR"

# 拉取应用外部存储中的测试报告
echo "🔍 搜索设备上的测试报告..."
adb shell "find /sdcard/Android/data/com.ai.voice/files -name 'device_control_test_report_*.txt' -type f 2>/dev/null" | while read -r file; do
    if [ -n "$file" ] && [ "$file" != "" ]; then
        local_file="$REPORT_DIR/$(basename "$file")"
        if adb pull "$file" "$local_file" 2>/dev/null; then
            echo "✅ 报告已拉取: $(basename "$file")"
        fi
    fi
done

# 拉取最新的logcat日志
echo "📋 拉取相关日志..."
adb logcat -d -s "TestRunner" "DeviceControlInstrumentationTest" "DeviceControlSkill" "AutoTest" > "$REPORT_DIR/recent_logcat.log" 2>/dev/null || true

# 检查是否有Gradle测试报告
if [ -d "app/build/reports/androidTests/connected" ]; then
    cp -r app/build/reports/androidTests/connected "$REPORT_DIR/gradle_reports"
    echo "✅ Gradle测试报告已复制"
fi

# 生成报告分析
ANALYSIS_FILE="$REPORT_DIR/report_analysis.txt"
echo "📊 生成报告分析..."

cat > "$ANALYSIS_FILE" << EOF
DeviceControl测试报告分析
========================
拉取时间: $(date '+%Y-%m-%d %H:%M:%S')
设备信息: $DEVICE_INFO

文件清单:
EOF

# 列出所有拉取的文件
echo "--------" >> "$ANALYSIS_FILE"
ls -la "$REPORT_DIR" >> "$ANALYSIS_FILE"
echo "" >> "$ANALYSIS_FILE"

# 分析测试报告内容
for report in "$REPORT_DIR"/device_control_test_report_*.txt; do
    if [ -f "$report" ]; then
        echo "测试报告内容分析:" >> "$ANALYSIS_FILE"
        echo "=================" >> "$ANALYSIS_FILE"
        echo "文件: $(basename "$report")" >> "$ANALYSIS_FILE"
        echo "" >> "$ANALYSIS_FILE"
        
        # 提取关键信息
        if grep -q "测试总结" "$report" 2>/dev/null; then
            echo "📈 测试总结:" >> "$ANALYSIS_FILE"
            grep -A 20 "测试总结" "$report" >> "$ANALYSIS_FILE" 2>/dev/null || true
        fi
        
        if grep -q "成功率" "$report" 2>/dev/null; then
            echo "" >> "$ANALYSIS_FILE"
            echo "📊 成功率信息:" >> "$ANALYSIS_FILE"
            grep "成功率" "$report" >> "$ANALYSIS_FILE" 2>/dev/null || true
        fi
        
        if grep -q "失败" "$report" 2>/dev/null; then
            echo "" >> "$ANALYSIS_FILE"
            echo "❌ 失败信息:" >> "$ANALYSIS_FILE"
            grep -B 2 -A 2 "失败" "$report" >> "$ANALYSIS_FILE" 2>/dev/null || true
        fi
        
        echo "" >> "$ANALYSIS_FILE"
        echo "完整报告内容:" >> "$ANALYSIS_FILE"
        echo "=============" >> "$ANALYSIS_FILE"
        cat "$report" >> "$ANALYSIS_FILE" 2>/dev/null || true
        echo "" >> "$ANALYSIS_FILE"
        break
    fi
done

# 分析logcat日志
if [ -f "$REPORT_DIR/recent_logcat.log" ] && [ -s "$REPORT_DIR/recent_logcat.log" ]; then
    echo "Logcat关键信息:" >> "$ANALYSIS_FILE"
    echo "===============" >> "$ANALYSIS_FILE"
    
    # 提取测试相关的关键日志
    grep -i "test\|error\|fail\|success" "$REPORT_DIR/recent_logcat.log" | tail -20 >> "$ANALYSIS_FILE" 2>/dev/null || true
fi

echo "📋 报告分析已保存到: $ANALYSIS_FILE"

# 显示拉取结果
echo ""
echo "🎯 报告拉取完成:"
echo "==============="
echo "📁 报告目录: $REPORT_DIR"

# 统计拉取的文件
REPORT_COUNT=$(find "$REPORT_DIR" -name "device_control_test_report_*.txt" | wc -l)
echo "📄 测试报告文件: $REPORT_COUNT 个"

if [ "$REPORT_COUNT" -gt 0 ]; then
    echo "✅ 成功拉取测试报告"
    echo "📊 分析文件: $ANALYSIS_FILE"
    
    # 显示简要分析
    echo ""
    echo "📈 快速预览:"
    echo "==========="
    if [ -f "$ANALYSIS_FILE" ]; then
        head -30 "$ANALYSIS_FILE"
    fi
else
    echo "⚠️  未找到测试报告文件"
    echo "请确保已运行DeviceControl仪器测试"
fi

echo ""
echo "🎉 报告拉取完成!"