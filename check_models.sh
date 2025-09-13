#!/bin/bash

# 模型文件检查脚本
echo "=== Dicio 模型文件完整性检查 ==="
echo

# 检查assets目录是否存在
ASSETS_DIR="app/src/main/assets/models"
if [ ! -d "$ASSETS_DIR" ]; then
    echo "❌ Assets模型目录不存在: $ASSETS_DIR"
    exit 1
fi

echo "✅ Assets模型目录存在: $ASSETS_DIR"
echo

# 检查OpenWakeWord模型
echo "--- OpenWakeWord模型检查 ---"
OWW_DIR="$ASSETS_DIR/openWakeWord"

if [ ! -d "$OWW_DIR" ]; then
    echo "❌ OpenWakeWord目录不存在"
else
    echo "✅ OpenWakeWord目录存在"
    
    # 检查必需的模型文件
    REQUIRED_OWW_FILES=("melspectrogram.tflite" "embedding.tflite" "wake.tflite")
    
    for file in "${REQUIRED_OWW_FILES[@]}"; do
        if [ -f "$OWW_DIR/$file" ]; then
            size=$(ls -lh "$OWW_DIR/$file" | awk '{print $5}')
            echo "  ✅ $file ($size)"
        else
            echo "  ❌ $file (缺失)"
        fi
    done
fi
echo

# 检查Vosk模型
echo "--- Vosk模型检查 ---"
VOSK_DIR="$ASSETS_DIR/vosk"

if [ ! -d "$VOSK_DIR" ]; then
    echo "❌ Vosk目录不存在"
else
    echo "✅ Vosk目录存在"
    
    # 检查支持的语言
    SUPPORTED_LANGS=("cn" "en" "ko")
    
    for lang in "${SUPPORTED_LANGS[@]}"; do
        lang_dir="$VOSK_DIR/$lang"
        if [ -d "$lang_dir" ]; then
            echo "  ✅ $lang 语言模型存在"
            
            # 检查必需的子目录
            REQUIRED_DIRS=("am" "conf" "graph" "ivector")
            for subdir in "${REQUIRED_DIRS[@]}"; do
                if [ -d "$lang_dir/$subdir" ]; then
                    echo "    ✅ $subdir/"
                else
                    echo "    ❌ $subdir/ (缺失)"
                fi
            done
            
            # 检查关键文件
            if [ -f "$lang_dir/am/final.mdl" ]; then
                size=$(ls -lh "$lang_dir/am/final.mdl" | awk '{print $5}')
                echo "    ✅ am/final.mdl ($size)"
            else
                echo "    ❌ am/final.mdl (缺失)"
            fi
            
            if [ -d "$lang_dir/ivector" ]; then
                ivector_files=$(ls "$lang_dir/ivector" | wc -l)
                echo "    ✅ ivector/ ($ivector_files 个文件)"
            fi
            
        else
            echo "  ❌ $lang 语言模型不存在"
        fi
        echo
    done
fi

# 计算总大小
echo "--- 存储空间统计 ---"
if [ -d "$ASSETS_DIR" ]; then
    total_size=$(du -sh "$ASSETS_DIR" | awk '{print $1}')
    echo "总模型大小: $total_size"
    
    if [ -d "$OWW_DIR" ]; then
        oww_size=$(du -sh "$OWW_DIR" | awk '{print $1}')
        echo "OpenWakeWord模型: $oww_size"
    fi
    
    if [ -d "$VOSK_DIR" ]; then
        vosk_size=$(du -sh "$VOSK_DIR" | awk '{print $1}')
        echo "Vosk模型总计: $vosk_size"
        
        for lang in "${SUPPORTED_LANGS[@]}"; do
            if [ -d "$VOSK_DIR/$lang" ]; then
                lang_size=$(du -sh "$VOSK_DIR/$lang" | awk '{print $1}')
                echo "  $lang: $lang_size"
            fi
        done
    fi
fi
echo

# 检查代码兼容性
echo "--- 代码兼容性检查 ---"

# 检查OpenWakeWordDevice中期望的文件名
echo "OpenWakeWord文件名兼容性:"
if grep -q "embedding.tflite" app/src/main/kotlin/org/stypox/dicio/io/wake/oww/OpenWakeWordDevice.kt; then
    echo "  ✅ embedding.tflite - 代码期望匹配"
else
    echo "  ❌ embedding.tflite - 代码期望不匹配"
fi

if grep -q "melspectrogram.tflite" app/src/main/kotlin/org/stypox/dicio/io/wake/oww/OpenWakeWordDevice.kt; then
    echo "  ✅ melspectrogram.tflite - 代码期望匹配"
else
    echo "  ❌ melspectrogram.tflite - 代码期望不匹配"
fi

if grep -q "wake.tflite" app/src/main/kotlin/org/stypox/dicio/io/wake/oww/OpenWakeWordDevice.kt; then
    echo "  ✅ wake.tflite - 代码期望匹配"
else
    echo "  ❌ wake.tflite - 代码期望不匹配"
fi

# 检查Vosk语言代码
echo
echo "Vosk语言代码兼容性:"
if grep -q '"cn"' app/src/main/kotlin/org/stypox/dicio/io/input/vosk/VoskInputDevice.kt; then
    echo "  ✅ cn - 中文语言代码存在"
else
    echo "  ❌ cn - 中文语言代码不存在"
fi

if grep -q '"en"' app/src/main/kotlin/org/stypox/dicio/io/input/vosk/VoskInputDevice.kt; then
    echo "  ✅ en - 英语语言代码存在"
else
    echo "  ❌ en - 英语语言代码不存在"
fi

if grep -q '"ko"' app/src/main/kotlin/org/stypox/dicio/io/input/vosk/VoskInputDevice.kt; then
    echo "  ✅ ko - 韩语语言代码存在"
else
    echo "  ❌ ko - 韩语语言代码不存在"
fi

echo
echo "=== 检查完成 ==="
