#!/bin/bash
# Git Post-Commit Hook - 自动编译并保存APK
# 每次git commit后自动触发

set -e

# 获取git仓库根目录
PROJECT_DIR="$(git rev-parse --show-toplevel)"
cd "$PROJECT_DIR"

# 目标目录
TARGET_DIR="../app_version/dicio-android"

# 创建目标目录（如果不存在）
mkdir -p "$TARGET_DIR"

# 计算编号：基于目标目录下已有的APK数量
EXISTING_APK_COUNT=$(find "$TARGET_DIR" -name "*.apk" 2>/dev/null | wc -l | tr -d ' ')
APK_NUMBER=$((EXISTING_APK_COUNT + 1))

# 获取git信息
COMMIT_HASH=$(git rev-parse --short HEAD)
COMMIT_MSG=$(git log -1 --pretty=%B | head -n 1 | tr -d '\n' | tr ' ' '_' | tr '/' '_')
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# 清理commit message，只保留前30个字符
COMMIT_MSG_SHORT=$(echo "$COMMIT_MSG" | cut -c1-30)

# APK名称: 编号_dicio_gitmsg_time
APK_NAME="${APK_NUMBER}_dicio_${COMMIT_MSG_SHORT}_${TIMESTAMP}.apk"

echo "=========================================="
echo "Git Post-Commit Hook - 开始编译"
echo "=========================================="
echo "Commit Hash: $COMMIT_HASH"
echo "APK Number: $APK_NUMBER (基于已有APK数量)"
echo "Commit Message: $COMMIT_MSG_SHORT"
echo "Timestamp: $TIMESTAMP"
echo "APK Name: $APK_NAME"
echo "=========================================="

# 设置Java 17环境
echo "设置Java 17环境..."
export JAVA_HOME="/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 编译APK (withModels debug版本)
echo "开始编译 WithModels Debug APK..."
"$PROJECT_DIR/gradlew" assembleWithModelsDebug

# 检查编译是否成功
APK_SOURCE="app/build/outputs/apk/withModels/debug/app-withModels-debug.apk"
if [ ! -f "$APK_SOURCE" ]; then
    echo "错误: APK编译失败，未找到 $APK_SOURCE"
    exit 1
fi

# 复制并重命名APK
echo "复制APK到: $TARGET_DIR/$APK_NAME"
cp "$APK_SOURCE" "$TARGET_DIR/$APK_NAME"

# 创建版本信息文件
VERSION_INFO="$TARGET_DIR/${APK_NUMBER}_version_info.txt"
cat > "$VERSION_INFO" << EOF
APK Version Information
=======================
APK Number: $APK_NUMBER
Commit Hash: $COMMIT_HASH
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

