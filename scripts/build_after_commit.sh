#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "=========================================="
echo "开始编译 APK (assemblehyitRelease)"
echo "=========================================="

./gradlew clean assemblehyitRelease

if [ $? -eq 0 ]; then
    echo "=========================================="
    echo "✅ APK 编译成功"
    echo "=========================================="
    
    APK_PATH=$(find app/build/outputs/apk/hyitRelease -name "*.apk" -type f | head -1)
    if [ -n "$APK_PATH" ]; then
        echo "APK 位置: $APK_PATH"
        echo "文件大小: $(du -h "$APK_PATH" | cut -f1)"
    fi
else
    echo "=========================================="
    echo "❌ APK 编译失败"
    echo "=========================================="
    exit 1
fi
