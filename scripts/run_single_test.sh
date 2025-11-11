#!/bin/bash

# 执行单个测试用例的脚本
# 用法: ./run_single_test.sh testVolumeUp

if [ -z "$1" ]; then
    echo "用法: $0 <测试方法名>"
    echo "示例: $0 testVolumeUp"
    exit 1
fi

TEST_METHOD=$1
TEST_CLASS="com.ai.voice.skills.device_control.DeviceControlInstrumentationTest"

echo "执行测试用例: $TEST_CLASS#$TEST_METHOD"

adb shell am instrument -w \
  -e class ${TEST_CLASS}#${TEST_METHOD} \
  com.ai.voice.test/com.ai.voice.CustomTestRunner

