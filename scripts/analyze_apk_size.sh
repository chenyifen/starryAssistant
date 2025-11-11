#!/bin/bash
# APK大小分析脚本

APK_FILE="${1:-app/build/outputs/apk/release/app-release.apk}"

if [ ! -f "$APK_FILE" ]; then
    echo "❌ APK文件不存在: $APK_FILE"
    echo "请先构建Release版本: ./gradlew assembleRelease"
    exit 1
fi

echo "📦 APK大小分析报告"
echo "=================="
echo "APK文件: $APK_FILE"
echo "文件大小: $(ls -lh "$APK_FILE" | awk '{print $5}')"
echo ""

# 创建临时目录
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# 解压APK
echo "📂 解压APK..."
unzip -q "$APK_FILE" -d "$TEMP_DIR"

echo ""
echo "📊 文件大小组成分析"
echo "=================="

# 1. Native库分析
echo ""
echo "1️⃣ Native库 (lib/)"
echo "-------------------"
if [ -d "$TEMP_DIR/lib" ]; then
    for arch in "$TEMP_DIR/lib"/*; do
        if [ -d "$arch" ]; then
            arch_name=$(basename "$arch")
            arch_size=$(du -sh "$arch" | awk '{print $1}')
            arch_size_bytes=$(du -sb "$arch" | awk '{print $1}')
            echo "  📁 $arch_name: $arch_size"
            
            # 列出主要库文件
            echo "     主要库文件:"
            find "$arch" -name "*.so" -exec ls -lh {} \; | awk '{printf "       %s: %s\n", $9, $5}' | sed "s|$TEMP_DIR/lib/$arch_name/||"
        fi
    done
    lib_total=$(du -sb "$TEMP_DIR/lib" 2>/dev/null | awk '{print $1}')
    lib_total_mb=$(echo "scale=2; $lib_total / 1024 / 1024" | bc)
    echo "  📊 Native库总计: ${lib_total_mb} MB"
else
    echo "  ⚠️  未找到lib目录"
fi

# 2. Assets分析
echo ""
echo "2️⃣ Assets资源 (assets/)"
echo "----------------------"
if [ -d "$TEMP_DIR/assets" ]; then
    assets_total=0
    for item in "$TEMP_DIR/assets"/*; do
        if [ -f "$item" ]; then
            item_name=$(basename "$item")
            item_size=$(ls -lh "$item" | awk '{print $5}')
            item_size_bytes=$(stat -f%z "$item" 2>/dev/null || stat -c%s "$item" 2>/dev/null)
            assets_total=$((assets_total + item_size_bytes))
            echo "  📄 $item_name: $item_size"
        elif [ -d "$item" ]; then
            dir_name=$(basename "$item")
            dir_size=$(du -sh "$item" | awk '{print $1}')
            dir_size_bytes=$(du -sb "$item" | awk '{print $1}')
            assets_total=$((assets_total + dir_size_bytes))
            echo "  📁 $dir_name/: $dir_size"
            
            # 如果是模型目录，列出主要文件
            if [[ "$dir_name" == *"model"* ]] || [[ "$dir_name" == *"tts"* ]] || [[ "$dir_name" == *"asr"* ]] || [[ "$dir_name" == *"onnx"* ]]; then
                echo "     主要文件:"
                find "$item" -type f -exec ls -lh {} \; | awk '{printf "       %s: %s\n", $9, $5}' | sed "s|$TEMP_DIR/assets/$dir_name/||" | head -10
            fi
        fi
    done
    assets_total_mb=$(echo "scale=2; $assets_total / 1024 / 1024" | bc)
    echo "  📊 Assets总计: ${assets_total_mb} MB"
else
    echo "  ⚠️  未找到assets目录"
fi

# 3. DEX文件分析
echo ""
echo "3️⃣ DEX文件 (classes*.dex)"
echo "------------------------"
dex_total=0
for dex in "$TEMP_DIR"/*.dex; do
    if [ -f "$dex" ]; then
        dex_name=$(basename "$dex")
        dex_size=$(ls -lh "$dex" | awk '{print $5}')
        dex_size_bytes=$(stat -f%z "$dex" 2>/dev/null || stat -c%s "$dex" 2>/dev/null)
        dex_total=$((dex_total + dex_size_bytes))
        echo "  📄 $dex_name: $dex_size"
    fi
done
if [ $dex_total -gt 0 ]; then
    dex_total_mb=$(echo "scale=2; $dex_total / 1024 / 1024" | bc)
    echo "  📊 DEX文件总计: ${dex_total_mb} MB"
else
    echo "  ⚠️  未找到DEX文件"
fi

# 4. Resources分析
echo ""
echo "4️⃣ Resources (resources.arsc, res/)"
echo "-----------------------------------"
res_total=0
if [ -f "$TEMP_DIR/resources.arsc" ]; then
    res_size=$(ls -lh "$TEMP_DIR/resources.arsc" | awk '{print $5}')
    res_size_bytes=$(stat -f%z "$TEMP_DIR/resources.arsc" 2>/dev/null || stat -c%s "$TEMP_DIR/resources.arsc" 2>/dev/null)
    res_total=$((res_total + res_size_bytes))
    echo "  📄 resources.arsc: $res_size"
fi
if [ -d "$TEMP_DIR/res" ]; then
    res_dir_size=$(du -sh "$TEMP_DIR/res" | awk '{print $1}')
    res_dir_size_bytes=$(du -sb "$TEMP_DIR/res" | awk '{print $1}')
    res_total=$((res_total + res_dir_size_bytes))
    echo "  📁 res/: $res_dir_size"
fi
if [ $res_total -gt 0 ]; then
    res_total_mb=$(echo "scale=2; $res_total / 1024 / 1024" | bc)
    echo "  📊 Resources总计: ${res_total_mb} MB"
fi

# 5. 其他文件
echo ""
echo "5️⃣ 其他文件"
echo "----------"
other_total=0
for file in "$TEMP_DIR"/*; do
    if [ -f "$file" ]; then
        file_name=$(basename "$file")
        if [[ "$file_name" != *.dex ]] && [[ "$file_name" != resources.arsc ]]; then
            file_size=$(ls -lh "$file" | awk '{print $5}')
            file_size_bytes=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)
            other_total=$((other_total + file_size_bytes))
            echo "  📄 $file_name: $file_size"
        fi
    fi
done
if [ $other_total -gt 0 ]; then
    other_total_mb=$(echo "scale=2; $other_total / 1024 / 1024" | bc)
    echo "  📊 其他文件总计: ${other_total_mb} MB"
fi

# 6. 总结
echo ""
echo "📈 大小总结"
echo "=========="
apk_size_bytes=$(stat -f%z "$APK_FILE" 2>/dev/null || stat -c%s "$APK_FILE" 2>/dev/null)
apk_size_mb=$(echo "scale=2; $apk_size_bytes / 1024 / 1024" | bc)

echo "APK总大小: ${apk_size_mb} MB"
echo ""
echo "各组件占比:"
if [ $lib_total -gt 0 ]; then
    lib_percent=$(echo "scale=1; $lib_total * 100 / $apk_size_bytes" | bc)
    echo "  Native库: ${lib_total_mb} MB (${lib_percent}%)"
fi
if [ $assets_total -gt 0 ]; then
    assets_percent=$(echo "scale=1; $assets_total * 100 / $apk_size_bytes" | bc)
    echo "  Assets: ${assets_total_mb} MB (${assets_percent}%)"
fi
if [ $dex_total -gt 0 ]; then
    dex_percent=$(echo "scale=1; $dex_total * 100 / $apk_size_bytes" | bc)
    echo "  DEX文件: ${dex_total_mb} MB (${dex_percent}%)"
fi
if [ $res_total -gt 0 ]; then
    res_percent=$(echo "scale=1; $res_total * 100 / $apk_size_bytes" | bc)
    echo "  Resources: ${res_total_mb} MB (${res_percent}%)"
fi
if [ $other_total -gt 0 ]; then
    other_percent=$(echo "scale=1; $other_total * 100 / $apk_size_bytes" | bc)
    echo "  其他: ${other_total_mb} MB (${other_percent}%)"
fi

echo ""
echo "✅ 分析完成"

