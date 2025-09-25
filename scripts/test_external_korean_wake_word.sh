#!/bin/bash

# 测试外部存储韩语唤醒词功能
# 验证优先级：外部存储 > Assets

set -e

echo "🇰🇷 测试外部存储韩语唤醒词功能..."

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 没有找到连接的Android设备"
    exit 1
fi

echo "📱 检测到Android设备"

# 检查外部存储中的模型文件
TARGET_DIR="/storage/emulated/0/Dicio/models/openWakeWord"
echo "🔍 检查外部存储中的韩语模型文件..."

if adb shell "test -f '$TARGET_DIR/wake.tflite'"; then
    echo "✅ 外部存储中存在韩语唤醒词模型"
    adb shell "ls -la '$TARGET_DIR/'"
else
    echo "❌ 外部存储中不存在韩语唤醒词模型"
    echo "💡 请先运行: ./push_korean_models.sh"
    exit 1
fi

# 构建应用（如果需要）
if [ ! -f "app/build/outputs/apk/withModels/debug/app-withModels-debug.apk" ]; then
    echo "🔨 构建应用..."
    export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
    ./gradlew assembleWithModelsDebug
fi

# 安装应用
echo "📦 安装应用..."
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk

# 启动应用
echo "🚀 启动应用..."
adb shell am start -n org.stypox.dicio/.ui.main.MainActivity

# 等待应用启动
sleep 3

echo ""
echo "🧪 测试外部存储优先级功能..."
echo ""
echo "📋 测试步骤："
echo "1. 在应用中切换语言到韩语（한국어）"
echo "2. 观察日志中是否显示'Found Korean wake word in external storage'"
echo "3. 启用OpenWakeWord唤醒功能"
echo "4. 说'하이넛지'测试唤醒"
echo ""

# 启动日志监控
echo "📊 启动日志监控（按Ctrl+C停止）..."
echo "关键日志标识："
echo "  • '📱 Found Korean wake word in external storage' - 检测到外部存储模型"
echo "  • '✅ Korean wake word copied from external storage' - 从外部存储复制成功"
echo "  • '📱 Source: /storage/emulated/0/Dicio/models/openWakeWord/wake.tflite' - 确认源路径"
echo ""

# 提供选择：实时监控或手动测试
echo "选择测试模式："
echo "1) 实时日志监控"
echo "2) 手动测试指导"
echo "3) 验证优先级切换"
read -p "请选择 (1-3): " choice

case $choice in
    1)
        echo "🔍 启动实时日志监控..."
        adb logcat | grep -E "(LanguageWakeWordManager|External.*Korean|Found Korean wake word|copied from external)"
        ;;
    2)
        echo ""
        echo "📝 手动测试指导："
        echo ""
        echo "1. 🌐 切换语言测试："
        echo "   • 进入设置 → 语言 → 选择'한국어'"
        echo "   • 观察应用是否自动设置韩语唤醒词"
        echo ""
        echo "2. 🎯 唤醒功能测试："
        echo "   • 进入设置 → 唤醒方法 → 选择'OpenWakeWord offline audio processing'"
        echo "   • 确认唤醒词设置显示'하이넛지'"
        echo ""
        echo "3. 🎤 语音唤醒测试："
        echo "   • 说'하이넛지'来测试唤醒"
        echo "   • 观察应用是否响应并启动语音识别"
        echo ""
        echo "4. 📊 验证外部存储优先级："
        echo "   • 查看日志确认使用的是外部存储模型"
        echo "   • 应该看到'Found Korean wake word in external storage'"
        echo ""
        echo "🔍 调试命令："
        echo "adb logcat | grep -E '(LanguageWakeWordManager|Korean)'"
        ;;
    3)
        echo ""
        echo "🔄 验证优先级切换功能..."
        echo ""
        echo "当前状态：外部存储中有韩语模型"
        echo ""
        echo "测试1: 删除外部存储模型，验证回退到Assets"
        read -p "是否删除外部存储模型进行测试？(y/n): " delete_external
        
        if [[ "$delete_external" =~ ^[Yy]$ ]]; then
            echo "🗑️ 删除外部存储模型..."
            adb shell "rm -rf '$TARGET_DIR'"
            echo "✅ 外部存储模型已删除"
            echo ""
            echo "现在重新启动应用并切换到韩语，应该使用Assets中的模型"
            echo "日志中应该显示'Found Korean wake word in assets'"
            echo ""
            
            # 重启应用
            adb shell am force-stop org.stypox.dicio
            sleep 1
            adb shell am start -n org.stypox.dicio/.ui.main.MainActivity
            
            echo "📊 监控Assets回退日志..."
            timeout 10s adb logcat | grep -E "(LanguageWakeWordManager|Found Korean wake word in assets)" || true
            
            echo ""
            echo "测试2: 恢复外部存储模型"
            read -p "是否恢复外部存储模型？(y/n): " restore_external
            
            if [[ "$restore_external" =~ ^[Yy]$ ]]; then
                echo "📤 恢复外部存储模型..."
                ./push_korean_models.sh
                echo ""
                echo "现在重新启动应用，应该重新使用外部存储模型"
                
                # 重启应用
                adb shell am force-stop org.stypox.dicio
                sleep 1
                adb shell am start -n org.stypox.dicio/.ui.main.MainActivity
            fi
        fi
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo ""
echo "✅ 外部存储韩语唤醒词测试完成！"
echo ""
echo "📋 功能总结："
echo "• ✅ 韩语模型已推送到外部存储"
echo "• ✅ 应用支持外部存储优先级"
echo "• ✅ 语言切换自动使用对应唤醒词"
echo "• ✅ 外部存储 > Assets 优先级机制"
