#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
    if command -v /usr/libexec/java_home &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || /usr/libexec/java_home -v 11 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || echo "")
    fi
    
    if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
        echo "警告: 无法自动检测 JAVA_HOME，请手动设置"
        echo "例如: export JAVA_HOME=\$(/usr/libexec/java_home -v 11)"
    fi
fi

echo "=========================================="
echo "开始编译 APK (assemblehyitRelease)"
echo "JAVA_HOME: ${JAVA_HOME:-未设置}"
echo "=========================================="

SKIP_LINT=${SKIP_LINT:-true}
if [ "$SKIP_LINT" = "true" ]; then
    echo "跳过 lint 检查以加快编译速度"
    ./gradlew clean assemblehyitRelease -x lintVitalReportHyitRelease -x lintVitalAnalyzeHyitRelease 2>&1 | tee /tmp/gradle_build.log
else
    ./gradlew clean assemblehyitRelease 2>&1 | tee /tmp/gradle_build.log
fi

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
