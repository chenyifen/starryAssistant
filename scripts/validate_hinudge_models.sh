#!/bin/bash

# 验证HiNudge模型文件
echo "🔍 验证HiNudge模型文件..."

EXTERNAL_MODEL_DIR="/storage/emulated/0/Dicio/models/openWakeWord"
LOCAL_MODEL_DIR="/data/user/0/org.stypox.dicio.master/files/hiNudgeOpenWakeWord"

echo ""
echo "📱 检查外部存储模型文件..."
echo "路径: $EXTERNAL_MODEL_DIR"

# 检查外部存储文件
for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
    echo ""
    echo "📄 检查文件: $file"
    
    if adb shell "test -f $EXTERNAL_MODEL_DIR/$file" 2>/dev/null; then
        # 文件存在，获取详细信息
        size=$(adb shell "stat -c%s $EXTERNAL_MODEL_DIR/$file" 2>/dev/null)
        echo "  ✅ 文件存在"
        echo "  📏 大小: ${size} bytes"
        
        # 检查文件大小是否合理
        if [ "$size" -lt 1000 ]; then
            echo "  ❌ 文件太小，可能损坏"
        elif [ "$size" -gt 100000000 ]; then
            echo "  ⚠️  文件很大 (>100MB)"
        else
            echo "  ✅ 文件大小合理"
        fi
        
        # 检查文件头（TensorFlow Lite文件应该以特定字节开头）
        echo "  🔍 检查文件头..."
        header=$(adb shell "xxd -l 8 -p $EXTERNAL_MODEL_DIR/$file" 2>/dev/null | tr -d '\n\r')
        echo "  📋 文件头: $header"
        
        # TensorFlow Lite文件通常以 "TFL3" 开头，对应十六进制 54464C33
        if [[ "$header" == 54464c33* ]] || [[ "$header" == 54464C33* ]]; then
            echo "  ✅ TensorFlow Lite文件头正确"
        else
            echo "  ❌ 不是有效的TensorFlow Lite文件头"
            echo "  💡 期望: 54464C33 (TFL3)"
            echo "  💡 实际: $header"
        fi
        
        # 检查文件是否可读
        if adb shell "test -r $EXTERNAL_MODEL_DIR/$file" 2>/dev/null; then
            echo "  ✅ 文件可读"
        else
            echo "  ❌ 文件不可读"
        fi
        
    else
        echo "  ❌ 文件不存在"
    fi
done

echo ""
echo "📱 检查内部存储模型文件..."
echo "路径: $LOCAL_MODEL_DIR"

# 检查内部存储文件
for file in "melspectrogram.tflite" "embedding.tflite" "wake.tflite"; do
    echo ""
    echo "📄 检查内部文件: $file"
    
    if adb shell "test -f $LOCAL_MODEL_DIR/$file" 2>/dev/null; then
        size=$(adb shell "stat -c%s $LOCAL_MODEL_DIR/$file" 2>/dev/null)
        echo "  ✅ 文件存在，大小: ${size} bytes"
        
        # 检查文件头
        header=$(adb shell "xxd -l 8 -p $LOCAL_MODEL_DIR/$file" 2>/dev/null | tr -d '\n\r')
        echo "  📋 文件头: $header"
        
        if [[ "$header" == 54464c33* ]] || [[ "$header" == 54464C33* ]]; then
            echo "  ✅ TensorFlow Lite文件头正确"
        else
            echo "  ❌ 不是有效的TensorFlow Lite文件头"
        fi
    else
        echo "  ❌ 文件不存在"
    fi
done

echo ""
echo "🔧 推荐的修复步骤："
echo "1. 确保外部存储中的模型文件是有效的TensorFlow Lite文件"
echo "2. 检查文件大小是否合理（通常几MB到几十MB）"
echo "3. 如果文件损坏，重新下载或生成模型文件"
echo "4. 删除内部存储的损坏文件，让应用重新复制"

echo ""
echo "🗑️  清理损坏的内部模型文件："
echo "adb shell rm -rf $LOCAL_MODEL_DIR/*"

echo ""
echo "🏁 验证完成"
