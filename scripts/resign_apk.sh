#!/bin/bash

# APK重签名脚本
# 用法: ./scripts/resign_apk.sh [选项]
#
# 选项:
#   -a, --apk <path>            APK文件路径（必需）
#   -k, --keystore <path>       Keystore文件路径（必需）
#   -s, --storepass <password>  Keystore密码（必需）
#   -p, --keypass <password>     Key密码（可选，默认使用storepass）
#   -n, --alias <alias>          Key别名（必需）
#   -o, --output <path>         输出APK路径（可选，默认添加-signed后缀）
#   -h, --help                  显示帮助信息
#
# 示例:
#   ./scripts/resign_apk.sh -a app.apk -k release.keystore -s android123 -n release
#   ./scripts/resign_apk.sh --apk app.apk --keystore release.keystore --storepass android123 --keypass android123 --alias release

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认值
APK_PATH=""
KEYSTORE_PATH=""
STOREPASS=""
KEYPASS=""
ALIAS=""
OUTPUT_PATH=""
ZIPALIGN_PATH=""
APKSIGNER_PATH=""

# 显示帮助信息
show_help() {
    cat << EOF
${BLUE}APK重签名脚本${NC}

${GREEN}用法:${NC}
    $0 [选项]

${GREEN}选项:${NC}
    -a, --apk <path>            APK文件路径（必需）
    -k, --keystore <path>       Keystore文件路径（必需）
    -s, --storepass <password>  Keystore密码（必需）
    -p, --keypass <password>     Key密码（可选，默认使用storepass）
    -n, --alias <alias>          Key别名（必需）
    -o, --output <path>         输出APK路径（可选，默认添加-signed后缀）
    -h, --help                  显示帮助信息

${GREEN}示例:${NC}
    $0 -a app.apk -k release.keystore -s android123 -n release
    $0 --apk app.apk --keystore release.keystore --storepass android123 --keypass android123 --alias release --output app-signed.apk

${GREEN}说明:${NC}
    1. 脚本会自动查找Android SDK中的zipalign和apksigner工具
    2. 如果未指定输出路径，会在原APK同目录下生成带-signed后缀的文件
    3. 签名完成后会自动验证签名并显示证书信息
EOF
}

# 查找Android SDK工具
find_android_tools() {
    # 常见的Android SDK路径
    local sdk_paths=(
        "$HOME/Library/Android/sdk"
        "$ANDROID_HOME"
        "$ANDROID_SDK_ROOT"
        "/Users/$USER/Library/Android/sdk"
    )
    
    # 查找build-tools目录
    for sdk_path in "${sdk_paths[@]}"; do
        if [ -d "$sdk_path/build-tools" ]; then
            # 查找最新的build-tools版本
            local latest_version=$(ls -1 "$sdk_path/build-tools" 2>/dev/null | sort -V | tail -1)
            if [ -n "$latest_version" ]; then
                local zipalign="$sdk_path/build-tools/$latest_version/zipalign"
                local apksigner="$sdk_path/build-tools/$latest_version/apksigner"
                
                if [ -f "$zipalign" ] && [ -f "$apksigner" ]; then
                    ZIPALIGN_PATH="$zipalign"
                    APKSIGNER_PATH="$apksigner"
                    echo -e "${GREEN}✅ 找到Android SDK工具:${NC}"
                    echo "   zipalign: $ZIPALIGN_PATH"
                    echo "   apksigner: $APKSIGNER_PATH"
                    return 0
                fi
            fi
        fi
    done
    
    echo -e "${RED}❌ 未找到Android SDK工具，请设置ANDROID_HOME环境变量${NC}"
    return 1
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -a|--apk)
                APK_PATH="$2"
                shift 2
                ;;
            -k|--keystore)
                KEYSTORE_PATH="$2"
                shift 2
                ;;
            -s|--storepass)
                STOREPASS="$2"
                shift 2
                ;;
            -p|--keypass)
                KEYPASS="$2"
                shift 2
                ;;
            -n|--alias)
                ALIAS="$2"
                shift 2
                ;;
            -o|--output)
                OUTPUT_PATH="$2"
                shift 2
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                echo -e "${RED}❌ 未知参数: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done
}

# 交互式输入缺失的参数
interactive_input() {
    if [ -z "$APK_PATH" ]; then
        read -p "请输入APK文件路径: " APK_PATH
    fi
    
    if [ -z "$KEYSTORE_PATH" ]; then
        read -p "请输入Keystore文件路径: " KEYSTORE_PATH
    fi
    
    if [ -z "$STOREPASS" ]; then
        read -sp "请输入Keystore密码: " STOREPASS
        echo
    fi
    
    if [ -z "$KEYPASS" ]; then
        read -sp "请输入Key密码（直接回车使用Keystore密码）: " KEYPASS
        echo
        if [ -z "$KEYPASS" ]; then
            KEYPASS="$STOREPASS"
        fi
    fi
    
    if [ -z "$ALIAS" ]; then
        read -p "请输入Key别名: " ALIAS
    fi
}

# 验证参数
validate_args() {
    local errors=0
    
    if [ -z "$APK_PATH" ]; then
        echo -e "${RED}❌ 错误: 未指定APK文件路径${NC}"
        errors=$((errors + 1))
    elif [ ! -f "$APK_PATH" ]; then
        echo -e "${RED}❌ 错误: APK文件不存在: $APK_PATH${NC}"
        errors=$((errors + 1))
    fi
    
    if [ -z "$KEYSTORE_PATH" ]; then
        echo -e "${RED}❌ 错误: 未指定Keystore文件路径${NC}"
        errors=$((errors + 1))
    elif [ ! -f "$KEYSTORE_PATH" ]; then
        echo -e "${RED}❌ 错误: Keystore文件不存在: $KEYSTORE_PATH${NC}"
        errors=$((errors + 1))
    fi
    
    if [ -z "$STOREPASS" ]; then
        echo -e "${RED}❌ 错误: 未指定Keystore密码${NC}"
        errors=$((errors + 1))
    fi
    
    if [ -z "$ALIAS" ]; then
        echo -e "${RED}❌ 错误: 未指定Key别名${NC}"
        errors=$((errors + 1))
    fi
    
    if [ $errors -gt 0 ]; then
        echo ""
        echo -e "${YELLOW}提示: 使用 -h 或 --help 查看帮助信息${NC}"
        exit 1
    fi
    
    # 如果没有指定keypass，使用storepass
    if [ -z "$KEYPASS" ]; then
        KEYPASS="$STOREPASS"
    fi
    
    # 如果没有指定输出路径，生成默认路径
    if [ -z "$OUTPUT_PATH" ]; then
        local dir=$(dirname "$APK_PATH")
        local name=$(basename "$APK_PATH" .apk)
        OUTPUT_PATH="$dir/${name}-signed.apk"
    fi
}

# 对齐APK
align_apk() {
    local input_apk="$1"
    local aligned_apk="$2"
    
    echo -e "${BLUE}📦 步骤1: 对齐APK文件...${NC}"
    
    if [ -f "$aligned_apk" ]; then
        rm -f "$aligned_apk"
    fi
    
    "$ZIPALIGN_PATH" -v -p 4 "$input_apk" "$aligned_apk" > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ APK对齐完成${NC}"
        return 0
    else
        echo -e "${RED}❌ APK对齐失败${NC}"
        return 1
    fi
}

# 签名APK
sign_apk() {
    local aligned_apk="$1"
    local signed_apk="$2"
    
    echo -e "${BLUE}🔐 步骤2: 签名APK文件...${NC}"
    
    if [ -f "$signed_apk" ]; then
        rm -f "$signed_apk"
    fi
    
    "$APKSIGNER_PATH" sign \
        --ks "$KEYSTORE_PATH" \
        --ks-pass "pass:$STOREPASS" \
        --key-pass "pass:$KEYPASS" \
        --ks-key-alias "$ALIAS" \
        --out "$signed_apk" \
        "$aligned_apk" > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ APK签名完成${NC}"
        return 0
    else
        echo -e "${RED}❌ APK签名失败${NC}"
        return 1
    fi
}

# 验证签名
verify_signature() {
    local signed_apk="$1"
    
    echo -e "${BLUE}🔍 步骤3: 验证签名...${NC}"
    echo ""
    
    # 验证签名
    local verify_output=$("$APKSIGNER_PATH" verify --verbose "$signed_apk" 2>&1)
    local verify_status=$?
    
    if [ $verify_status -eq 0 ]; then
        echo -e "${GREEN}✅ 签名验证通过${NC}"
        echo ""
        
        # 显示证书信息
        echo -e "${BLUE}📜 证书信息:${NC}"
        "$APKSIGNER_PATH" verify --print-certs "$signed_apk" 2>&1 | grep -E "(Signer|certificate|DN|SHA)" | head -10
        echo ""
        
        return 0
    else
        echo -e "${RED}❌ 签名验证失败${NC}"
        echo "$verify_output"
        return 1
    fi
}

# 清理临时文件
cleanup() {
    local aligned_apk="$1"
    
    if [ -f "$aligned_apk" ]; then
        rm -f "$aligned_apk"
        echo -e "${GREEN}✅ 临时文件已清理${NC}"
    fi
}

# 主函数
main() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}           APK重签名工具${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    
    # 解析命令行参数
    parse_args "$@"
    
    # 如果没有提供必需参数，尝试交互式输入
    if [ -z "$APK_PATH" ] || [ -z "$KEYSTORE_PATH" ] || [ -z "$STOREPASS" ] || [ -z "$ALIAS" ]; then
        echo -e "${YELLOW}⚠️  部分参数缺失，进入交互式输入模式${NC}"
        echo ""
        interactive_input
        echo ""
    fi
    
    # 验证参数
    validate_args
    
    # 查找Android SDK工具
    if ! find_android_tools; then
        exit 1
    fi
    echo ""
    
    # 显示配置信息
    echo -e "${BLUE}📋 配置信息:${NC}"
    echo "   APK文件: $APK_PATH"
    echo "   Keystore: $KEYSTORE_PATH"
    echo "   Key别名: $ALIAS"
    echo "   输出文件: $OUTPUT_PATH"
    echo ""
    
    # 生成临时对齐文件路径
    local aligned_apk="${OUTPUT_PATH%.apk}-aligned.apk"
    
    # 执行对齐
    if ! align_apk "$APK_PATH" "$aligned_apk"; then
        exit 1
    fi
    echo ""
    
    # 执行签名
    if ! sign_apk "$aligned_apk" "$OUTPUT_PATH"; then
        cleanup "$aligned_apk"
        exit 1
    fi
    echo ""
    
    # 验证签名
    if ! verify_signature "$OUTPUT_PATH"; then
        cleanup "$aligned_apk"
        exit 1
    fi
    
    # 清理临时文件
    cleanup "$aligned_apk"
    
    # 显示最终结果
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ 重签名完成！${NC}"
    echo ""
    echo -e "${BLUE}📦 签名后的APK文件:${NC}"
    ls -lh "$OUTPUT_PATH" | awk '{print "   " $9 " (" $5 ")"}'
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
}

# 运行主函数
main "$@"

