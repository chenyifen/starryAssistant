# Dicio Android MCP协议微服务架构设计

## 📋 目录

1. [架构概览](#架构概览)
2. [核心设计原则](#核心设计原则)
3. [MCP协议通信架构](#mcp协议通信架构)
4. [服务模块设计](#服务模块设计)
5. [云端服务集成](#云端服务集成)
6. [项目结构规划](#项目结构规划)
7. [实现计划](#实现计划)
8. [性能优化](#性能优化)
9. [安全机制](#安全机制)
10. [部署方案](#部署方案)

---

## 🏗️ 架构概览

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Dicio语音助手客户端                        │
│              (主UI应用 + 服务协调器)                          │
└─────────────────┬───────────────────────────────────────────┘
                  │ MCP协议通信
┌─────────────────┴───────────────────────────────────────────┐
│                服务注册中心 + 服务路由器                        │
│          (ServiceRegistry + ServiceRouter)                 │
└─┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┘
  │     │     │     │     │     │     │     │     │     │
  v     v     v     v     v     v     v     v     v     v
┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐
│KWS│ │KWS│ │ASR│ │ASR│ │TTS│ │TTS│ │NLU│ │CMD│ │AUD│ │MOD│
│本地│ │本地│ │本地│ │云端│ │本地│ │云端│ │云端│ │混合│ │   │ │   │
└─┬─┘ └───┘ └───┘ └─┬─┘ └───┘ └─┬─┘ └─┬─┘ └─┬─┘ └───┘ └───┘
  │                 │             │     │     │
  v                 v             v     v     v
┌───┐            ┌─────┐       ┌─────┐ ┌────────────┐
│硬件│            │ API │       │ API │ │   混合API  │
│设备│            │网关 │       │网关 │ │   网关     │
└───┘            └─────┘       └─────┘ └────────────┘
                    │             │         │
                    v             v         v
              ┌──────────┐  ┌──────────┐ ┌──────────┐
              │云端ASR   │  │云端TTS   │ │云端NLU   │
              │(百度/科大)│  │(阿里/腾讯)│ │(OpenAI等)│
              └──────────┘  └──────────┘ └──────────┘
```

### 设计理念

- **模块化**: 每个功能独立为一个服务模块
- **可扩展**: 支持新服务的动态注册和发现
- **高可用**: 多实例部署，故障自动切换
- **混合云**: 本地+云端服务无缝结合
- **性能优化**: 资源共享，智能调度
- **安全第一**: 端到端加密，权限控制

---

## 🎯 核心设计原则

### 1. 服务抽象原则
- 统一的服务接口定义
- 透明的本地/云端服务切换
- 标准化的数据格式和协议

### 2. 高可用原则
- 服务健康监控和自动恢复
- 多实例负载均衡
- 优雅降级机制

### 3. 性能优化原则
- 资源池化和复用
- 智能缓存策略
- 异步非阻塞通信

### 4. 安全原则
- 最小权限原则
- 数据加密传输
- 服务间访问控制

---

## 🔗 MCP协议通信架构

### MCP消息定义

```kotlin
// MCP消息基类
sealed class MCPMessage {
    data class Request(
        val id: String,
        val method: String,
        val params: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    ) : MCPMessage()
    
    data class Response(
        val id: String,
        val result: Any?,
        val error: MCPError?,
        val timestamp: Long = System.currentTimeMillis()
    ) : MCPMessage()
    
    data class Notification(
        val method: String,
        val params: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    ) : MCPMessage()
}

data class MCPError(
    val code: Int,
    val message: String,
    val data: Any? = null
)
```

### 服务注册中心

```kotlin
@Component
class ServiceRegistry {
    private val services = ConcurrentHashMap<String, ServiceDescriptor>()
    private val serviceHealth = ConcurrentHashMap<String, HealthStatus>()
    
    suspend fun registerService(descriptor: ServiceDescriptor): Result<Unit>
    suspend fun discoverServices(type: ServiceType): List<ServiceDescriptor>
    suspend fun getService(serviceId: String): ServiceDescriptor?
    suspend fun unregisterService(serviceId: String): Result<Unit>
    
    fun startHealthCheck() // 定期健康检查
}

data class ServiceDescriptor(
    val id: String,
    val name: String,
    val type: ServiceType,
    val version: String,
    val endpoint: String,
    val capabilities: Set<String>,
    val metadata: Map<String, String>
)
```

### MCP客户端实现

```kotlin
class MCPClient(private val serviceConnection: ServiceConnection) {
    suspend fun call(method: String, params: Map<String, Any>): Result<Any>
    suspend fun notify(method: String, params: Map<String, Any>): Result<Unit>
    fun subscribe(method: String): Flow<MCPMessage.Notification>
}
```

---

## 🧩 服务模块设计

### 1. 语音唤醒服务 (KWS)

#### 服务接口定义

```kotlin
interface KWSService {
    suspend fun startListening(config: KWSConfig): Result<Unit>
    suspend fun stopListening(): Result<Unit>
    suspend fun setWakeWord(model: WakeWordModel): Result<Unit>
    suspend fun getStatus(): KWSStatus
    fun onWakeWordDetected(): Flow<WakeWordEvent>
}

data class KWSConfig(
    val model: WakeWordModel,
    val sensitivity: Float = 0.5f,
    val continuousMode: Boolean = true
)

data class WakeWordEvent(
    val timestamp: Long,
    val confidence: Float,
    val audioBuffer: ByteArray? = null
)
```

#### 支持的唤醒引擎

1. **OpenWakeWord引擎**
   - 支持自定义唤醒词
   - 高准确率，低误触发
   - 离线运行

2. **Sherpa-ONNX KWS引擎**
   - 基于ONNX Runtime
   - 多语言支持
   - 轻量级部署

3. **HeyDicio引擎**
   - 简单关键词匹配
   - 快速响应
   - 低资源消耗

#### 服务实现示例

```kotlin
@AndroidEntryPoint
class KWSService : BaseVoiceService(), KWSServiceInterface {
    
    @Inject lateinit var openWakeWordEngine: OpenWakeWordEngine
    @Inject lateinit var sherpaKWSEngine: SherpaKWSEngine
    @Inject lateinit var resourceManager: ResourceManager
    
    private var currentEngine: WakeWordEngine? = null
    private var isListening = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun getServiceId() = "kws-primary"
    override fun getServiceType() = ServiceType.KWS
    override fun getCapabilities() = setOf(
        "openwakeword", "sherpa-kws", "custom-models", "streaming"
    )
    
    override suspend fun startListening(config: KWSConfig): Result<Unit> {
        return try {
            // 1. 获取音频资源
            val audioResource = resourceManager.acquireAudioResource(getServiceId())
                .getOrThrow()
            
            // 2. 选择并初始化引擎
            currentEngine = selectEngine(config.model)
            currentEngine?.initialize(config)
            
            // 3. 开始监听
            isListening.set(true)
            scope.launch {
                listenForWakeWord(audioResource, config)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun listenForWakeWord(
        audioResource: AudioResource, 
        config: KWSConfig
    ) {
        val audioRecord = audioResource.createAudioRecord()
        val buffer = ShortArray(FRAME_SIZE)
        
        while (isListening.get() && !Thread.currentThread().isInterrupted) {
            try {
                val samplesRead = audioRecord.read(buffer, 0, buffer.size)
                if (samplesRead > 0) {
                    val detected = currentEngine?.processFrame(buffer) ?: false
                    if (detected) {
                        handleWakeWordDetected(config)
                    }
                }
                yield() // 让出CPU时间
            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error", e)
                break
            }
        }
    }
    
    companion object {
        private const val TAG = "KWSService"
        private const val FRAME_SIZE = 1024
    }
}
```

### 2. 语音识别服务 (ASR)

#### 服务接口定义

```kotlin
interface ASRService {
    suspend fun startRecognition(config: ASRConfig): Result<String>
    suspend fun processAudioStream(audioStream: Flow<ByteArray>): Flow<ASRResult>
    suspend fun processAudioFile(audioData: ByteArray): Result<ASRResult>
    suspend fun stopRecognition(): Result<Unit>
    suspend fun getAvailableModels(): List<ASRModel>
}

data class ASRConfig(
    val model: ASRModel,
    val language: String,
    val streaming: Boolean = true,
    val maxDuration: Long = 30_000L
)

data class ASRResult(
    val text: String,
    val confidence: Float,
    val isPartial: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
```

#### 支持的ASR引擎

1. **Vosk引擎**
   - 完全离线
   - 多语言支持
   - 实时流式识别

2. **SenseVoice引擎**
   - 高精度识别
   - 中文优化
   - 快速响应

3. **Sherpa-ONNX ASR引擎**
   - 基于ONNX Runtime
   - 轻量级部署
   - 多模型支持

4. **云端ASR服务**
   - 百度语音识别
   - 科大讯飞
   - 阿里云ASR
   - 腾讯云ASR

### 3. 语音合成服务 (TTS)

#### 服务接口定义

```kotlin
interface TTSService {
    suspend fun synthesize(text: String, config: TTSConfig): Result<ByteArray>
    suspend fun synthesizeStreaming(text: String, config: TTSConfig): Flow<ByteArray>
    suspend fun play(text: String, config: TTSConfig): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun getAvailableVoices(): List<TTSVoice>
}

data class TTSConfig(
    val voice: TTSVoice,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f
)
```

#### 支持的TTS引擎

1. **Android系统TTS**
   - 系统集成
   - 多语言支持
   - 标准化接口

2. **Sherpa-ONNX TTS**
   - 离线合成
   - 高质量音频
   - 自定义音色

3. **PiperTTS**
   - 开源解决方案
   - 快速合成
   - 多音色支持

4. **云端TTS服务**
   - 阿里云TTS
   - 腾讯云TTS
   - 百度TTS
   - 微软TTS

### 4. 自然语言理解服务 (NLU)

#### 服务接口定义

```kotlin
interface NLUService {
    suspend fun parseIntent(text: String, context: ConversationContext): Result<Intent>
    suspend fun extractEntities(text: String): List<Entity>
    suspend fun updateContext(context: ConversationContext): Result<Unit>
    suspend fun getAvailableSkills(): List<SkillInfo>
}

data class Intent(
    val skillId: String,
    val action: String,
    val entities: Map<String, Any>,
    val confidence: Float
)
```

#### NLU实现方案

1. **本地规则引擎**
   - 基于现有Dicio技能系统
   - 快速响应
   - 隐私保护

2. **云端AI服务**
   - OpenAI GPT
   - 百度UNIT
   - 科大讯飞AIUI
   - 微软LUIS

### 5. 指令执行服务 (CMD)

#### 服务接口定义

```kotlin
interface CMDService {
    suspend fun executeCommand(command: Command): Result<CommandResult>
    suspend fun getCommandStatus(commandId: String): CommandStatus
    suspend fun cancelCommand(commandId: String): Result<Unit>
    suspend fun getAvailableCommands(): List<CommandInfo>
}

data class Command(
    val id: String,
    val type: CommandType,
    val parameters: Map<String, Any>,
    val timeout: Long = 30_000L
)
```

---

## 🌐 云端服务集成

### 服务提供者抽象

```kotlin
interface ServiceProvider {
    val providerId: String
    val providerType: ProviderType
    val capabilities: Set<String>
    val metadata: Map<String, Any>
    
    suspend fun initialize(config: ProviderConfig): Result<Unit>
    suspend fun healthCheck(): HealthStatus
    suspend fun destroy(): Result<Unit>
}

enum class ProviderType {
    LOCAL,      // 本地服务
    CLOUD,      // 云端服务
    HYBRID      // 混合服务（本地+云端）
}
```

### 云端服务基类

```kotlin
abstract class CloudServiceProvider(
    override val providerId: String,
    private val apiConfig: CloudAPIConfig
) : ServiceProvider {
    
    override val providerType = ProviderType.CLOUD
    
    protected val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(apiConfig.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(apiConfig.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(apiConfig.writeTimeout, TimeUnit.SECONDS)
            .addInterceptor(AuthenticationInterceptor(apiConfig))
            .addInterceptor(RetryInterceptor(apiConfig.maxRetries))
            .addInterceptor(LoggingInterceptor())
            .build()
    }
    
    protected suspend fun <T> makeApiCall(
        call: suspend () -> T,
        fallback: (suspend () -> T)? = null
    ): Result<T> {
        return try {
            val result = call()
            Result.success(result)
        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException,
                is ConnectException,
                is UnknownHostException -> {
                    // 网络错误，尝试降级
                    fallback?.let { 
                        try {
                            Result.success(it())
                        } catch (fallbackError: Exception) {
                            Result.failure(fallbackError)
                        }
                    } ?: Result.failure(NetworkException("网络连接失败", e))
                }
                else -> Result.failure(e)
            }
        }
    }
}
```

### 云端API配置

```kotlin
data class CloudAPIConfig(
    val baseUrl: String,
    val apiKey: String,
    val secretKey: String? = null,
    val connectTimeout: Long = 30L,
    val readTimeout: Long = 30L,
    val writeTimeout: Long = 30L,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000L,
    val batchSize: Int = 10,
    val enableCache: Boolean = true,
    val cacheTimeoutMs: Long = 300_000L // 5分钟
)
```

### 服务路由和智能选择

```kotlin
@Singleton
class ServiceRouter @Inject constructor(
    private val serviceRegistry: ServiceRegistry,
    private val configManager: ConfigManager,
    private val networkMonitor: NetworkMonitor
) {
    
    suspend fun findBestService(
        serviceType: ServiceType,
        requirements: ServiceRequirements = ServiceRequirements()
    ): ServiceDescriptor? {
        val availableServices = serviceRegistry.getServicesByType(serviceType)
        
        return when (requirements.priority) {
            ServicePriority.PERFORMANCE -> selectByPerformance(availableServices)
            ServicePriority.ACCURACY -> selectByAccuracy(availableServices)
            ServicePriority.OFFLINE -> selectOfflineFirst(availableServices)
            ServicePriority.COST -> selectByCost(availableServices)
            ServicePriority.AUTO -> selectAutomatically(availableServices, requirements)
        }
    }
    
    private suspend fun selectAutomatically(
        services: List<ServiceDescriptor>,
        requirements: ServiceRequirements
    ): ServiceDescriptor? {
        val networkAvailable = networkMonitor.isNetworkAvailable()
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()
        
        return when {
            // 网络不可用，优选本地服务
            !networkAvailable -> {
                services.filter { it.type == ProviderType.LOCAL }.firstOrNull()
            }
            
            // 电量低且未充电，优选本地服务
            batteryLevel < 20 && !isCharging -> {
                services.filter { it.type == ProviderType.LOCAL }.firstOrNull()
            }
            
            // 需要高准确度，优选云端服务
            requirements.accuracy > 0.9f -> {
                services.filter { it.type == ProviderType.CLOUD }
                    .maxByOrNull { it.metadata["accuracy"] as? Float ?: 0.0f }
            }
            
            // 需要低延迟，优选本地服务
            requirements.maxLatency < 1000L -> {
                services.filter { it.type == ProviderType.LOCAL }.firstOrNull()
            }
            
            // 默认情况：混合策略
            else -> {
                services.maxByOrNull { calculateServiceScore(it, requirements) }
            }
        }
    }
}
```

---

## 📁 项目结构规划

```
dicio-android/
├── app/                                    # 主UI应用
│   ├── src/main/kotlin/org/stypox/dicio/
│   │   ├── coordinator/                    # 服务协调器
│   │   │   ├── VoiceAssistantCoordinator.kt
│   │   │   ├── VoiceFlowManager.kt
│   │   │   └── ServiceOrchestrator.kt
│   │   ├── flow/                          # 流程管理
│   │   │   ├── VoiceInteractionFlow.kt
│   │   │   ├── StateManager.kt
│   │   │   └── FlowTransition.kt
│   │   ├── ui/                            # UI界面
│   │   │   ├── home/
│   │   │   ├── settings/
│   │   │   └── components/
│   │   └── mcp/                           # MCP客户端
│   │       ├── MCPClient.kt
│   │       ├── MessageHandler.kt
│   │       └── ConnectionManager.kt
│   └── build.gradle.kts
│
├── mcp-core/                              # MCP协议核心库
│   ├── src/main/kotlin/org/stypox/dicio/mcp/
│   │   ├── protocol/                      # 协议定义
│   │   │   ├── MCPMessage.kt
│   │   │   ├── MCPError.kt
│   │   │   └── ServiceContract.kt
│   │   ├── client/                        # 客户端实现
│   │   │   ├── MCPClient.kt
│   │   │   ├── ConnectionPool.kt
│   │   │   └── CallManager.kt
│   │   ├── server/                        # 服务端实现
│   │   │   ├── MCPServer.kt
│   │   │   ├── RequestHandler.kt
│   │   │   └── ResponseManager.kt
│   │   └── security/                      # 安全机制
│   │       ├── AuthenticationManager.kt
│   │       ├── EncryptionUtils.kt
│   │       └── AccessController.kt
│   └── build.gradle.kts
│
├── service-registry/                      # 服务注册中心
│   ├── src/main/kotlin/org/stypox/dicio/registry/
│   │   ├── ServiceRegistry.kt
│   │   ├── ServiceRouter.kt
│   │   ├── HealthChecker.kt
│   │   └── LoadBalancer.kt
│   └── build.gradle.kts
│
├── kws-service/                          # 语音唤醒服务
│   ├── src/main/kotlin/org/stypox/dicio/kws/
│   │   ├── openwakeword/                 # OpenWakeWord实现
│   │   │   ├── OpenWakeWordEngine.kt
│   │   │   ├── ModelManager.kt
│   │   │   └── AudioProcessor.kt
│   │   ├── sherpa/                       # Sherpa-ONNX KWS实现
│   │   │   ├── SherpaKWSEngine.kt
│   │   │   ├── ONNXModelLoader.kt
│   │   │   └── KWSPreprocessor.kt
│   │   └── service/                      # 服务实现
│   │       ├── KWSService.kt
│   │       ├── WakeWordDetector.kt
│   │       └── AudioStreamManager.kt
│   └── build.gradle.kts
│
├── asr-service/                          # 语音识别服务
│   ├── src/main/kotlin/org/stypox/dicio/asr/
│   │   ├── vosk/                         # Vosk实现
│   │   │   ├── VoskEngine.kt
│   │   │   ├── VoskModelManager.kt
│   │   │   └── VoskRecognizer.kt
│   │   ├── sensevoice/                   # SenseVoice实现
│   │   │   ├── SenseVoiceEngine.kt
│   │   │   ├── AudioBuffer.kt
│   │   │   └── SenseVoiceModelManager.kt
│   │   ├── sherpa/                       # Sherpa-ONNX ASR实现
│   │   │   ├── SherpaASREngine.kt
│   │   │   ├── StreamingRecognizer.kt
│   │   │   └── BatchRecognizer.kt
│   │   ├── cloud/                        # 云端ASR实现
│   │   │   ├── BaiduASRProvider.kt
│   │   │   ├── XunfeiASRProvider.kt
│   │   │   ├── AlibabaASRProvider.kt
│   │   │   └── TencentASRProvider.kt
│   │   └── service/                      # 服务实现
│   │       ├── ASRService.kt
│   │       ├── RecognitionSession.kt
│   │       └── AudioStreamProcessor.kt
│   └── build.gradle.kts
│
├── tts-service/                          # 语音合成服务
│   ├── src/main/kotlin/org/stypox/dicio/tts/
│   │   ├── android/                      # Android TTS
│   │   │   ├── AndroidTTSEngine.kt
│   │   │   ├── SystemTTSWrapper.kt
│   │   │   └── VoiceManager.kt
│   │   ├── sherpa/                       # Sherpa-ONNX TTS
│   │   │   ├── SherpaTTSEngine.kt
│   │   │   ├── VoiceModelManager.kt
│   │   │   └── AudioSynthesizer.kt
│   │   ├── piper/                        # PiperTTS
│   │   │   ├── PiperTTSEngine.kt
│   │   │   ├── PhonemeTTSConverter.kt
│   │   │   └── ModelLoader.kt
│   │   ├── cloud/                        # 云端TTS实现
│   │   │   ├── AlibabaTTSProvider.kt
│   │   │   ├── TencentTTSProvider.kt
│   │   │   ├── BaiduTTSProvider.kt
│   │   │   └── MicrosoftTTSProvider.kt
│   │   └── service/                      # 服务实现
│   │       ├── TTSService.kt
│   │       ├── SynthesisManager.kt
│   │       └── AudioPlayer.kt
│   └── build.gradle.kts
│
├── nlu-service/                          # 意图理解服务
│   ├── src/main/kotlin/org/stypox/dicio/nlu/
│   │   ├── skills/                       # 技能定义
│   │   │   ├── SkillRegistry.kt
│   │   │   ├── SkillMatcher.kt
│   │   │   └── SkillExecutor.kt
│   │   ├── intent/                       # 意图识别
│   │   │   ├── IntentClassifier.kt
│   │   │   ├── EntityExtractor.kt
│   │   │   └── ContextManager.kt
│   │   ├── cloud/                        # 云端NLU实现
│   │   │   ├── OpenAINLUProvider.kt
│   │   │   ├── BaiduUNITProvider.kt
│   │   │   ├── XunfeiAIUIProvider.kt
│   │   │   └── MicrosoftLUISProvider.kt
│   │   └── service/                      # 服务实现
│   │       ├── NLUService.kt
│   │       ├── IntentParser.kt
│   │       └── ConversationManager.kt
│   └── build.gradle.kts
│
├── cmd-service/                          # 指令执行服务
│   ├── src/main/kotlin/org/stypox/dicio/cmd/
│   │   ├── system/                       # 系统指令
│   │   │   ├── SystemController.kt
│   │   │   ├── DeviceManager.kt
│   │   │   └── AppLauncher.kt
│   │   ├── skills/                       # 技能执行
│   │   │   ├── WeatherSkill.kt
│   │   │   ├── MusicSkill.kt
│   │   │   ├── TimerSkill.kt
│   │   │   └── SearchSkill.kt
│   │   ├── cloud/                        # 云端API调用
│   │   │   ├── WeatherAPIClient.kt
│   │   │   ├── NewsAPIClient.kt
│   │   │   └── SmartHomeController.kt
│   │   └── service/                      # 服务实现
│   │       ├── CMDService.kt
│   │       ├── CommandExecutor.kt
│   │       └── ResultFormatter.kt
│   └── build.gradle.kts
│
└── shared/                               # 共享库
    ├── models/                           # 数据模型
    │   ├── ServiceDescriptor.kt
    │   ├── ASRResult.kt
    │   ├── TTSConfig.kt
    │   └── Intent.kt
    ├── utils/                            # 工具类
    │   ├── AudioUtils.kt
    │   ├── NetworkUtils.kt
    │   ├── EncryptionUtils.kt
    │   └── PerformanceMonitor.kt
    └── build.gradle.kts
```

---

## 📋 实现计划

### 阶段1: 基础架构搭建 (2-3周)

#### 1.1 MCP协议核心库开发
- [ ] 消息协议定义
- [ ] 客户端/服务端基础框架
- [ ] 安全机制实现
- [ ] 单元测试编写

#### 1.2 服务注册中心开发
- [ ] 服务注册/发现机制
- [ ] 健康检查系统
- [ ] 负载均衡器
- [ ] 服务路由器

#### 1.3 基础服务框架
- [ ] BaseVoiceService抽象类
- [ ] 服务生命周期管理
- [ ] 资源管理器
- [ ] 配置管理系统

### 阶段2: 核心服务开发 (3-4周)

#### 2.1 语音唤醒服务 (KWS)
- [ ] OpenWakeWord引擎集成
- [ ] Sherpa-ONNX KWS引擎集成
- [ ] 音频流处理优化
- [ ] 多模型支持

#### 2.2 语音识别服务 (ASR)
- [ ] Vosk引擎集成
- [ ] SenseVoice引擎集成
- [ ] 流式识别实现
- [ ] 批量识别支持

#### 2.3 语音合成服务 (TTS)
- [ ] Android TTS集成
- [ ] Sherpa-ONNX TTS集成
- [ ] PiperTTS集成
- [ ] 音质优化

### 阶段3: 云端服务集成 (2-3周)

#### 3.1 云端ASR服务
- [ ] 百度语音识别API
- [ ] 科大讯飞API
- [ ] 阿里云ASR API
- [ ] 腾讯云ASR API

#### 3.2 云端NLU服务
- [ ] OpenAI GPT集成
- [ ] 百度UNIT API
- [ ] 科大讯飞AIUI API
- [ ] 微软LUIS API

#### 3.3 云端TTS服务
- [ ] 阿里云TTS API
- [ ] 腾讯云TTS API
- [ ] 百度TTS API
- [ ] 微软TTS API

### 阶段4: 主应用开发 (2-3周)

#### 4.1 服务协调器
- [ ] VoiceAssistantCoordinator实现
- [ ] 流程管理器开发
- [ ] 状态机实现
- [ ] 错误处理机制

#### 4.2 用户界面
- [ ] 主界面设计
- [ ] 设置页面
- [ ] 服务状态监控
- [ ] 性能仪表板

#### 4.3 配置和管理
- [ ] 服务配置界面
- [ ] 云端API密钥管理
- [ ] 权限管理
- [ ] 日志查看器

### 阶段5: 测试和优化 (2-3周)

#### 5.1 功能测试
- [ ] 单元测试完善
- [ ] 集成测试
- [ ] 端到端测试
- [ ] 性能测试

#### 5.2 性能优化
- [ ] 内存使用优化
- [ ] 启动时间优化
- [ ] 响应延迟优化
- [ ] 电池使用优化

#### 5.3 稳定性测试
- [ ] 长时间运行测试
- [ ] 压力测试
- [ ] 故障恢复测试
- [ ] 兼容性测试

---

## ⚡ 性能优化

### 1. 资源管理优化

```kotlin
class ServiceResourceManager {
    private val serviceInstances = ConcurrentHashMap<String, ServiceInstance>()
    private val resourcePools = ConcurrentHashMap<String, ResourcePool>()
    
    suspend fun getOrCreateService(serviceId: String): ServiceInstance {
        return serviceInstances.getOrPut(serviceId) {
            createServiceInstance(serviceId)
        }
    }
    
    suspend fun recycleIdleServices() {
        val currentTime = System.currentTimeMillis()
        val idleThreshold = 5 * 60 * 1000L // 5分钟
        
        serviceInstances.entries.removeAll { (serviceId, instance) ->
            if (currentTime - instance.lastAccessTime > idleThreshold) {
                try {
                    instance.destroy()
                    Log.d(TAG, "Recycled idle service: $serviceId")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to destroy service: $serviceId", e)
                    false
                }
            } else {
                false
            }
        }
    }
    
    fun optimizeMemoryUsage() {
        // 1. 清理模型缓存
        clearOldModelCache()
        
        // 2. 压缩音频缓冲区
        compressAudioBuffers()
        
        // 3. 释放不必要的资源
        releaseUnusedResources()
        
        // 4. 触发GC
        System.gc()
    }
}
```

### 2. 缓存策略

```kotlin
@Singleton
class MultiLevelCacheManager {
    
    // L1缓存：内存缓存
    private val memoryCache = LRUCache<String, Any>(maxSize = 100)
    
    // L2缓存：磁盘缓存
    private val diskCache = DiskLruCache.open(cacheDir, 1, 1, 50 * 1024 * 1024)
    
    // L3缓存：云端缓存
    private val cloudCache = CloudCacheClient()
    
    suspend fun <T> getOrCompute(
        key: String,
        type: Class<T>,
        computer: suspend () -> T
    ): T {
        // L1缓存查找
        memoryCache.get(key)?.let { 
            return it as T 
        }
        
        // L2缓存查找
        diskCache.get(key)?.let { snapshot ->
            val data = deserialize<T>(snapshot.getInputStream(0), type)
            memoryCache.put(key, data) // 回填L1缓存
            return data
        }
        
        // L3缓存查找（仅对特定类型）
        if (shouldUseCloudCache(type)) {
            cloudCache.get(key, type)?.let { data ->
                memoryCache.put(key, data) // 回填L1缓存
                diskCache.put(key, serialize(data)) // 回填L2缓存
                return data
            }
        }
        
        // 计算结果
        val result = computer()
        
        // 写入各级缓存
        memoryCache.put(key, result)
        diskCache.put(key, serialize(result))
        if (shouldUseCloudCache(type)) {
            cloudCache.put(key, result)
        }
        
        return result
    }
}
```

### 3. 音频处理优化

```kotlin
class OptimizedAudioProcessor {
    
    private val audioQueue = ArrayBlockingQueue<FloatArray>(10)
    private val processingPool = Executors.newFixedThreadPool(2)
    
    fun processAudioStream(inputStream: Flow<ByteArray>): Flow<ProcessedAudio> = flow {
        // 使用协程并行处理音频
        inputStream
            .buffer(capacity = 5) // 缓冲5个音频块
            .map { audioData ->
                async(Dispatchers.Default) {
                    processAudioChunk(audioData)
                }
            }
            .collect { deferredResult ->
                emit(deferredResult.await())
            }
    }
    
    private suspend fun processAudioChunk(audioData: ByteArray): ProcessedAudio {
        return withContext(Dispatchers.Default) {
            // 1. 降噪处理
            val denoisedAudio = denoiseAudio(audioData)
            
            // 2. 音量标准化
            val normalizedAudio = normalizeVolume(denoisedAudio)
            
            // 3. 特征提取
            val features = extractFeatures(normalizedAudio)
            
            ProcessedAudio(normalizedAudio, features)
        }
    }
    
    private fun denoiseAudio(audioData: ByteArray): ByteArray {
        // 使用Wiener滤波或谱减法进行降噪
        return AudioDenoiser.denoise(audioData)
    }
    
    private fun normalizeVolume(audioData: ByteArray): ByteArray {
        // 音量标准化到合适范围
        return AudioNormalizer.normalize(audioData, targetRMS = 0.1f)
    }
    
    private fun extractFeatures(audioData: ByteArray): AudioFeatures {
        // 提取MFCC、能量等特征
        return AudioFeatureExtractor.extract(audioData)
    }
}
```

### 4. 网络优化

```kotlin
class OptimizedNetworkClient {
    
    private val connectionPool = ConnectionPool(
        maxIdleConnections = 5,
        keepAliveDuration = 30,
        timeUnit = TimeUnit.SECONDS
    )
    
    private val httpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .addInterceptor(CompressionInterceptor())
        .addInterceptor(BatchingInterceptor())
        .addNetworkInterceptor(CacheInterceptor())
        .build()
    
    suspend fun batchApiCalls(requests: List<ApiRequest>): List<ApiResponse> {
        // 批量处理API请求
        return requests.chunked(10).flatMap { batch ->
            batch.map { request ->
                async { makeApiCall(request) }
            }.awaitAll()
        }
    }
    
    private suspend fun makeApiCall(request: ApiRequest): ApiResponse {
        return withContext(Dispatchers.IO) {
            val httpRequest = request.toOkHttpRequest()
            val response = httpClient.newCall(httpRequest).execute()
            ApiResponse.fromOkHttpResponse(response)
        }
    }
}
```

---

## 🔒 安全机制

### 1. 服务间认证

```kotlin
class ServiceAuthenticationManager {
    
    private val serviceTokens = ConcurrentHashMap<String, ServiceToken>()
    private val keyStore = AndroidKeyStore()
    
    suspend fun generateServiceToken(serviceId: String): ServiceToken {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val payload = "$serviceId:$timestamp:$nonce"
        
        val signature = keyStore.sign(payload)
        
        val token = ServiceToken(
            serviceId = serviceId,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature,
            expiresAt = timestamp + TOKEN_VALIDITY_MS
        )
        
        serviceTokens[serviceId] = token
        return token
    }
    
    suspend fun validateServiceToken(token: ServiceToken): Boolean {
        return try {
            // 检查过期时间
            if (token.expiresAt < System.currentTimeMillis()) {
                return false
            }
            
            // 验证签名
            val payload = "${token.serviceId}:${token.timestamp}:${token.nonce}"
            keyStore.verify(payload, token.signature)
        } catch (e: Exception) {
            Log.e(TAG, "Token validation failed", e)
            false
        }
    }
    
    companion object {
        private const val TOKEN_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24小时
        private const val TAG = "ServiceAuthenticationManager"
    }
}
```

### 2. 数据加密

```kotlin
class DataEncryptionManager {
    
    private val keyAlias = "DicioServiceKey"
    private val transformation = "AES/GCM/NoPadding"
    
    init {
        generateKeyIfNeeded()
    }
    
    private fun generateKeyIfNeeded() {
        if (!keyExists()) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    fun encrypt(data: ByteArray): EncryptedData {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val key = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val encryptedBytes = cipher.doFinal(data)
        val iv = cipher.iv
        
        return EncryptedData(
            data = encryptedBytes,
            iv = iv,
            algorithm = transformation
        )
    }
    
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val key = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(encryptedData.data)
    }
    
    private fun keyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            false
        }
    }
}

data class EncryptedData(
    val data: ByteArray,
    val iv: ByteArray,
    val algorithm: String
)
```

### 3. 权限控制

```kotlin
class ServicePermissionManager {
    
    private val permissions = mapOf(
        "kws-service" to setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.WAKE_LOCK"
        ),
        "asr-service" to setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
        ),
        "tts-service" to setOf(
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.INTERNET"
        ),
        "nlu-service" to setOf(
            "android.permission.INTERNET"
        ),
        "cmd-service" to setOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
    )
    
    private val serviceAccessMatrix = mapOf(
        "dicio-main" to setOf("kws-service", "asr-service", "tts-service", "nlu-service", "cmd-service"),
        "kws-service" to setOf("asr-service"),
        "asr-service" to setOf("nlu-service"),
        "nlu-service" to setOf("cmd-service"),
        "cmd-service" to setOf("tts-service")
    )
    
    fun checkServiceAccess(clientServiceId: String, targetServiceId: String): Boolean {
        return serviceAccessMatrix[clientServiceId]?.contains(targetServiceId) ?: false
    }
    
    fun checkPermissions(serviceId: String, context: Context): PermissionCheckResult {
        val requiredPermissions = permissions[serviceId] ?: emptySet()
        val deniedPermissions = mutableSetOf<String>()
        
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission)
            }
        }
        
        return PermissionCheckResult(
            allGranted = deniedPermissions.isEmpty(),
            grantedPermissions = requiredPermissions - deniedPermissions,
            deniedPermissions = deniedPermissions
        )
    }
}

data class PermissionCheckResult(
    val allGranted: Boolean,
    val grantedPermissions: Set<String>,
    val deniedPermissions: Set<String>
)
```

---

## 🚀 部署方案

### 1. 多APK构建配置

#### 主应用 (app/build.gradle.kts)
```kotlin
android {
    defaultConfig {
        applicationId = "org.stypox.dicio"
        minSdk = 24
        targetSdk = 34
        
        // 主应用配置
        manifestPlaceholders["appType"] = "main"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":mcp-core"))
    implementation(project(":service-registry"))
    implementation(project(":shared"))
    
    // UI相关依赖
    implementation(libs.androidx.compose.bom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    
    // 依赖注入
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
```

#### KWS服务 (kws-service/build.gradle.kts)
```kotlin
android {
    defaultConfig {
        applicationId = "org.stypox.dicio.kws"
        minSdk = 24
        targetSdk = 34
        
        // 服务专用配置
        manifestPlaceholders["serviceType"] = "kws"
        manifestPlaceholders["servicePriority"] = "high"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-kws-service.pro")
        }
    }
}

dependencies {
    implementation(project(":mcp-core"))
    implementation(project(":shared"))
    
    // KWS专用依赖
    implementation("ai.onnxruntime:onnxruntime-android:1.15.1")
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    
    // OpenWakeWord
    implementation("com.github.dscripka:openwakeword-android:1.0.0")
}
```

### 2. 服务清单配置

#### KWS服务清单 (kws-service/src/main/AndroidManifest.xml)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".KWSServiceApplication"
        android:process=":kws_service"
        android:exported="false">
        
        <service
            android:name=".KWSService"
            android:enabled="true"
            android:exported="true"
            android:permission="org.stypox.dicio.permission.VOICE_SERVICE">
            <intent-filter>
                <action android:name="org.stypox.dicio.action.KWS_SERVICE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            
            <meta-data
                android:name="service.type"
                android:value="kws" />
            <meta-data
                android:name="service.version"
                android:value="1.0.0" />
            <meta-data
                android:name="service.capabilities"
                android:value="openwakeword,sherpa-kws,custom-models" />
        </service>
    </application>
    
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
</manifest>
```

### 3. 自动化构建脚本

#### 构建脚本 (build-all-services.sh)
```bash
#!/bin/bash

set -e

echo "🚀 开始构建Dicio微服务架构"

# 构建顺序：依赖库 -> 服务 -> 主应用
MODULES=(
    "shared"
    "mcp-core" 
    "service-registry"
    "kws-service"
    "asr-service"
    "tts-service"
    "nlu-service"
    "cmd-service"
    "app"
)

BUILD_TYPE=${1:-debug}
PARALLEL_JOBS=${2:-4}

echo "📋 构建配置："
echo "   构建类型: $BUILD_TYPE"
echo "   并行任务: $PARALLEL_JOBS"
echo "   目标模块: ${MODULES[*]}"

# 清理之前的构建
echo "🧹 清理之前的构建..."
./gradlew clean

# 并行构建所有模块
echo "🔨 开始并行构建..."
for module in "${MODULES[@]}"; do
    (
        echo "📦 构建模块: $module"
        ./gradlew ":$module:assemble${BUILD_TYPE^}" \
            --parallel \
            --max-workers=$PARALLEL_JOBS \
            --build-cache \
            --configuration-cache
        echo "✅ 模块 $module 构建完成"
    ) &
done

# 等待所有构建完成
wait

echo "🎉 所有模块构建完成"

# 收集APK文件
echo "📦 收集构建产物..."
OUTPUT_DIR="build/outputs/apks"
mkdir -p "$OUTPUT_DIR"

for module in "${MODULES[@]}"; do
    if [ -d "$module/build/outputs/apk" ]; then
        cp -r "$module/build/outputs/apk/"* "$OUTPUT_DIR/"
        echo "   ✅ 收集 $module APK"
    fi
done

echo "🏁 构建完成！APK文件位于: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"
```

#### 部署脚本 (deploy-services.sh)
```bash
#!/bin/bash

set -e

ADB=${ADB:-adb}
DEVICE_ID=${1:-""}
INSTALL_TYPE=${2:-"replace"}

if [ -n "$DEVICE_ID" ]; then
    ADB="$ADB -s $DEVICE_ID"
fi

echo "📱 开始部署服务到设备"

# 检查设备连接
if ! $ADB devices | grep -q "device$"; then
    echo "❌ 未找到连接的Android设备"
    exit 1
fi

# APK安装顺序
APKS=(
    "shared-debug.apk"
    "mcp-core-debug.apk"
    "service-registry-debug.apk"
    "kws-service-debug.apk"
    "asr-service-debug.apk"
    "tts-service-debug.apk"
    "nlu-service-debug.apk"
    "cmd-service-debug.apk"
    "app-debug.apk"
)

OUTPUT_DIR="build/outputs/apks"

# 安装APK
for apk in "${APKS[@]}"; do
    if [ -f "$OUTPUT_DIR/$apk" ]; then
        echo "📦 安装: $apk"
        if [ "$INSTALL_TYPE" = "replace" ]; then
            $ADB install -r "$OUTPUT_DIR/$apk"
        else
            $ADB install "$OUTPUT_DIR/$apk"
        fi
        echo "   ✅ $apk 安装完成"
    else
        echo "   ⚠️  未找到: $apk"
    fi
done

# 启动主应用
echo "🚀 启动主应用"
$ADB shell am start -n org.stypox.dicio/.MainActivity

echo "🎉 部署完成！"
```

### 4. Docker容器化（可选）

#### Dockerfile
```dockerfile
FROM openjdk:11-jdk

# 安装Android SDK
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/tools:${ANDROID_SDK_ROOT}/platform-tools

# 安装构建依赖
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# 下载Android SDK
RUN mkdir -p ${ANDROID_SDK_ROOT} && \
    cd ${ANDROID_SDK_ROOT} && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip commandlinetools-linux-9477386_latest.zip && \
    rm commandlinetools-linux-9477386_latest.zip

# 设置工作目录
WORKDIR /workspace

# 复制项目文件
COPY . .

# 构建应用
RUN ./gradlew assembleDebug --no-daemon

# 输出目录
VOLUME ["/workspace/build/outputs"]

CMD ["./build-all-services.sh", "debug"]
```

---

## 📊 监控和维护

### 1. 性能监控

```kotlin
class ServicePerformanceMonitor @Inject constructor() {
    private val metrics = ConcurrentHashMap<String, ServiceMetrics>()
    
    fun recordServiceCall(
        serviceId: String,
        method: String,
        duration: Long,
        success: Boolean
    ) {
        val metric = metrics.getOrPut(serviceId) { ServiceMetrics(serviceId) }
        metric.recordCall(method, duration, success)
    }
    
    fun getServiceHealth(serviceId: String): ServiceHealth {
        val metric = metrics[serviceId] ?: return ServiceHealth.Unknown
        
        return ServiceHealth(
            serviceId = serviceId,
            avgResponseTime = metric.getAverageResponseTime(),
            successRate = metric.getSuccessRate(),
            memoryUsage = getServiceMemoryUsage(serviceId),
            cpuUsage = getServiceCpuUsage(serviceId),
            status = determineHealthStatus(metric)
        )
    }
    
    suspend fun generatePerformanceReport(): PerformanceReport {
        val allMetrics = metrics.values.map { it.generateReport() }
        return PerformanceReport(
            timestamp = System.currentTimeMillis(),
            serviceReports = allMetrics,
            systemOverview = generateSystemOverview()
        )
    }
}
```

### 2. 日志聚合

```kotlin
class LogAggregator {
    private val logBuffer = CircularBuffer<LogEntry>(capacity = 10000)
    
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            threadName = Thread.currentThread().name,
            serviceId = getCurrentServiceId()
        )
        
        logBuffer.add(entry)
        
        // 异步上传到远程日志服务
        if (shouldUploadLogs(level)) {
            uploadLogEntry(entry)
        }
    }
    
    fun exportLogs(filter: LogFilter): List<LogEntry> {
        return logBuffer.filter { entry ->
            filter.matches(entry)
        }
    }
}
```

### 3. 自动化测试

```kotlin
@RunWith(AndroidJUnit4::class)
class ServiceIntegrationTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var serviceRegistry: ServiceRegistry
    
    @Inject 
    lateinit var mcpClient: MCPClient
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun testVoiceInteractionFlow() = runTest {
        // 1. 启动KWS服务
        val kwsService = serviceRegistry.getService("kws-primary")
        assertNotNull(kwsService)
        
        val kwsStarted = mcpClient.call(
            kwsService!!.endpoint,
            "startListening",
            mapOf("sensitivity" to 0.6f)
        )
        assertTrue(kwsStarted.isSuccess)
        
        // 2. 模拟唤醒检测
        val wakeEvent = simulateWakeWordDetection()
        assertNotNull(wakeEvent)
        
        // 3. 启动ASR服务
        val asrService = serviceRegistry.getService("asr-primary")
        assertNotNull(asrService)
        
        val recognitionResult = simulateVoiceRecognition("今天天气怎么样")
        assertEquals("今天天气怎么样", recognitionResult.text)
        assertTrue(recognitionResult.confidence > 0.8f)
        
        // 4. NLU处理
        val nluService = serviceRegistry.getService("nlu-primary")
        val intent = mcpClient.call(
            nluService!!.endpoint,
            "parseIntent",
            mapOf("text" to recognitionResult.text)
        ).getOrThrow() as Intent
        
        assertEquals("weather", intent.skillId)
        assertEquals("query", intent.action)
        
        // 5. 指令执行
        val cmdService = serviceRegistry.getService("cmd-primary")
        val commandResult = mcpClient.call(
            cmdService!!.endpoint,
            "executeCommand",
            intent.toCommand().toMap()
        ).getOrThrow() as CommandResult
        
        assertNotNull(commandResult.response)
        assertTrue(commandResult.success)
    }
    
    @Test
    fun testServiceFailoverScenario() = runTest {
        // 测试服务故障切换场景
        val primaryASR = serviceRegistry.getService("asr-primary")
        val backupASR = serviceRegistry.getService("asr-backup")
        
        // 模拟主服务故障
        simulateServiceFailure(primaryASR!!.id)
        
        // 验证自动切换到备用服务
        delay(1000) // 等待故障检测
        
        val currentASR = serviceRegistry.findBestService(ServiceType.ASR)
        assertEquals(backupASR!!.id, currentASR!!.id)
    }
}
```

---

## 📚 总结

这个基于MCP协议的微服务架构设计为Dicio语音助手提供了：

### 🎯 核心优势

1. **高度模块化**: 每个功能独立为服务，便于开发、测试和维护
2. **可扩展性**: 支持动态添加新服务和功能
3. **高可用性**: 多实例部署，自动故障切换
4. **混合云架构**: 本地和云端服务无缝结合
5. **性能优化**: 智能资源管理和缓存策略
6. **安全可靠**: 端到端加密和访问控制

### 🚀 技术特色

1. **MCP协议通信**: 标准化的服务间通信协议
2. **智能服务路由**: 根据网络、电量、性能自动选择最佳服务
3. **多级缓存**: 内存、磁盘、云端三级缓存策略
4. **优雅降级**: 云端服务失败时自动降级到本地服务
5. **实时监控**: 全面的性能监控和健康检查

### 📋 实施建议

1. **分阶段实施**: 按照5个阶段逐步实现，确保每个阶段都有可交付的成果
2. **测试驱动**: 每个服务都要有完整的单元测试和集成测试
3. **性能优先**: 重点关注启动时间、响应延迟和内存使用
4. **用户体验**: 确保服务切换对用户透明，保持流畅体验
5. **文档完善**: 维护详细的API文档和部署指南

这个架构设计为Dicio项目的未来发展奠定了坚实的基础，支持从简单的语音助手发展为全功能的AI助手平台。
