#!/bin/bash

# 唤醒词测试脚本
# 将Java测试的WAV文件push到Android设备，然后运行测试

echo "========================================" echo "唤醒词Android测试脚本"
echo "========================================"

# Java测试数据目录
JAVA_TEST_DIR="/Users/user/AndroidStudioProjects/openWakeWord/test_data"
# Android测试目录
ANDROID_TEST_DIR="/sdcard/wake_test"

# 检查Java测试数据是否存在
if [ ! -d "$JAVA_TEST_DIR" ]; then
    echo "❌ Java测试数据目录不存在: $JAVA_TEST_DIR"
    exit 1
fi

# 创建Android测试目录
echo "📁 创建Android测试目录..."
adb shell mkdir -p "$ANDROID_TEST_DIR/positive"
adb shell mkdir -p "$ANDROID_TEST_DIR/negative"

# 推送正样本 (前20个)
echo "📤 推送正样本..."
positive_count=0
for file in "$JAVA_TEST_DIR/positive"/*.wav; do
    if [ -f "$file" ]; then
        adb push "$file" "$ANDROID_TEST_DIR/positive/" > /dev/null 2>&1
        ((positive_count++))
        if [ $positive_count -ge 20 ]; then
            break
        fi
    fi
done
echo "✅ 推送了 $positive_count 个正样本"

# 推送负样本 (前20个)
echo "📤 推送负样本..."
negative_count=0
for file in "$JAVA_TEST_DIR/negative"/*.wav; do
    if [ -f "$file" ]; then
        adb push "$file" "$ANDROID_TEST_DIR/negative/" > /dev/null 2>&1
        ((negative_count++))
        if [ $negative_count -ge 20 ]; then
            break
        fi
    fi
done
echo "✅ 推送了 $negative_count 个负样本"

# 创建触发文件
echo "🚀 创建测试触发文件..."
adb shell "echo 'trigger' > /sdcard/Android/data/com.ai.voice/files/run_wake_test"

echo "========================================"
echo "测试文件已推送，等待应用执行测试..."
echo "请使用: adb logcat | grep WakeWord"
echo "========================================"

# 启动logcat监听
adb logcat -c
adb logcat | grep -E "(WakeWordTest|HiNudgeOnnxV8)"

