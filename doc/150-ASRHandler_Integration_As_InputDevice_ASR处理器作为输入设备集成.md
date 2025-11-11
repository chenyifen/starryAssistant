# AsrHandler 作为 InputDevice 的整合分析与迁移方案

## 背景
- 目前 `AsrHandler` 在应用内承担了语音输入（ASR）的核心功能：初始化 Sherpa-ONNX 的离线识别器与 VAD、开启/停止录音、进行实时/最终识别、并通过回调触发技能识别与 UI 状态更新。
- 传统 STT 流程基于 `SttInputDevice` 接口，通过 `SttInputDeviceWrapper` 注入与切换不同设备（如 `VoskInputDevice`、`TwoPassInputDevice`、`SherpaOnnxSimulateInputDevice`、`ExternalPopupInputDevice` 等），并在 UI 中统一为 `SttState` 与 `InputEvent`。
- 需求：评估并设计将 `AsrHandler` 作为一种 `InputDevice` 合并进入现有流程，减少分散管理与重复逻辑，使 UI/业务层重新统一使用 STT 抽象。

## AsrHandler 概述
- 单例对象：`object AsrHandler`
- 依赖：`OfflineRecognizer`、`Vad`（Sherpa-ONNX），`AudioRecord`，协程通道（`Channel<FloatArray>(capacity = 100)`）
- 主要接口：
  - `initialize(context: Context): Boolean`：初始化识别器与 VAD（资产模型与配置）
  - `start(context: Context): Boolean` / `stop(context: Context)`：开启/停止录音与识别
  - `isStarted(): Boolean`：识别是否运行中
  - `getResultList(): List<String>` / `clearResults()`：供 UI 展示实时/最终文本列表
  - `setSilenceTimeoutCallback(() -> Unit)`：静音超时回调（目前用于更新 Orb 状态）
  - `setFinalResultCallback((String) -> Unit)`：最终识别结果回调（用于触发技能）
- 识别流程要点：
  - 音频采集（IO）：从麦克风读 16kHz PCM，写入 `samplesChannel`
  - 音频处理（Default）：VAD 滑窗检测、每 200ms 做一次实时识别更新；在 VAD 段结束时做最终识别，并触发 `finalResultCallback`
  - 静音超时：基于 VAD 的最近语音时间，超过阈值自动 `stop()` 并触发回调
  - 资源：内部管理 `AudioRecord`，未提供 `destroy()`；模型由 `SimulateStreamingAsr` 单例持有

## SttInputDevice 流程概述
- 接口：`SttInputDevice`
  - `val uiState: StateFlow<SttState>`（UI 层统一状态）
  - `fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean`
  - `fun stopListening()`
  - `fun onClick(eventListener: (InputEvent) -> Unit)`
  - `suspend fun destroy()`
- 事件模型：`InputEvent`
  - `Partial(utterance)`：语音中实时文本
  - `Final(utterances: List<Pair<String, Float>>)`：最终结果（带置信度）
  - `None`：启动后未检测到输入
  - `Error(throwable)`：过程错误
- UI 状态：`SttState`（NotInitialized/Loaded/Listening/...），用于统一 UI 层表现与交互逻辑。
- 注入与选择：`SttInputDeviceWrapper` 根据 `UserSettings.InputDevice` 枚举选择设备，并负责设备切换时的资源清理与 UI 状态联动。

## 差异与兼容性评估
- 生命周期与实例管理：
  - `AsrHandler` 为单例，`SttInputDevice` 为按需构造实例；可通过“适配器模式”在设备实例内委托给 `AsrHandler` 并避免重复构造。
- UI 状态流：
  - `AsrHandler` 未提供 `StateFlow<SttState>`；需要在适配器中按 `isStarted()` 与识别阶段映射为 `SttState.Listening/Loaded/NotInitialized` 等。
- 事件模型：
  - `AsrHandler` 通过 `resultList` 与 `finalResultCallback` 输出文本；需要在适配器中转换为 `InputEvent.Partial` 与 `InputEvent.Final`（可用单备选、置信度 1.0）。静音超时可发出 `InputEvent.None`。
- 权限与资源竞争：
  - `AsrHandler` 直接使用 `AudioRecord`，未集成 `AudioResourceManager` 的麦克风/tts 互斥；现有 Sherpa/Vosk 设备有 `AudioResourceManager.canRecord()` 检查。适配器需在启动前调用 `AudioResourceManager.requestMicrophone()` 并在停止后 `releaseMicrophone()`，避免与 TTS/WakeService 冲突。
- 模型管理：
  - `AsrHandler` 的模型来源与配置由 `getOfflineModelConfig()/getVadModelConfig()` 决定，未走 DataStore 语言/下载路径；适配器保持当前策略即可，不必映射到 `SttState.Downloaded/Unzipping/Loading` 的完整状态机（它更适合 Vosk）。
- 错误与回调：
  - 需在适配器层捕获启动/处理异常并发送 `InputEvent.Error`；同时确保 `destroy()` 时停止识别并清理回调。

## 整合方案（适配器模式）
目标：新增 `AsrHandlerInputDevice : SttInputDevice`，内部委托 `AsrHandler`，实现与现有 UI/业务层一致的接口与状态。

### 适配器职责映射
- `uiState`：
  - 初始：`SttState.Loaded`（若 `AsrHandler.initialize()` 完成）或 `NotInitialized`
  - 运行中：`SttState.Listening`
- `tryLoad(thenStart)`：
  - `AudioResourceManager.requestMicrophone(ASR_DEVICE)` 成功后调用 `AsrHandler.start(context)`
  - 绑定 `setFinalResultCallback { text -> thenStart(InputEvent.Final(listOf(text to 1.0f))) }`
  - 绑定实时文本到 `InputEvent.Partial`
  - 返回 `true` 表示会进入监听
- `onClick(listener)`：
  - 若未启动则与 `tryLoad(listener)` 等价；若已启动则调用 `stopListening()` 并在必要时发送 `InputEvent.None`
- `stopListening()`：
  - 调用 `AsrHandler.stop(context)`、`AudioResourceManager.releaseMicrophone(ASR_DEVICE)`；更新 `uiState` 为 `Loaded`
- `destroy()`：
  - 清理回调、停止识别、释放资源；不销毁模型（保持应用级初始化策略）

### 示例代码骨架（仅示意，不作为实现提交）
```kotlin
class AsrHandlerInputDevice(
    private val appContext: Context,
) : SttInputDevice {
    private val _uiState = MutableStateFlow<SttState>(SttState.NotInitialized)
    override val uiState: StateFlow<SttState> = _uiState
    private var eventListener: ((InputEvent) -> Unit)? = null

    init {
        if (AsrHandler.initialize(appContext)) {
            _uiState.value = SttState.Loaded
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        eventListener = thenStartListeningEventListener
        val granted = AudioResourceManager.requestMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
        if (!granted) return false

        AsrHandler.setFinalResultCallback { text ->
            eventListener?.invoke(InputEvent.Final(listOf(text to 1.0f)))
        }
        // 可选：监听 resultList 做 Partial 事件
        _uiState.value = SttState.Listening
        return AsrHandler.start(appContext)
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        if (!AsrHandler.isStarted()) tryLoad(eventListener) else stopListening()
    }

    override fun stopListening() {
        AsrHandler.stop(appContext)
        AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
        _uiState.value = SttState.Loaded
        eventListener?.invoke(InputEvent.None)
    }

    override suspend fun destroy() {
        AsrHandler.setFinalResultCallback(null)
        AsrHandler.setSilenceTimeoutCallback(null)
        if (AsrHandler.isStarted()) stopListening()
    }
}
```

## 迁移改动点（不立即实施，待确认）
为避免“大规模修改”，建议先以最小代价接入，再逐步替换：

1) 注入层：`SttInputDeviceWrapper.buildInputDevice()`
- 在 `when(setting)` 分支中，将 `INPUT_DEVICE_SHERPA_SIMULATE` 映射为 `AsrHandlerInputDevice(appContext)`（或新增枚举 `INPUT_DEVICE_ASR_HANDLER`，需要改动 `input_device.proto`，属于较大改动，需另行确认）。

2) UI/服务层回归统一接口：
- `EnhancedFloatingWindowService`：用 `SttInputDeviceWrapper` 替换直接调用 `AsrHandler.start/stop`；事件回调从 `InputEvent` 驱动技能识别与 UI；静音超时由设备内处理。
- `VoiceAssistantStateProvider`：改为订阅 `wrapper.uiState` 而非 `AsrHandler.isStarted()`；状态映射保持 `IDLE/LISTENING`。
- `WakeService`：去除对 `AsrHandler.isStarted()` 的轮询依赖，改为统一通过 `AudioResourceManager` 与设备状态协调；避免双 `AudioRecord` 竞争。
- `DraggableFloatingOrb`：从 `resultList` 迁移为订阅 `InputEvent.Partial/Final`（或维护同样的列表用于 UI 渲染）。

3) 资源互斥：
- 在适配器中统一调用 `AudioResourceManager.requestMicrophone/releaseMicrophone`，确保与 TTS/WakeWord 的麦克风共享策略一致。

4) 初始化策略：
- 保留 `App.onCreate()` 中的 `AsrHandler.initialize(this)`，以降低首次启动延迟；在适配器构造时避免重复初始化。

## 风险与测试建议
- 双录音竞争：目前 `WakeService` 与 `AsrHandler` 各自管理 `AudioRecord`；统一为 `SttInputDevice` 后需通过 `AudioResourceManager` 严格互斥，防止崩溃或录音失败。
- 模型路径与语言：`AsrHandler` 模型配置不走 DataStore 语言选择；若未来需要语言切换，需为 Sherpa 模型提供语言映射策略（可参考 `VoskInputDevice` 的 `resolveVoskLanguageCode` 方案）。
- 事件一致性：`InputEvent.Final` 目前只提供一个备选且置信度固定 1.0；如需多备选/置信度，需扩展识别器返回结构或增加打分逻辑。
- 性能与内存：`samplesChannel` 容量限制为 100；适配器需保留现有 buffer 清理策略，避免内存增长。

测试建议：
- 单元验证：
  - 适配器在静音超时、异常抛出、快速开关场景下的事件序列（Partial→Final/None/Error）是否符合协议。
- 集成验证：
  - 在 `FloatingWindow` 场景下，点击/唤醒流程的 UI 状态与事件是否正常；与 TTS 播放互斥；唤醒词检测是否能在 ASR 停止后恢复。
- 构建与安装：
  - 按工作区规则运行 `dicio-android/run.sh` 验证构建安装。

## 回滚与演进
- 回滚：若出现不可接受问题，可在 `SttInputDeviceWrapper` 中恢复原映射，继续由 `EnhancedFloatingWindowService` 直接驱动 `AsrHandler`。
- 演进：
  - 合并 `SherpaOnnxSimulateInputDevice` 与 `AsrHandler` 的重复逻辑（两者均为离线识别 + VAD）为一个统一实现，减少维护成本。
  - 如需要枚举可见化，新增 `INPUT_DEVICE_ASR_HANDLER` 并在设置 UI 提供选择；此为“大改动”，需事先确认。

## 结论
- `AsrHandler` 完全可以通过“适配器模式”被纳入现有 `InputDevice` 流程，且第一阶段只需在注入层增加映射与实现一个轻量设备适配器即可；
- 后续再逐步将服务与 UI 从直接依赖 `AsrHandler` 回归到统一的 `SttInputDevice`/`InputEvent`/`SttState` 抽象，达到架构统一与可扩展性提升。