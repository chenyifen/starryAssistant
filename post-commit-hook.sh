#!/bin/bash
# Git Post-Commit Hook - 自动编译并保存APK
# 每次git commit后自动触发

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 获取git信息
COMMIT_HASH=$(git rev-parse --short HEAD)
COMMIT_COUNT=$(git rev-list --count HEAD)
COMMIT_MSG=$(git log -1 --pretty=%B | head -n 1 | tr -d '\n' | tr ' ' '_' | tr '/' '_')
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# 清理commit message，只保留前30个字符
COMMIT_MSG_SHORT=$(echo "$COMMIT_MSG" | cut -c1-30)

# APK名称: 编号_dicio_gitmsg_time
APK_NAME="${COMMIT_COUNT}_dicio_${COMMIT_MSG_SHORT}_${TIMESTAMP}.apk"

# 目标目录
TARGET_DIR="../app_version/dicio-android"

echo "=========================================="
echo "Git Post-Commit Hook - 开始编译"
echo "=========================================="
echo "Commit Hash: $COMMIT_HASH"
echo "Commit Count: $COMMIT_COUNT"
echo "Commit Message: $COMMIT_MSG_SHORT"
echo "Timestamp: $TIMESTAMP"
echo "APK Name: $APK_NAME"
echo "=========================================="

# 设置Java 17环境
echo "设置Java 17环境..."
export JAVA_HOME="/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 编译APK (debug版本)
echo "开始编译 Debug APK..."
"$PROJECT_DIR/gradlew" assembleDebug

# 检查编译是否成功
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "错误: APK编译失败，未找到 app-debug.apk"
    exit 1
fi

# 创建目标目录
mkdir -p "$TARGET_DIR"

# 复制并重命名APK
echo "复制APK到: $TARGET_DIR/$APK_NAME"
cp "app/build/outputs/apk/debug/app-debug.apk" "$TARGET_DIR/$APK_NAME"

# 创建版本信息文件
VERSION_INFO="$TARGET_DIR/${COMMIT_COUNT}_version_info.txt"
cat > "$VERSION_INFO" << EOF
APK Version Information
=======================
Commit Hash: $COMMIT_HASH
Commit Count: $COMMIT_COUNT
Commit Message: $COMMIT_MSG
Build Time: $TIMESTAMP
APK File: $APK_NAME
Git Branch: $(git branch --show-current)
Git Tag: $(git describe --tags --always)
=======================
EOF

echo "=========================================="
echo "编译完成！"
echo "APK已保存到: $TARGET_DIR/$APK_NAME"
echo "版本信息已保存到: $VERSION_INFO"
echo "=========================================="

