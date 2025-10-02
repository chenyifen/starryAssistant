# Dicio-Android WebSocket 集成设计文档

**文档编号**: 31  
**创建日期**: 2025-01-02  
**版本**: v1.0  
**目标**: 在 dicio-android 中集成 WebSocket 支持，实现在线 ASR、TTS 和意图识别功能

---

## 一、概述

本文档描述了在 dicio-android 中新增的 WebSocket 功能，使其能够连接到远程服务器（如 xiaozhi-server）进行在线语音识别、语音合成和意图识别。

### 1.1 设计目标

- ✅ **协议抽象**：定义统一的 Protocol 接口，支持多种通信协议
- ✅ **在线 ASR**：通过 WebSocket 实现实时语音识别
- ✅ **在线 TTS**：通过 WebSocket 接收服务器合成的语音
- ✅ **混合模式**：支持离线和在线模式无缝切换
- ⏳ **意图识别**：支持服务器端的 LLM 意图识别
- ⏳ **MCP 工具**：支持 MCP 协议调用远程工具

### 1.2 参考架构

本实现参考了 `py-xiaozhi` 和 `xiaozhi-esp32-server` 的架构设计：

```
py-xiaozhi (Python 客户端)
├── protocols/
│   ├── protocol.py (协议基类)
│   └── websocket_protocol.py (WebSocket 实现)
├── application.py (应用核心)
└── audio_codecs/ (音频编解码)

xiaozhi-esp32-server (Python 服务端)
└── core/
    ├── websocket_server.py (WebSocket 服务器)
    └── connection.py (连接处理器)
```

## 二、核心组件

### 2.1 协议层 (`org.stypox.dicio.io.net`)

#### 2.1.1 Protocol 接口

```kotlin
interface Protocol {
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun sendText(message: String)
    suspend fun sendAudio(audioData: ByteArray)
    fun onTextMessage(callback: (String) -> Unit)
    fun onAudioMessage(callback: (ByteArray) -> Unit)
    fun onNetworkError(callback: (String) -> Unit)
    fun onConnectionStateChanged(callback: (Boolean, String) -> Unit)
}
```

**设计特点**：
- 📡 异步操作使用 Kotlin Coroutines
- 🔄 StateFlow 管理连接状态
- 📨 回调机制处理服务器消息
- 🔌 支持文本和二进制消息

#### 2.1.2 WebSocketProtocol 实现

```kotlin
class WebSocketProtocol(
    private val serverUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val clientId: String
) : Protocol
```

**核心功能**：
1. **连接管理**
   - OkHttp WebSocket 客户端
   - 30 秒心跳保持连接
   - 自动重连机制（可配置）

2. **Hello 握手**
   ```json
   {
     "type": "hello",
     "version": 1,
     "features": { "mcp": true },
     "transport": "websocket",
     "audio_params": {
       "format": "opus",
       "sample_rate": 16000,
       "channels": 1,
       "frame_duration": 20
     }
   }
   ```

3. **消息路由**
   - 文本消息：JSON 格式，包含 `type` 字段
   - 音频消息：二进制格式（PCM 或 Opus 编码）

### 2.2 输入设备层 (`org.stypox.dicio.io.input.websocket`)

#### 2.2.1 WebSocketInputDevice

```kotlin
class WebSocketInputDevice(
    @ApplicationContext private val appContext: Context,
    private val serverUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val clientId: String
) : SttInputDevice
```

**核心功能**：
1. **音频采集**
   - AudioRecord 采集麦克风音频
   - 16kHz 单声道 PCM16 格式
   - 20ms 帧大小（640 samples）

2. **实时发送**
   - 采集的音频实时发送到服务器
   - 通过 WebSocket 二进制消息传输

3. **识别结果接收**
   ```json
   {
     "type": "stt",
     "text": "识别的文本",
     "is_final": true
   }
   ```

4. **状态管理**
   - NotAvailable → Connecting → Available → Listening
   - 通过 StateFlow 通知 UI 更新

### 2.3 输出设备层 (`org.stypox.dicio.io.speech`)

#### 2.3.1 WebSocketTtsSpeechDevice

```kotlin
class WebSocketTtsSpeechDevice(
    private val context: Context,
    private val protocol: WebSocketProtocol
) : SpeechOutputDevice
```

**核心功能**：
1. **TTS 请求**
   ```json
   {
     "type": "tts_request",
     "text": "需要合成的文本"
   }
   ```

2. **音频流接收**
   - 服务器发送 TTS 状态消息（start/stop/sentence_start）
   - 接收 PCM16 音频数据（24kHz 单声道）
   - 通过 AudioTrack 实时播放

3. **状态同步**
   ```json
   {
     "type": "tts",
     "state": "start" | "stop" | "sentence_start",
     "text": "当前句子（仅 sentence_start）"
   }
   ```

4. **缓冲管理**
   - 音频数据缓冲队列
   - 异步播放避免阻塞
   - 支持停止和清空缓冲

## 三、消息协议

### 3.1 消息类型

| 类型 | 方向 | 描述 |
|-----|-----|-----|
| `hello` | 双向 | 连接握手 |
| `tts` | S→C | TTS 状态消息 |
| `stt` | S→C | STT 识别结果 |
| `llm` | S→C | LLM 响应（意图识别）|
| `mcp` | S→C | MCP 工具调用 |
| `command` | C→S | 客户端命令 |
| `tts_request` | C→S | TTS 合成请求 |

> S→C: Server to Client  
> C→S: Client to Server

### 3.2 命令消息示例

#### 开始监听
```json
{
  "type": "command",
  "action": "start_listening",
  "mode": "auto_stop"
}
```

#### 停止监听
```json
{
  "type": "command",
  "action": "stop_listening"
}
```

#### 停止 TTS
```json
{
  "type": "command",
  "action": "stop_tts"
}
```

## 四、集成点

### 4.1 与现有架构的集成

```
EnhancedFloatingWindowService (入口服务)
├── SttInputDeviceWrapper
│   ├── VoskInputDevice (离线)
│   ├── SenseVoiceInputDevice (离线)
│   └── WebSocketInputDevice (在线) ← 新增
├── SpeechOutputDeviceWrapper
│   ├── SherpaOnnxTtsSpeechDevice (离线)
│   └── WebSocketTtsSpeechDevice (在线) ← 新增
└── SkillEvaluator
    └── (将来支持在线意图识别)
```

### 4.2 配置管理

需要添加到 `UserSettings.proto`:

```protobuf
message UserSettings {
  // ... 现有字段 ...
  
  // WebSocket 配置
  bool use_online_asr = 20;
  bool use_online_tts = 21;
  string websocket_url = 22;
  string access_token = 23;
  string device_id = 24;
  string client_id = 25;
}
```

### 4.3 设备构建逻辑

在 `SttInputDeviceWrapper` 中添加：

```kotlin
INPUT_DEVICE_WEBSOCKET -> {
    Log.d(TAG, "   🌐 创建 WebSocketInputDevice")
    WebSocketInputDevice(
        appContext,
        userSettings.websocketUrl,
        userSettings.accessToken,
        userSettings.deviceId,
        userSettings.clientId
    )
}
```

## 五、数据流

### 5.1 ASR 识别流程

```
用户说话
  ↓
AudioRecord 采集音频 (16kHz PCM16)
  ↓
WebSocketInputDevice 实时发送
  ↓
WebSocket 二进制消息
  ↓
服务器 ASR 识别
  ↓
服务器发送 STT 消息 (JSON)
  ↓
WebSocketInputDevice 接收并转换为 InputEvent
  ↓
SkillEvaluator 处理
```

### 5.2 TTS 合成流程

```
技能生成文本输出
  ↓
WebSocketTtsSpeechDevice.speak()
  ↓
发送 tts_request 消息
  ↓
服务器接收并开始 TTS 合成
  ↓
服务器发送 TTS start 消息
  ↓
服务器流式发送 PCM 音频
  ↓
WebSocketTtsSpeechDevice 缓冲并播放
  ↓
服务器发送 TTS stop 消息
  ↓
播放完成，执行回调
```

## 六、优势与特点

### 6.1 与 py-xiaozhi 的对比

| 特性 | py-xiaozhi | dicio-android (本实现) |
|-----|-----------|---------------------|
| 编程语言 | Python | Kotlin |
| 异步框架 | asyncio | Kotlin Coroutines |
| WebSocket 库 | websockets | OkHttp |
| 音频编码 | Opus | PCM16 (Opus 待实现) |
| 协议抽象 | Protocol 基类 | Protocol 接口 |
| 状态管理 | Event/Callback | StateFlow |
| 消息格式 | JSON | JSON |

### 6.2 技术亮点

1. **Kotlin Coroutines**
   - 所有网络和 I/O 操作都是异步的
   - 使用 `suspend` 函数避免回调地狱
   - 结构化并发管理资源生命周期

2. **StateFlow 状态管理**
   - 响应式状态更新
   - UI 自动响应状态变化
   - 线程安全的状态共享

3. **模块化设计**
   - Protocol 接口支持未来扩展（MQTT、gRPC 等）
   - InputDevice 和 SpeechDevice 遵循现有架构
   - 易于集成和测试

## 七、待完成功能

### 7.1 优先级 1（核心功能）
- [ ] **Opus 音频编解码**：集成 libopus 进行音频压缩，减少带宽
- [ ] **SttInputDeviceWrapper 集成**：添加 WebSocket 选项
- [ ] **SpeechOutputDeviceWrapper 集成**：添加 WebSocket 选项
- [ ] **配置界面**：添加 WebSocket 服务器配置 UI

### 7.2 优先级 2（增强功能）
- [ ] **在线意图识别**：支持服务器端 LLM 意图识别
- [ ] **MCP 工具调用**：支持远程工具调用（如查询天气、设置提醒等）
- [ ] **连接状态 UI**：显示 WebSocket 连接状态
- [ ] **自动重连**：网络中断后自动重连

### 7.3 优先级 3（优化功能）
- [ ] **音频缓冲优化**：减少延迟和卡顿
- [ ] **错误恢复机制**：更完善的异常处理
- [ ] **性能监控**：添加连接质量和延迟监控
- [ ] **协议版本协商**：支持多版本协议兼容

## 八、使用示例

### 8.1 初始化 WebSocket 连接

```kotlin
val protocol = WebSocketProtocol(
    serverUrl = "wss://your-server.com/ws",
    accessToken = "your-access-token",
    deviceId = "your-device-id",
    clientId = "your-client-id"
)

// 连接到服务器
scope.launch {
    val connected = protocol.connect()
    if (connected) {
        Log.d(TAG, "✅ 连接成功")
    }
}
```

### 8.2 创建在线 ASR 输入设备

```kotlin
val inputDevice = WebSocketInputDevice(
    appContext = context,
    serverUrl = "wss://your-server.com/ws",
    accessToken = "your-token",
    deviceId = "device-123",
    clientId = "client-123"
)

// 开始监听
inputDevice.startListening { event ->
    when (event) {
        is InputEvent.Partial -> Log.d(TAG, "部分结果: ${event.utterance}")
        is InputEvent.Final -> Log.d(TAG, "最终结果: ${event.utterances}")
        is InputEvent.Error -> Log.e(TAG, "错误: ${event.throwable}")
        InputEvent.None -> Log.d(TAG, "无语音")
    }
}
```

### 8.3 创建在线 TTS 输出设备

```kotlin
val ttsDevice = WebSocketTtsSpeechDevice(
    context = context,
    protocol = protocol
)

// 合成语音
ttsDevice.speak("你好，我是小智语音助手")

// 等待播放完成后执行操作
ttsDevice.runWhenFinishedSpeaking {
    Log.d(TAG, "播放完成")
}
```

## 九、测试指南

### 9.1 单元测试

```kotlin
@Test
fun testWebSocketConnection() = runBlocking {
    val protocol = WebSocketProtocol(
        serverUrl = TEST_SERVER_URL,
        accessToken = TEST_TOKEN,
        deviceId = "test-device",
        clientId = "test-client"
    )
    
    val connected = protocol.connect()
    assertTrue(connected)
    
    protocol.disconnect()
}
```

### 9.2 集成测试

1. 启动 xiaozhi-esp32-server 测试服务器
2. 配置 dicio-android 连接到测试服务器
3. 测试 ASR 识别准确性
4. 测试 TTS 播放流畅性
5. 测试断线重连机制

## 十、总结

本次 WebSocket 集成为 dicio-android 带来了以下能力：

1. ✅ **在线 ASR**：通过服务器进行更准确的语音识别
2. ✅ **在线 TTS**：使用服务器高质量的 TTS 合成
3. ✅ **协议抽象**：为未来扩展其他协议（MQTT、gRPC）打下基础
4. ✅ **架构一致性**：与现有离线模式无缝集成

后续工作将聚焦于 Opus 编解码、MCP 工具集成和用户界面优化。

---

**参考文档**：
- [py-xiaozhi 源码](https://github.com/huangjunsen0406/py-xiaozhi)
- [xiaozhi-esp32-server 源码](https://github.com/78/xiaozhi-esp32)
- [dicio-android 架构设计](./30-架构优化设计-服务化与混合模式.md)

