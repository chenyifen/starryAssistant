#!/bin/bash

echo "🚀 Dicio Android withModels变体构建、安装和启动脚本"
echo "================================================="

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

# 检查模型文件是否存在
echo ""
echo "📦 3. 检查withModels变体模型文件..."
models_path="app/src/withModels/assets/models/sherpa_onnx_kws"
handsfree_models_path="/Users/user/AndroidStudioProjects/HandsFree/app/src/withModels/assets/models/kws"

if [ ! -d "$models_path" ]; then
    if [ -d "$handsfree_models_path" ]; then
        echo "⚠️ Dicio模型文件不存在，但发现HandsFree兼容模型"
        echo "📦 将使用HandsFree项目的兼容模型文件"
        
        # 创建目录并复制HandsFree的模型文件
        mkdir -p "$models_path"
        cp "$handsfree_models_path"/* "$models_path/"
        echo "✅ 已复制HandsFree兼容模型文件到withModels变体"
    else
        echo "❌ 未找到可用的模型文件"
        echo "请确保以下路径之一存在模型文件:"
        echo "1. $models_path (Dicio原生)"
        echo "2. $handsfree_models_path (HandsFree兼容)"
        exit 1
    fi
fi

# 检查必要的模型文件
required_files=(
    "encoder-epoch-12-avg-2-chunk-16-left-64.onnx"
    "decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
    "joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
    "keywords.txt"
    "tokens.txt"
)

missing_files=()
for file in "${required_files[@]}"; do
    if [ ! -f "$models_path/$file" ]; then
        missing_files+=("$file")
    fi
done

if [ ${#missing_files[@]} -gt 0 ]; then
    echo "❌ 缺少必要的模型文件:"
    for file in "${missing_files[@]}"; do
        echo "  - $file"
    done
    exit 1
fi

echo "✅ 模型文件检查通过"

# 显示模型文件信息
echo "📄 模型文件信息:"
for file in "${required_files[@]}"; do
    if [ -f "$models_path/$file" ]; then
        file_size=$(ls -lh "$models_path/$file" | awk '{print $5}')
        echo "  - $file ($file_size)"
    fi
done

# 构建应用
echo ""
echo "🔨 4. 构建withModels变体..."
echo "注意: withModels变体包含预置模型文件，构建时间较长且APK较大"

./gradlew assembleWithModelsDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 检查APK文件是否存在
apk_path="app/build/outputs/apk/withModels/debug/app-withModels-debug.apk"
if [ ! -f "$apk_path" ]; then
    echo "❌ APK文件不存在: $apk_path"
    exit 1
fi

# 获取APK信息
apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
echo "📦 APK大小: $apk_size"
echo "💡 withModels变体APK较大是正常的，因为包含了预置模型文件"

# 安装应用
echo ""
echo "📲 5. 安装应用到设备..."
adb install -r "$apk_path"

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ 安装成功"

# 启动应用
echo ""
echo "🎯 6. 启动应用..."
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
echo "📊 7. 监控应用日志..."
echo "运行以下命令查看实时日志："
echo "adb logcat | grep -E 'SherpaOnnx|🎯|🎉|STT|WakeService|AssetManager'"

echo ""
echo "🎤 8. 测试说明..."
echo "现在可以测试以下功能："
echo "- 进入Settings检查Wake word recognition method设置"
echo "- 测试韩语唤醒词: 하이넛지"
echo "- 测试中文唤醒词: 小艺小艺"
echo "- 测试英文唤醒词: Hey Nudget"

echo ""
echo "✨ withModels变体特点:"
echo "- ✅ 包含预置SherpaOnnx KWS模型文件"
echo "- ✅ 使用AssetManager加载模型，无需外部文件"
echo "- ✅ 安装后即可直接使用，无需额外配置"
echo "- ✅ 不依赖外部存储权限"
echo "- ⚠️  APK体积较大 (包含模型文件)"

echo ""
echo "🔍 9. 验证withModels变体运行状态..."
echo "检查应用是否正确识别为withModels变体..."

# 等待应用启动
sleep 3

# 检查日志中的变体信息
echo "查看构建变体检测日志:"
adb logcat -d | grep -E "🏷️.*构建变体|📦.*AssetManager" | tail -5

echo ""
echo "🎉 withModels变体脚本执行完成！"
echo ""
echo "📝 下一步建议:"
echo "1. 进入应用设置，确认Wake word method为SherpaOnnx"
echo "2. 测试语音唤醒功能"
echo "3. 查看日志确认模型加载成功"
echo "4. 如有问题，检查AssetManager相关日志"
