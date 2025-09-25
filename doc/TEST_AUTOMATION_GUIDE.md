# 语音助手自动化测试系统

## 🎯 概述

这是一套完整的语音助手自动化测试系统，用于验证Dicio语音助手的稳定性、正确性和性能。测试系统包含多个层次的测试，从单元测试到集成测试，从功能测试到压力测试。

## 🏗️ 架构设计

### 核心组件

1. **VoiceAssistantTestFramework** - 测试框架核心
   - 状态机转换测试
   - 音频管道测试
   - UI集成测试
   - 场景测试
   - 压力测试和边界条件测试

2. **TestRunner** - 测试运行器
   - 执行测试套件
   - 生成HTML/JSON报告
   - 监控测试进度
   - 日志收集和分析

3. **AutomatedTestLauncher** - 测试启动器
   - 简化的测试API
   - 与应用集成
   - 实时状态监控

4. **命令行工具** - `test_voice_assistant.py`
   - 独立的测试执行
   - ADB集成
   - 外部测试验证

## 🚀 快速开始

### 1. 环境准备

```bash
# 确保ADB已安装并可用
adb version

# 连接Android设备
adb devices

# 安装应用
cd /Users/user/AndroidStudioProjects/dicio-android
./gradlew installDebug
```

### 2. 运行测试

#### 方式1: 命令行工具 (推荐)

```bash
# 运行完整测试套件
python3 test_voice_assistant.py --test full

# 运行特定测试
python3 test_voice_assistant.py --test basic      # 基本功能测试
python3 test_voice_assistant.py --test state      # 状态机测试
python3 test_voice_assistant.py --test memory     # 内存测试
python3 test_voice_assistant.py --test audio      # 音频管道测试
python3 test_voice_assistant.py --test continuous # 连续操作测试

# 自定义参数
python3 test_voice_assistant.py --test continuous --duration 10 --output my_report.json
```

#### 方式2: 应用内测试

```kotlin
// 在应用代码中集成
val testLauncher = AutomatedTestLauncher(...)
VoiceAssistantTester.initialize(testLauncher)

// 运行测试
VoiceAssistantTester.runAllTests()
VoiceAssistantTester.runQuickValidation()
VoiceAssistantTester.runStateMachineTests()
```

## 📊 测试类别详解

### 1. 状态机转换测试 (STATE_MACHINE)

**目标**: 验证音频管道状态机的正确性

**测试内容**:
- 基本状态转换 (Idle → WakeListening → WakeDetected → AsrListening → WakeListening)
- 并发状态转换处理
- 状态回滚机制
- 状态持久性

**关键验证点**:
```
✅ 状态转换序列正确
✅ 并发操作不会导致状态混乱
✅ 异常情况下能正确回滚
✅ 状态变化能正确通知UI
```

### 2. 音频管道测试 (AUDIO_PIPELINE)

**目标**: 验证音频资源管理的独占性和稳定性

**测试内容**:
- 音频资源独占性 (WakeService vs ASR)
- 音频切换流畅性
- 音频异常恢复
- 音频资源泄露检测

**关键验证点**:
```
✅ WakeService和ASR不能同时使用音频
✅ 音频切换无卡顿和异常
✅ AudioRecord异常后能正确恢复
✅ 无音频资源泄露
```

### 3. UI集成测试 (UI_INTEGRATION)

**目标**: 验证UI与后端服务的同步性

**测试内容**:
- ASR实时文本显示
- TTS文本显示
- UI状态同步
- UI响应性测试

**关键验证点**:
```
✅ ASR文本实时更新
✅ 部分识别结果连续显示
✅ 最终识别结果正确
✅ UI状态与服务状态同步
✅ 界面响应及时无卡顿
```

### 4. 场景测试 (SCENARIO_TEST)

**目标**: 验证真实使用场景下的稳定性

**测试内容**:
- 连续对话场景
- 快速连续唤醒
- 长时间运行稳定性
- 多技能切换

**关键验证点**:
```
✅ 连续多轮对话正常
✅ 快速操作不会崩溃
✅ 长时间运行无内存泄露
✅ 技能切换流畅
```

### 5. 压力测试 (STRESS_TEST)

**目标**: 验证系统在高负载下的表现

**测试内容**:
- 高频操作压力测试
- 内存压力测试
- 并发压力测试

**关键验证点**:
```
✅ 高频操作不会导致崩溃
✅ 内存使用在合理范围内
✅ 并发操作处理正确
```

### 6. 边界条件测试 (EDGE_CASE)

**目标**: 验证异常情况的处理能力

**测试内容**:
- 空输入处理
- 超长输入处理
- 网络异常处理
- 资源不足处理

**关键验证点**:
```
✅ 空输入不会导致崩溃
✅ 超长输入能正确处理
✅ 网络异常有合适的降级策略
✅ 资源不足时能优雅处理
```

## 📈 测试报告

### HTML报告特性

- 📊 可视化测试结果统计
- 🎨 美观的界面设计
- 📋 详细的测试执行信息
- 🔍 按类别分组的测试结果
- ⏱️ 执行时间和性能指标

### JSON报告特性

- 🔧 机器可读的结构化数据
- 📊 便于集成到CI/CD系统
- 📈 支持历史趋势分析
- 🔄 可用于自动化处理

## 🛠️ 高级用法

### 自定义测试配置

```kotlin
val config = TestConfiguration(
    categories = setOf(
        TestCategory.STATE_MACHINE,
        TestCategory.AUDIO_PIPELINE
    ),
    generateReport = true,
    enableStressTests = false,
    maxTestDuration = 300000L,
    logLevel = LogLevel.INFO
)
```

### 监控测试进度

```kotlin
// 监控测试状态
testLauncher.testState.collect { state ->
    when (state) {
        TestState.RUNNING -> println("测试运行中...")
        TestState.COMPLETED -> println("测试完成")
        TestState.ERROR -> println("测试出错")
    }
}

// 监控测试结果
testLauncher.testResults.collect { result ->
    result?.let {
        println("成功率: ${it.testSuiteResult?.successRate}")
    }
}
```

### 集成到CI/CD

```bash
#!/bin/bash
# ci_test.sh

echo "开始语音助手自动化测试..."

# 启动模拟器或连接设备
adb wait-for-device

# 安装应用
./gradlew installDebug

# 运行测试
python3 test_voice_assistant.py --test full --output ci_report.json

# 检查测试结果
if [ $? -eq 0 ]; then
    echo "✅ 所有测试通过"
    exit 0
else
    echo "❌ 测试失败"
    exit 1
fi
```

## 🔧 故障排除

### 常见问题

1. **ADB连接问题**
   ```bash
   adb kill-server
   adb start-server
   adb devices
   ```

2. **应用权限问题**
   ```bash
   adb shell pm grant org.stypox.dicio.master android.permission.RECORD_AUDIO
   adb shell pm grant org.stypox.dicio.master android.permission.SYSTEM_ALERT_WINDOW
   ```

3. **测试超时**
   - 检查设备性能
   - 调整测试超时时间
   - 确认网络连接

4. **内存不足**
   - 关闭其他应用
   - 重启设备
   - 检查可用存储空间

### 日志分析

关键日志标识符：
```
🧪 开始执行测试
✅ 测试通过
❌ 测试失败
⏰ 测试超时
🔄 状态转换
🎵 音频操作
📱 UI更新
```

## 📚 扩展开发

### 添加新测试

1. 在`VoiceAssistantTestFramework`中添加测试方法
2. 在相应的测试类别中注册
3. 更新测试计数估算
4. 添加相应的文档

### 自定义测试类别

```kotlin
enum class CustomTestCategory {
    PERFORMANCE,
    SECURITY,
    ACCESSIBILITY
}
```

### 集成外部工具

- Monkey测试集成
- 性能监控工具
- 崩溃报告系统
- 自动化截图对比

## 🎯 最佳实践

1. **测试前准备**
   - 确保设备电量充足
   - 关闭其他音频应用
   - 连接稳定的网络

2. **测试执行**
   - 从基本测试开始
   - 逐步增加测试复杂度
   - 定期检查测试结果

3. **结果分析**
   - 关注成功率趋势
   - 分析失败原因
   - 优化测试策略

4. **持续改进**
   - 根据测试结果优化代码
   - 更新测试用例
   - 扩展测试覆盖范围

---

## 📞 支持

如有问题或建议，请：
1. 查看测试日志
2. 检查常见问题解决方案
3. 提交详细的问题报告

**测试愉快！** 🎉
