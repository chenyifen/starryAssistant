#!/bin/bash

# 快速编译验证脚本
# 适用于无设备环境的编译验证

set -e

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 设置Java环境
log_info "设置Java 17环境..."
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# 显示Java版本
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
log_success "Java版本: $java_version"

# 清理并编译
log_info "清理构建缓存..."
./gradlew clean

log_info "编译主应用..."
./gradlew assembleNoModelsDebug

log_info "编译测试APK..."
./gradlew assembleNoModelsDebugAndroidTest

log_info "运行单元测试..."
./gradlew testNoModelsDebugUnitTest

log_success "编译验证完成！"
log_info "生成的APK文件："
echo "  - 主应用: app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
echo "  - 测试APK: app/build/outputs/apk/androidTest/noModels/debug/app-noModels-debug-androidTest.apk"