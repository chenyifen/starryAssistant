#!/bin/bash

echo "🚀 Dicio Android 构建、安装和启动脚本"
echo "========================================"

# 设置Java 17环境
echo "☕ 1. 设置Java 17环境..."
export JAVA_HOME="/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 验证Java版本
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "✅ Java版本: $java_version"

if [[ ! "$java_version" =~ ^17\. ]]; then
    echo "⚠️  警告: 检测到的Java版本不是17，可能会导致构建问题"
    echo "请确保已安装Java 17并正确设置JAVA_HOME路径"
fi

# 检查设备连接
echo ""
echo "📱 2. 检查Android设备连接..."
device_count=$(adb devices | grep -v "List of devices" | grep -c "device")
if [ $device_count -eq 0 ]; then
    echo "❌ 未检测到连接的Android设备"
    echo "请确保："
    echo "- 设备已连接并启用USB调试"
    echo "- 运行 'adb devices' 确认设备可见"
    exit 1
fi
echo "✅ 检测到 $device_count 个设备"

# 构建应用
echo ""
echo "🔨 3. 构建noModels变体..."
./gradlew assembleNoModelsDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 检查APK文件是否存在
apk_path="app/build/outputs/apk/noModels/debug/app-noModels-debug.apk"
if [ ! -f "$apk_path" ]; then
    echo "❌ APK文件不存在: $apk_path"
    exit 1
fi

# 获取APK信息
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK大小: $apk_size"

# 安装应用
echo ""
echo "📲 4. 安装应用到设备..."
adb install -r "$apk_path"

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用
echo ""
echo "🎯 5. 启动应用..."
package_name="org.stypox.dicio.master"
activity_name="org.stypox.dicio.MainActivity"

adb shell am start -n "$package_name/$activity_name"

if [ $? -ne 0 ]; then
    echo "❌ 启动应用失败"
    exit 1
fi

echo "✅ 应用已启动"

# 获取应用进程ID
echo ""
echo "🔍 获取应用进程信息..."
sleep 2  # 等待应用完全启动

app_pid=$(adb shell ps | grep "$package_name" | awk '{print $2}' | head -1)
if [ -n "$app_pid" ]; then
    echo "📱 应用进程ID: $app_pid"
    echo "💡 可以使用以下命令监控特定进程:"
    echo "   adb shell top -p $app_pid"
    echo "   adb shell dumpsys meminfo $app_pid"
else
    echo "⚠️ 未能获取应用进程ID，应用可能未完全启动"
fi

# 显示日志监控命令
echo ""
echo "📊 6. 监控应用日志..."
echo "运行以下命令查看实时日志："
echo "adb logcat | grep -E 'SherpaOnnx|🎯|🎉|STT|WakeService'"

echo ""
echo "🎤 7. 测试说明..."
echo "现在可以测试以下功能："
echo "- 进入Settings检查Wake word recognition method设置"
echo "- 测试韩语唤醒词: 하이넛지"
echo "- 测试中文唤醒词: 小艺小艺"
echo "- 测试英文唤醒词: hey dicio"

echo ""
echo "⚠️  注意事项 (noModels变体):"
echo "- 此版本不包含预置模型文件"
echo "- 需要手动推送SherpaOnnx KWS模型到设备"
echo "- 模型路径: /storage/emulated/0/Dicio/models/sherpa_onnx_kws/"

echo ""
echo "🔧 8. 推送SherpaOnnx模型文件..."
echo "为noModels变体推送必要的模型文件..."

# 创建外部存储目录
adb shell mkdir -p /storage/emulated/0/Dicio/models/sherpa_onnx_kws/

# 检查设备上是否已有模型文件
echo "🔍 检查设备上的模型文件..."
device_model_count=$(adb shell ls /storage/emulated/0/Dicio/models/sherpa_onnx_kws/ 2>/dev/null | wc -l)

if [ "$device_model_count" -gt 3 ]; then
    echo "✅ 设备上已存在模型文件 ($device_model_count 个文件)，跳过推送"
    echo "📄 现有文件列表:"
    adb shell ls -la /storage/emulated/0/Dicio/models/sherpa_onnx_kws/ | head -10
    echo "💡 如需重新推送模型文件，请先删除设备上的模型目录："
    echo "   adb shell rm -rf /storage/emulated/0/Dicio/models/sherpa_onnx_kws/*"
else
    echo "📦 设备上模型文件不完整或不存在，开始推送..."
    
    # 检查本地模型文件是否存在
    if [ -d "app/src/withModels/assets/models/sherpa_onnx_kws" ]; then
        echo "📦 推送Dicio项目模型文件到设备..."
        adb push app/src/withModels/assets/models/sherpa_onnx_kws/* /storage/emulated/0/Dicio/models/sherpa_onnx_kws/
    elif [ -d "/Users/user/AndroidStudioProjects/HandsFree/app/src/withModels/assets/models/kws" ]; then
        echo "📦 使用HandsFree项目的兼容模型文件..."
        adb push /Users/user/AndroidStudioProjects/HandsFree/app/src/withModels/assets/models/kws/* /storage/emulated/0/Dicio/models/sherpa_onnx_kws/
    else
        echo "⚠️ 警告: 未找到可用的模型文件"
        echo "请确保以下路径之一存在模型文件:"
        echo "1. app/src/withModels/assets/models/sherpa_onnx_kws (Dicio原生)"
        echo "2. /Users/user/AndroidStudioProjects/HandsFree/app/src/withModels/assets/models/kws (HandsFree兼容)"
    fi
    
    if [ $? -eq 0 ]; then
        echo "✅ 模型文件推送成功"
        
        # 验证文件是否推送成功
        echo "🔍 验证推送的文件:"
        adb shell ls -la /storage/emulated/0/Dicio/models/sherpa_onnx_kws/
    else
        echo "❌ 模型文件推送失败"
    fi
fi

echo ""
echo "🎉 脚本执行完成！"
