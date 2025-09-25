#!/bin/bash

echo "🔍 验证HiNudge ONNX模型文件..."

EXTERNAL_MODEL_DIR="/storage/emulated/0/Dicio/models/openWakeWord"
INTERNAL_MODEL_DIR="/data/user/0/org.stypox.dicio.master/files/hiNudgeOpenWakeWord"
MODEL_FILES=("melspectrogram.onnx" "embedding_model.onnx" "hey_nugget_new.onnx")

# Function to validate a single ONNX model file
validate_onnx_file() {
  local file_path=$1
  local file_name=$(basename "$file_path")

  echo "📄 检查ONNX文件: $file_name"
  if [ ! -f "$file_path" ]; then
    echo "  ❌ 文件不存在"
    return 1
  fi
  echo "  ✅ 文件存在"

  local file_size=$(stat -c %s "$file_path" 2>/dev/null || stat -f %z "$file_path" 2>/dev/null)
  echo "  📏 大小: $file_size bytes"
  if [ "$file_size" -lt 10000 ]; then # ONNX models are usually larger than 10KB
    echo "  ❌ 文件大小过小，可能损坏"
    return 1
  fi
  echo "  ✅ 文件大小合理"

  echo "  🔍 检查ONNX文件头..."
  # ONNX files start with specific bytes (Protocol Buffers format)
  local file_header=$(head -c 8 "$file_path" | xxd -p -c 8 2>/dev/null)
  echo "  📋 文件头: $file_header"
  
  # Check for common ONNX signatures (Protocol Buffers)
  if [[ "$file_header" =~ ^08[0-9a-f]{2}12 ]] || [[ "$file_header" =~ ^0a[0-9a-f]{2} ]]; then
    echo "  ✅ ONNX文件头格式正确"
  else
    echo "  ⚠️  文件头格式不是标准ONNX格式，但可能仍然有效"
  fi

  if [ ! -r "$file_path" ]; then
    echo "  ❌ 文件不可读"
    return 1
  fi
  echo "  ✅ 文件可读"
  return 0
}

# Function to check if adb is available and device is connected
check_adb() {
  if ! command -v adb &> /dev/null; then
    echo "❌ ADB未找到，请确保Android SDK已安装并在PATH中"
    return 1
  fi
  
  if ! adb devices | grep -q "device$"; then
    echo "❌ 没有连接的Android设备，请连接设备并启用USB调试"
    return 1
  fi
  
  echo "✅ ADB可用，设备已连接"
  return 0
}

echo ""
echo "📱 检查外部存储模型文件..."
echo "路径: $EXTERNAL_MODEL_DIR"
EXTERNAL_VALID=true

if check_adb; then
  # Check if external directory exists
  if adb shell "[ -d '$EXTERNAL_MODEL_DIR' ]"; then
    echo "✅ 外部存储目录存在"
    
    for model_file in "${MODEL_FILES[@]}"; do
      if adb shell "[ -f '$EXTERNAL_MODEL_DIR/$model_file' ]"; then
        # Get file size
        file_size=$(adb shell "stat -c %s '$EXTERNAL_MODEL_DIR/$model_file'" 2>/dev/null || adb shell "wc -c < '$EXTERNAL_MODEL_DIR/$model_file'" 2>/dev/null)
        echo "📄 $model_file: ${file_size} bytes"
        
        if [ "$file_size" -lt 10000 ]; then
          echo "  ❌ 文件大小过小"
          EXTERNAL_VALID=false
        else
          echo "  ✅ 文件大小合理"
        fi
      else
        echo "❌ $model_file 不存在"
        EXTERNAL_VALID=false
      fi
    done
  else
    echo "❌ 外部存储目录不存在: $EXTERNAL_MODEL_DIR"
    EXTERNAL_VALID=false
  fi
else
  echo "⚠️  无法通过ADB检查外部存储，请手动验证"
  EXTERNAL_VALID=false
fi

echo ""
echo "📱 检查内部存储模型文件..."
echo "路径: $INTERNAL_MODEL_DIR"
INTERNAL_VALID=true

if check_adb; then
  # Check if internal directory exists
  if adb shell "[ -d '$INTERNAL_MODEL_DIR' ]"; then
    echo "✅ 内部存储目录存在"
    
    for model_file in "${MODEL_FILES[@]}"; do
      if adb shell "[ -f '$INTERNAL_MODEL_DIR/$model_file' ]"; then
        # Get file size
        file_size=$(adb shell "stat -c %s '$INTERNAL_MODEL_DIR/$model_file'" 2>/dev/null || adb shell "wc -c < '$INTERNAL_MODEL_DIR/$model_file'" 2>/dev/null)
        echo "📄 $model_file: ${file_size} bytes"
        
        if [ "$file_size" -lt 10000 ]; then
          echo "  ❌ 文件大小过小"
          INTERNAL_VALID=false
        else
          echo "  ✅ 文件大小合理"
        fi
      else
        echo "❌ $model_file 不存在"
        INTERNAL_VALID=false
      fi
    done
  else
    echo "❌ 内部存储目录不存在: $INTERNAL_MODEL_DIR"
    INTERNAL_VALID=false
  fi
else
  echo "⚠️  无法通过ADB检查内部存储，请手动验证"
  INTERNAL_VALID=false
fi

echo ""
echo "📋 模型文件要求："
echo "1. melspectrogram.onnx - Mel频谱图生成模型"
echo "2. embedding_model.onnx - 特征嵌入模型" 
echo "3. hey_nugget_new.onnx - 韩语唤醒词检测模型"
echo ""
echo "🔧 推荐的修复步骤："
echo "1. 确保外部存储中的模型文件是有效的ONNX文件"
echo "2. 检查文件大小是否合理（通常几MB到几十MB）"
echo "3. 如果文件损坏，重新下载或生成模型文件"
echo "4. 删除内部存储的损坏文件，让应用重新复制"
echo ""
echo "🗑️  清理损坏的内部模型文件："
echo "adb shell rm -rf $INTERNAL_MODEL_DIR/*"
echo ""
echo "📱 推送模型文件到外部存储："
echo "adb shell mkdir -p $EXTERNAL_MODEL_DIR"
echo "adb push melspectrogram.onnx $EXTERNAL_MODEL_DIR/"
echo "adb push embedding_model.onnx $EXTERNAL_MODEL_DIR/"
echo "adb push hey_nugget_new.onnx $EXTERNAL_MODEL_DIR/"
echo ""

# Summary
if [ "$EXTERNAL_VALID" = true ] && [ "$INTERNAL_VALID" = true ]; then
  echo "🎉 所有HiNudge ONNX模型文件验证通过！"
  exit 0
elif [ "$EXTERNAL_VALID" = true ]; then
  echo "✅ 外部存储模型文件正常，内部存储需要修复"
  exit 1
else
  echo "❌ 需要修复模型文件"
  exit 1
fi

echo "🏁 验证完成"
