# Dicio Android 测试指南

## 📋 概述

本项目使用Android Instrumented Test框架进行测试，所有测试文件位于 `app/src/androidTest/kotlin/` 目录中。

## 🚀 快速开始

### 1. 环境要求

- **Java 17** (必需，Hilt插件要求)
- **Android设备或模拟器** (已连接并启用USB调试)
- **adb工具** (Android SDK的一部分)

### 2. 运行测试

使用提供的测试脚本：

```bash
# 运行基础测试
./run_tests.sh simple

# 运行截图测试  
./run_tests.sh screenshot

# 运行所有测试
./run_tests.sh all

# 显示帮助
./run_tests.sh help
```

### 3. 手动运行测试

如果需要手动运行特定测试：

```bash
# 设置Java版本
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home

# 运行SimpleAdvancedTestSuite
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.stypox.dicio.test.SimpleAdvancedTestSuiteTest

# 运行ScreenshotTaker测试
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.stypox.dicio.screenshot.ScreenshotTakerTest

# 运行所有测试
./gradlew connectedAndroidTest
```

## 📁 测试文件结构

```
app/src/androidTest/kotlin/org/stypox/dicio/
├── CustomTestRunner.kt                    # 自定义测试运行器
├── screenshot/                           # 截图测试相关
│   ├── CoilEventListener.kt
│   ├── DisableAnimationsRule.kt
│   ├── FakeSkillEvaluator.kt
│   ├── FakeSttInputDeviceWrapper.kt
│   ├── FakeWakeDeviceWrapper.kt
│   ├── ScreenshotTakerTest.kt           # 截图测试
│   ├── ScreenshotUtil.kt
│   └── screenshot_server.py
└── test/
    └── SimpleAdvancedTestSuiteTest.kt    # 基础测试套件
```

## 🧪 测试类型

### 1. SimpleAdvancedTestSuiteTest

**位置**: `app/src/androidTest/kotlin/org/stypox/dicio/test/SimpleAdvancedTestSuiteTest.kt`

**功能**:
- 基础环境测试
- 日志系统测试  
- 模拟语音助手组件测试

**运行方式**:
```bash
./run_tests.sh simple
```

### 2. ScreenshotTakerTest

**位置**: `app/src/androidTest/kotlin/org/stypox/dicio/screenshot/ScreenshotTakerTest.kt`

**功能**:
- UI截图测试
- 界面回归测试

**运行方式**:
```bash
./run_tests.sh screenshot
```

## 📊 测试结果

### 查看测试报告

测试完成后，可以通过以下方式查看结果：

1. **HTML报告**: `app/build/reports/androidTests/connected/debug/index.html`
2. **日志文件**: `test_logs.txt` (脚本运行时生成)
3. **控制台输出**: 实时显示测试进度和结果

### 日志标识

测试日志使用emoji标识便于识别：

- 🚀 测试开始
- ✅ 测试成功
- ❌ 测试失败
- 📱 设备信息
- 🧪 测试执行
- 🔧 环境检查
- 📊 数据分析
- 🎵 音频测试
- 🔄 流程测试
- 🎯 测试完成

## 🔧 故障排除

### 常见问题

1. **Java版本错误**
   ```
   错误: UnsupportedClassVersionError
   解决: 确保使用Java 17
   ```

2. **设备未连接**
   ```
   错误: 未找到连接的Android设备
   解决: 检查USB连接和调试模式
   ```

3. **编译错误**
   ```
   错误: Compilation error
   解决: 检查代码语法和依赖项
   ```

### 调试技巧

1. **查看详细日志**:
   ```bash
   ./gradlew connectedAndroidTest --info --stacktrace
   ```

2. **监控实时日志**:
   ```bash
   adb logcat -v time | grep -E "(SimpleAdvancedTestSuiteTest|🚀|✅|❌)"
   ```

3. **清理构建缓存**:
   ```bash
   ./gradlew clean
   ```

## 📝 添加新测试

### 创建新测试类

1. 在 `app/src/androidTest/kotlin/org/stypox/dicio/test/` 目录下创建新的测试文件
2. 使用以下模板：

```kotlin
package org.stypox.dicio.test

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YourNewTest {
    
    companion object {
        private const val TAG = "YourNewTest"
    }
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Log.d(TAG, "✅ 测试环境初始化完成")
    }
    
    @Test
    fun testYourFeature() {
        Log.d(TAG, "🚀 开始测试...")
        
        // 测试逻辑
        
        Log.d(TAG, "✅ 测试完成")
    }
}
```

### 更新测试脚本

在 `run_tests.sh` 中添加新的测试选项：

```bash
"your_test")
    ./gradlew connectedAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=org.stypox.dicio.test.YourNewTest
    ;;
```

## 🎯 最佳实践

1. **使用描述性的测试名称**
2. **添加适当的日志输出**
3. **使用emoji标识便于日志查看**
4. **测试前进行环境检查**
5. **测试后清理资源**
6. **使用断言验证结果**

## 📚 相关文档

- [Android Testing Guide](https://developer.android.com/training/testing)
- [JUnit 4 Documentation](https://junit.org/junit4/)
- [Gradle Android Plugin](https://developer.android.com/studio/build)
