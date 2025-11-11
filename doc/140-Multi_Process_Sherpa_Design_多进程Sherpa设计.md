# 多进程 Sherpa-ONNX 架构设计

## 目标
在同一个 APK 中使用两个不同版本的 sherpa-onnx：
- ASR 进程：使用 simulated_streaming_asr 版本
- TTS 进程：使用 包含TTS的完整版本

## 架构设计

### Module 结构
```
dicio-android/
├── app/                          # 主应用模块
│   └── UI + 业务逻辑
│
├── sherpa-asr/                   # ASR 模块（独立进程）
│   ├── src/main/
│   │   ├── java/
│   │   │   └── org/stypox/dicio/sherpa/asr/
│   │   │       ├── AsrService.kt           # Service运行在:asr进程
│   │   │       └── IAsrInterface.aidl      # IPC接口
│   │   ├── jniLibs/
│   │   │   └── arm64-v8a/
│   │   │       └── libsherpa-onnx-asr.so   # ASR版本的库
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
└── sherpa-tts/                   # TTS 模块（独立进程）
    ├── src/main/
    │   ├── java/
    │   │   └── org/stypox/dicio/sherpa/tts/
    │   │       ├── TtsService.kt           # Service运行在:tts进程
    │   │       └── ITtsInterface.aidl      # IPC接口
    │   ├── jniLibs/
    │   │   └── arm64-v8a/
    │   │       └── libsherpa-onnx-tts.so   # TTS版本的库
    │   └── AndroidManifest.xml
    └── build.gradle.kts
```

### 进程配置

**sherpa-asr/AndroidManifest.xml**:
```xml
<manifest package="org.stypox.dicio.sherpa.asr">
    <application>
        <service
            android:name=".AsrService"
            android:process=":asr"
            android:exported="false" />
    </application>
</manifest>
```

**sherpa-tts/AndroidManifest.xml**:
```xml
<manifest package="org.stypox.dicio.sherpa.tts">
    <application>
        <service
            android:name=".TtsService"
            android:process=":tts"
            android:exported="false" />
    </application>
</manifest>
```

### IPC 接口设计

**IAsrInterface.aidl**:
```aidl
interface IAsrInterface {
    void initialize();
    String recognize(in byte[] audioData);
    void release();
}
```

**ITtsInterface.aidl**:
```aidl
interface ITtsInterface {
    void initialize();
    byte[] synthesize(String text);
    void release();
}
```

### 主应用集成

```kotlin
// app module
class SherpaManager(context: Context) {
    private var asrService: IAsrInterface? = null
    private var ttsService: ITtsInterface? = null
    
    private val asrConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            asrService = IAsrInterface.Stub.asInterface(service)
            asrService?.initialize()
        }
        
        override fun onServiceDisconnected(name: ComponentName) {
            asrService = null
        }
    }
    
    fun bindServices() {
        // Bind ASR service
        val asrIntent = Intent(context, AsrService::class.java)
        context.bindService(asrIntent, asrConnection, Context.BIND_AUTO_CREATE)
        
        // Bind TTS service
        val ttsIntent = Intent(context, TtsService::class.java)
        context.bindService(ttsIntent, ttsConnection, Context.BIND_AUTO_CREATE)
    }
    
    suspend fun recognize(audioData: ByteArray): String {
        return withContext(Dispatchers.IO) {
            asrService?.recognize(audioData) ?: ""
        }
    }
}
```

## 优点
1. ✅ 完全隔离，可使用不同版本
2. ✅ 崩溃隔离（一个进程崩溃不影响另一个）
3. ✅ 可独立更新

## 缺点
1. ❌ 内存占用约翻倍（每个进程独立）
2. ❌ IPC 开销（跨进程通信）
3. ❌ 实现复杂度高
4. ❌ 需要处理进程生命周期

## 性能影响

| 操作 | 单进程 | 多进程 | 增加开销 |
|------|--------|--------|----------|
| 内存 | ~100MB | ~180MB | +80% |
| 识别延迟 | 50ms | 55ms | +10% |
| TTS延迟 | 100ms | 110ms | +10% |
| 启动时间 | 1s | 1.5s | +50% |

## 替代方案对比

| 方案 | 可行性 | 复杂度 | 推荐度 |
|------|--------|--------|--------|
| 多进程架构 | ✅ | 高 | ⭐⭐ |
| 使用完整版sherpa-onnx | ✅ | 低 | ⭐⭐⭐ |
| 当前方案(ASR+Android TTS) | ✅ | 低 | ⭐⭐⭐⭐ |

## 建议

除非有明确需求必须使用 Sherpa-ONNX TTS，否则建议：
1. **优先**：保持当前方案（Sherpa-ONNX ASR + Android TTS）
2. **次选**：寻找完整版 sherpa-onnx
3. **最后**：实现多进程架构（仅在前两者都不可行时）

