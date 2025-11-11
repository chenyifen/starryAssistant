# 唤醒触发式悬浮助手改造任务清单

## 项目概述
将现有的点击触发式悬浮球助手改造为唤醒触发式，实现更自然的语音交互流程。

**核心理念：**
- 应用启动后隐藏在后台，等待语音唤醒
- 唤醒后显示悬浮球并开始ASR监听
- 静音超时后自动隐藏，回到等待唤醒状态
- 支持ASR文本中多命令识别

---

## 任务1: 悬浮球UI和交互改造

### 输入
- 现有的`DraggableFloatingOrb`类（可点击、可拖动）
- 现有的文本显示逻辑（半屏显示）
- 现有的`EnhancedFloatingWindowService`

### 输出
- 不可点击的悬浮球（禁用点击和长按事件）
- 固定在屏幕左下角的悬浮球
- ASR/TTS文本显示在悬浮球右侧（水平排列）
- 启动后不显示悬浮球（完全隐藏）

### 实现方案
1. **禁用悬浮球点击交互**
   - 修改`DraggableFloatingOrb.kt`
   - 移除`onOrbClick`和`onOrbLongPress`回调设置
   - 禁用触摸事件处理（或只保留拖动，但可设为不可拖动）

2. **固定悬浮球位置**
   - 修改`DraggableFloatingOrb.kt`的初始位置逻辑
   - 设置固定坐标：左下角（x=边距，y=屏幕高度-悬浮球高度-边距）
   - 禁用拖动功能

3. **文本显示重构**
   - 在`DraggableFloatingOrb`中添加文本显示布局
   - 使用水平LinearLayout或Row布局
   - 左侧：悬浮球动画
   - 右侧：文本显示区域（ASR识别文本、TTS回复文本）

4. **启动时隐藏悬浮球**
   - 修改`EnhancedFloatingWindowService.onCreate()`
   - 移除`showFloatingOrb()`调用
   - 只在唤醒时才显示悬浮球

### 检查方案
- [ ] 启动应用，确认没有悬浮球显示
- [ ] 悬浮球显示时，位置固定在左下角
- [ ] 点击悬浮球无反应
- [ ] ASR文本和TTS文本显示在悬浮球右侧
- [ ] 文本显示清晰可读，不遮挡悬浮球动画

### 涉及文件
- `app/src/main/kotlin/com/ai/voice/ui/floating/DraggableFloatingOrb.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/EnhancedFloatingWindowService.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/components/LottieAnimationManager.kt`

---

## 任务2: 移除Storage权限（Hyundai IT渠道）

### 输入
- 现有的`AndroidManifest.xml`
- 现有的权限检查代码

### 输出
- 移除`READ_EXTERNAL_STORAGE`和`MANAGE_EXTERNAL_STORAGE`权限声明
- 移除相关权限检查代码

### 实现方案
1. **修改AndroidManifest.xml**
   - 移除或注释掉外部存储权限声明
   - 保留权限但添加flavor条件（仅非hyundaiit渠道需要）

2. **移除权限检查代码**
   - 搜索所有`READ_EXTERNAL_STORAGE`相关代码
   - 移除或条件编译相关检查

3. **验证模型文件加载**
   - 确认assets目录中的模型文件完整
   - 确认模型加载逻辑使用assets而非外部存储

### 检查方案
- [ ] 编译成功，无权限相关错误
- [ ] 安装后不请求存储权限
- [ ] ASR和唤醒功能正常工作
- [ ] 模型文件从assets正常加载

### 涉及文件
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/ai/voice/MainActivity.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/FloatingLauncherActivity.kt`

---

## 任务3: 状态管理机制改造

### 输入
- 现有的`VoiceAssistantStateProvider`
- 现有的`EnhancedFloatingWindowService`状态处理
- SenseVoice ASR设备
- V8 Wake设备

### 输出
- 新的状态流转机制：等待唤醒 → 唤醒检测 → 显示悬浮球 → ASR监听 → 静音超时 → 隐藏悬浮球 → 等待唤醒
- ASR在后台持续监听
- 静音10秒超时自动退出

### 实现方案
1. **状态定义**
   ```kotlin
   enum class AssistantState {
       WAITING_WAKE,    // 等待唤醒（悬浮球隐藏）
       WAKE_DETECTED,   // 检测到唤醒词
       LISTENING,       // ASR监听中（悬浮球显示）
       PROCESSING,      // 处理命令
       SPEAKING,        // TTS播放
       TIMEOUT          // 静音超时
   }
   ```

2. **唤醒后流程**
   - WakeService检测到唤醒词
   - 调用`EnhancedFloatingWindowService.handleVoiceWakeUp()`
   - 显示悬浮球（左下角，idle动画）
   - 启动SenseVoice ASR持续监听

3. **静音检测机制**
   - 在`SenseVoiceInputDevice`中添加静音检测
   - 记录最后一次接收到音频的时间
   - 超过10秒无有效音频输入时触发超时
   - 或者使用VAD（语音活动检测）判断静音

4. **超时处理**
   - 停止ASR监听
   - 隐藏悬浮球
   - 状态回到`WAITING_WAKE`
   - WakeService继续监听唤醒词

5. **状态流转管理**
   - 修改`VoiceAssistantStateProvider`
   - 添加状态机逻辑
   - 确保状态流转的原子性和正确性

### 检查方案
- [ ] 应用启动后，悬浮球隐藏，WakeService运行
- [ ] 说出唤醒词后，悬浮球显示在左下角
- [ ] 可以进行语音交互（识别命令）
- [ ] 静音10秒后，悬浮球自动隐藏
- [ ] 再次说出唤醒词，悬浮球重新显示
- [ ] 日志中可以看到清晰的状态流转

### 涉及文件
- `app/src/main/kotlin/com/ai/voice/ui/floating/state/VoiceAssistantStateProvider.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/EnhancedFloatingWindowService.kt`
- `app/src/main/kotlin/com/ai/voice/io/input/sensevoice/SenseVoiceInputDevice.kt`
- `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt`
- `app/src/main/kotlin/com/ai/voice/io/wake/onnx/HiNudgeOnnxV8WakeDevice.kt`

---

## 任务4: 动画状态映射简化

### 输入
- 现有的`LottieAnimationManager`和`LottieAnimationState`
- 多种动画状态（IDLE, LOADING, ACTIVE, WAKE_WORD等）

### 输出
- 简化的动画状态映射
- 未唤醒：悬浮窗不显示
- 唤醒后：使用idle动画，持续显示直到超时

### 实现方案
1. **简化动画状态**
   - 唤醒后只使用一种动画状态（当前的idle动画）
   - 移除其他动画切换逻辑
   - 保持动画简洁统一

2. **状态到动画的映射**
   ```kotlin
   WAITING_WAKE -> 不显示悬浮球
   WAKE_DETECTED -> 显示悬浮球（idle动画）
   LISTENING -> idle动画
   PROCESSING -> idle动画
   SPEAKING -> idle动画
   TIMEOUT -> 隐藏悬浮球
   ```

3. **移除不必要的动画切换**
   - 简化`LottieAnimationManager`
   - 移除频繁的动画状态切换
   - 减少视觉干扰

### 检查方案
- [ ] 未唤醒时悬浮窗完全不显示
- [ ] 唤醒后悬浮球显示idle动画
- [ ] 整个交互过程中动画保持一致
- [ ] 超时后悬浮球消失
- [ ] 动画流畅，无卡顿

### 涉及文件
- `app/src/main/kotlin/com/ai/voice/ui/floating/components/LottieAnimationManager.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/components/LottieAnimationState.kt`
- `app/src/main/kotlin/com/ai/voice/ui/floating/DraggableFloatingOrb.kt`

---

## 任务5: ASR文本多命令识别增强（复杂）

### 输入
- 现有的技能匹配机制（单句完整匹配）
- `SkillEvaluator`评估逻辑
- 所有已定义的技能

### 输出
- 支持从长文本中提取多个命令
- 支持部分匹配（文本中包含命令关键词）
- 返回所有匹配到的命令及其得分

### 实现方案

#### 5.1 需求分析
- **问题**: 当前技能匹配要求整句话匹配某个技能的句子模式
- **需求**: 用户可能说"打开空调并且调高音量"，需要识别出两个命令
- **挑战**: 
  - 如何分割长文本中的多个命令
  - 如何处理命令之间的连接词
  - 如何避免误匹配
  - 如何确定命令的执行顺序

#### 5.2 设计方案

**方案A: 关键词提取 + 模糊匹配**
1. 为每个技能定义关键词列表
2. 扫描ASR文本，提取所有包含关键词的片段
3. 对每个片段进行技能匹配
4. 返回匹配成功的所有技能

**方案B: 滑动窗口匹配**
1. 使用不同长度的滑动窗口扫描ASR文本
2. 对每个窗口内容进行技能匹配
3. 合并重叠的匹配结果
4. 按位置排序，顺序执行

**方案C: 分句 + 独立匹配**
1. 使用NLP技术将长文本分句
2. 对每个句子独立进行技能匹配
3. 收集所有匹配结果
4. 按顺序执行

**推荐方案: B + C 混合**
- 先进行简单分句（基于"并且"、"然后"、"再"等连接词）
- 对每个子句使用滑动窗口匹配
- 合并结果，按位置顺序执行

#### 5.3 实现步骤

**5.3.1 创建文本分割器**
```kotlin
class CommandTextSplitter {
    fun split(text: String): List<CommandSegment>
    // 返回: [(原始文本, 起始位置, 结束位置)]
}
```

**5.3.2 创建多命令评估器**
```kotlin
class MultiCommandEvaluator(
    private val singleEvaluator: SkillEvaluator
) {
    fun evaluateMultiCommand(text: String): List<CommandMatch>
    // 返回: [(技能, 得分, 匹配文本, 位置)]
}
```

**5.3.3 修改SkillEvaluator**
- 添加部分匹配模式
- 支持返回匹配位置信息
- 降低匹配阈值，允许模糊匹配

**5.3.4 创建命令执行协调器**
```kotlin
class CommandExecutor {
    suspend fun executeCommands(commands: List<CommandMatch>)
    // 按顺序执行多个命令
    // 处理命令之间的依赖关系
    // 合并输出结果
}
```

**5.3.5 集成到现有流程**
- 修改`processInputEvent`方法
- 检测到ASR文本后，使用`MultiCommandEvaluator`
- 如果找到多个命令，使用`CommandExecutor`执行
- 如果只有一个命令，走现有流程

#### 5.4 关键词定义
为主要技能定义关键词：
- **设备控制**: 打开、关闭、调高、调低、空调、灯光、音量
- **导航**: 回到、前往、主页、返回、上一页
- **应用启动**: 启动、打开、运行（+ 应用名）
- **电源控制**: 关机、重启、待机
- **输入源**: 切换、输入源、HDMI、USB

#### 5.5 测试用例
```
测试1: "打开空调"
期望: 1个命令（打开空调）

测试2: "打开空调并且调高音量"
期望: 2个命令（打开空调 + 调高音量）

测试3: "回到主页然后打开设置"
期望: 2个命令（回到主页 + 打开设置）

测试4: "请帮我打开空调谢谢"
期望: 1个命令（打开空调），忽略礼貌用语

测试5: "今天天气怎么样打开空调"
期望: 2个命令（天气查询 + 打开空调）
```

### 检查方案
- [ ] 单命令测试通过（向后兼容）
- [ ] 双命令测试通过（用"并且"连接）
- [ ] 多命令测试通过（3个以上命令）
- [ ] 部分匹配测试通过（命令嵌入在长句中）
- [ ] 误匹配率可接受（不会把普通对话识别为命令）
- [ ] 命令执行顺序正确
- [ ] 性能可接受（不影响响应速度）

### 涉及文件
- `app/src/main/kotlin/com/ai/voice/eval/SkillEvaluator.kt`（新增）
- `app/src/main/kotlin/com/ai/voice/eval/MultiCommandEvaluator.kt`（新建）
- `app/src/main/kotlin/com/ai/voice/eval/CommandTextSplitter.kt`（新建）
- `app/src/main/kotlin/com/ai/voice/eval/CommandExecutor.kt`（新建）
- `app/src/main/kotlin/com/ai/voice/util/AsrTextNormalizer.kt`（已存在，增强）
- 所有技能Info类（添加关键词定义）

---

## 任务执行顺序

建议按以下顺序执行，每完成一个任务进行测试验证：

1. **任务2** - 移除Storage权限（最简单，独立）
2. **任务4** - 动画状态映射简化（为后续改造做准备）
3. **任务1** - 悬浮球UI改造（视觉和交互基础）
4. **任务3** - 状态管理机制改造（核心功能）
5. **任务5** - ASR多命令识别（增强功能）

---

## 风险和注意事项

### 风险1: WakeService与ASR的资源冲突
- **问题**: 两者同时使用麦克风可能冲突
- **解决**: 唤醒后暂停WakeService，超时后恢复

### 风险2: 静音检测准确性
- **问题**: 可能误判静音或延迟判断
- **解决**: 使用VAD + 时间阈值双重判断

### 风险3: 多命令执行的用户体验
- **问题**: 连续执行多个命令可能让用户困惑
- **解决**: 
  - 提供清晰的反馈（"已识别到2个命令"）
  - 支持中断机制
  - 命令之间添加短暂停顿

### 风险4: 性能影响
- **问题**: 持续ASR监听可能耗电、耗内存
- **解决**: 
  - 优化ASR模型参数
  - 添加电量监控
  - 低电量时缩短超时时间

---

## 成功标准

- [ ] 应用启动后悬浮球隐藏，后台待命
- [ ] 唤醒词触发后悬浮球立即显示在左下角
- [ ] ASR文本和TTS文本显示在悬浮球右侧
- [ ] 静音10秒后自动退出，悬浮球消失
- [ ] 支持识别单句中的多个命令
- [ ] 不需要存储权限，应用可正常运行
- [ ] 所有状态流转清晰可追踪
- [ ] 用户体验流畅自然
