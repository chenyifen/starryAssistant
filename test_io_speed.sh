#!/bin/bash
# 测试设备I/O速度和模型加载性能

echo "================================"
echo "📊 ASR模型加载性能诊断测试"
echo "================================"
echo ""

# 测试1：纯文件读取速度
echo "🔍 测试1: 纯文件读取速度 (dd命令)"
echo "命令: dd if=model.onnx of=/dev/null"
adb shell "time dd if=/sdcard/Android/data/com.ai.voice/files/models/asr/sensevoice/model.onnx of=/dev/null bs=1M 2>&1" | tail -5
echo ""

# 测试2: 顺序读取速度
echo "🔍 测试2: 顺序读取速度 (cat命令)"
echo "命令: cat model.onnx > /dev/null"
adb shell "time cat /sdcard/Android/data/com.ai.voice/files/models/asr/sensevoice/model.onnx > /dev/null 2>&1"
echo ""

# 测试3: 查看文件系统类型
echo "🔍 测试3: 文件系统信息"
adb shell "df -h /sdcard/Android/data/com.ai.voice/files/models/asr/sensevoice/"
echo ""

# 测试4: 查看设备存储性能
echo "🔍 测试4: 存储设备信息"
adb shell "cat /proc/mounts | grep -E 'sdcard|data'"
echo ""

# 测试5: CPU信息
echo "🔍 测试5: CPU信息"
adb shell "cat /proc/cpuinfo | grep -E 'processor|Hardware|BogoMIPS' | head -10"
echo ""

# 测试6: 内存信息
echo "🔍 测试6: 内存信息"
adb shell "free -h"
echo ""

echo "================================"
echo "✅ 诊断测试完成"
echo "================================"

