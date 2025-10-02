package org.stypox.dicio.io.wake.sherpa

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.io.wake.WakeDevice
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.AudioDebugSaver
import org.stypox.dicio.util.measureTimeAndLog
import org.stypox.dicio.util.ModelVariantDetector
import org.stypox.dicio.util.PermissionHelper
import org.stypox.dicio.util.ModelPathManager
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File
import java.io.IOException
import kotlin.math.abs

class SherpaOnnxWakeDevice(
    private val appContext: Context,
) : WakeDevice {
    private val _state: MutableStateFlow<WakeState> = MutableStateFlow(WakeState.NotLoaded)
    override val state: StateFlow<WakeState> = _state

    private var keywordSpotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val modelDir = File(appContext.filesDir, "sherpa_onnx_kws")
    private val keywordsFile = File(modelDir, "keywords.txt")

    init {
        DebugLogger.logWakeWord(TAG, "🚀 Initializing SherpaOnnxWakeDevice")
        scope.launch {
            initialize()
        }
    }

    private suspend fun initialize() {
        _state.value = WakeState.Loading
        try {
            // 优先检查 assets 中是否有模型文件
            val hasAssetsModels = checkAssetsModelsAvailable()
            val useAssetManager = hasAssetsModels || ModelVariantDetector.shouldUseAssetManager(appContext)
            val variantName = ModelVariantDetector.getVariantName(appContext)
            
            DebugLogger.logModelManagement(TAG, "🏷️ 当前构建变体: $variantName")
            DebugLogger.logModelManagement(TAG, "📦 Assets 中有模型: $hasAssetsModels")
            DebugLogger.logModelManagement(TAG, "📦 使用 AssetManager: $useAssetManager")

            val config = createKwsConfig(useAssetManager)
            keywordSpotter = measureTimeAndLog(TAG, "Load SherpaOnnx KWS model") {
                if (useAssetManager) {
                    // 使用 AssetManager 方式（优先使用 assets 中的模型）
                    DebugLogger.logModelManagement(TAG, "🎯 使用 Assets 中的 SherpaOnnx KWS 模型")
                    KeywordSpotter(
                        assetManager = appContext.assets,
                        config = config
                    )
                } else {
                    // 使用文件系统路径方式（回退方案）
                    DebugLogger.logModelManagement(TAG, "🔄 回退到外部存储模型文件")
                    
                    // 显示路径状态信息
                    val pathStatus = ModelPathManager.getAllPathsStatus(appContext)
                    DebugLogger.logModelManagement(TAG, pathStatus)
                    
                    // 获取最佳外部存储路径
                    val externalModelPath = ModelPathManager.getExternalKwsModelsPath(appContext)
                    DebugLogger.logModelManagement(TAG, "🎯 选择的外部存储路径: $externalModelPath")
                    
                    // 检查模型文件是否可访问
                    if (!checkSherpaModelFilesAccess(externalModelPath)) {
                        // 设置为 ErrorLoading 而非 NotDownloaded，避免无限重试
                        _state.value = WakeState.ErrorLoading(IOException("SherpaOnnx KWS 模型文件不可访问: $externalModelPath"))
                        DebugLogger.logWakeWordError(TAG, "❌ SherpaOnnx KWS 模型文件不可访问")
                        DebugLogger.logWakeWordError(TAG, "💡 当前尝试路径: $externalModelPath")
                        
                        // 显示推荐的推送命令
                        val pushCommands = ModelPathManager.getModelPushCommands(appContext)
                        DebugLogger.logModelManagement(TAG, "📋 推荐的模型推送命令:")
                        pushCommands.forEach { cmd ->
                            DebugLogger.logModelManagement(TAG, cmd)
                        }
                        return
                    }
                    
                    KeywordSpotter(config = config)
                }
            }
            
            stream = keywordSpotter?.createStream()

            if (keywordSpotter != null && stream != null) {
                _state.value = WakeState.Loaded
                DebugLogger.logWakeWord(TAG, "✅ SherpaOnnx KWS model loaded successfully")
                DebugLogger.logWakeWord(TAG, "🔗 KeywordSpotter实例ID: ${keywordSpotter.hashCode()}")
            } else {
                _state.value = WakeState.ErrorLoading(IOException("Failed to initialize SherpaOnnx KeywordSpotter or stream"))
                DebugLogger.logWakeWordError(TAG, "❌ Failed to initialize SherpaOnnx KeywordSpotter or stream")
            }
        } catch (e: Exception) {
            _state.value = WakeState.ErrorLoading(e)
            DebugLogger.logWakeWordError(TAG, "❌ Error initializing SherpaOnnxWakeDevice: ${e.message}", e)
        }
    }

    /**
     * 检查 assets 中是否有 SherpaOnnx KWS 模型文件
     */
    private fun checkAssetsModelsAvailable(): Boolean {
        val requiredFiles = listOf(
            "models/sherpa_onnx_kws/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "models/sherpa_onnx_kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "models/sherpa_onnx_kws/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "models/sherpa_onnx_kws/keywords.txt",
            "models/sherpa_onnx_kws/tokens.txt"
        )
        
        return try {
            val allFilesExist = requiredFiles.all { fileName ->
                try {
                    appContext.assets.open(fileName).use { 
                        DebugLogger.logModelManagement(TAG, "✅ Assets 文件存在: $fileName")
                        true 
                    }
                } catch (e: Exception) {
                    DebugLogger.logModelManagement(TAG, "❌ Assets 文件缺失: $fileName")
                    false
                }
            }
            
            if (allFilesExist) {
                DebugLogger.logModelManagement(TAG, "🎉 Assets 中所有 SherpaOnnx KWS 模型文件都可用")
            } else {
                DebugLogger.logModelManagement(TAG, "⚠️ Assets 中缺少部分 SherpaOnnx KWS 模型文件")
            }
            
            allFilesExist
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ 检查 Assets 模型文件失败: ${e.message}")
            false
        }
    }

    private fun createKwsConfig(useAssetManager: Boolean): KeywordSpotterConfig {
        return if (useAssetManager) {
            // 使用 AssetManager 方式（withModels 变体）
            KeywordSpotterConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "models/sherpa_onnx_kws/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        decoder = "models/sherpa_onnx_kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        joiner = "models/sherpa_onnx_kws/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
                    ),
                    tokens = "models/sherpa_onnx_kws/tokens.txt",
                    modelType = "zipformer2",
                    numThreads = 1,
                    provider = "cpu"
                ),
                maxActivePaths = 4,
                keywordsFile = "models/sherpa_onnx_kws/keywords.txt",
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f,
                numTrailingBlanks = 2
            )
        } else {
            // 使用文件系统路径方式（noModels 变体）
            val externalModelPath = ModelPathManager.getExternalKwsModelsPath(appContext)
            KeywordSpotterConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$externalModelPath/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        decoder = "$externalModelPath/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        joiner = "$externalModelPath/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
                    ),
                    tokens = "$externalModelPath/tokens.txt",
                    modelType = "zipformer2",
                    numThreads = 1,
                    provider = "cpu"
                ),
                maxActivePaths = 4,
                keywordsFile = "$externalModelPath/keywords.txt",
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f,
                numTrailingBlanks = 2
            )
        }
    }

    override fun download() {
        // SherpaOnnx 模型"下载"逻辑 - 优先使用 assets，回退到外部存储
        if (_state.value == WakeState.NotDownloaded || _state.value is WakeState.ErrorLoading) {
            // 防止重复初始化：先设置为 Loading
            _state.value = WakeState.Loading
            
            scope.launch {
                val hasAssetsModels = checkAssetsModelsAvailable()
                
                if (hasAssetsModels) {
                    DebugLogger.logModelManagement(TAG, "🎯 检测到 Assets 中有模型文件，直接使用")
                    initialize()
                } else {
                    DebugLogger.logModelManagement(TAG, "🔄 Assets 中无模型文件，尝试使用外部存储...")
                    
                    // 检查权限
                    if (!PermissionHelper.hasExternalStoragePermission(appContext)) {
                        DebugLogger.logWakeWordError(TAG, "❌ 缺少外部存储权限")
                        DebugLogger.logWakeWordError(TAG, "💡 请在应用设置中授予存储权限后重试")
                        _state.value = WakeState.ErrorLoading(SecurityException("缺少外部存储权限"))
                        return@launch
                    }
                    
                    copyModelsForNoModelsVariant()
                    initialize()
                }
            }
        } else {
            DebugLogger.logModelManagement(TAG, "SherpaOnnx models already available or loading, skipping duplicate download call.")
        }
    }
    
    private suspend fun copyModelsForNoModelsVariant() {
        try {
            val externalModelPath = ModelPathManager.getExternalKwsModelsPath(appContext)
            val externalDir = java.io.File(externalModelPath)
            
            // 创建外部目录
            if (!externalDir.exists()) {
                externalDir.mkdirs()
                DebugLogger.logModelManagement(TAG, "📁 创建外部模型目录: $externalModelPath")
            }
            
            // 显示推荐的推送命令
            val pushCommands = ModelPathManager.getModelPushCommands(appContext)
            DebugLogger.logModelManagement(TAG, "⚠️ noModels变体需要手动推送模型文件")
            pushCommands.forEach { cmd ->
                DebugLogger.logModelManagement(TAG, cmd)
            }
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ 设置外部模型目录失败: ${e.message}", e)
        }
    }

    /**
     * 检查SherpaOnnx模型文件是否可访问
     */
    private fun checkSherpaModelFilesAccess(modelBasePath: String): Boolean {
        val requiredFiles = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx", 
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "keywords.txt",
            "tokens.txt"
        )
        
        return try {
            requiredFiles.all { fileName ->
                val file = File(modelBasePath, fileName)
                val exists = file.exists()
                // 移除 canRead 检查，只检查文件是否存在（简化权限处理）
                
                DebugLogger.logModelManagement(TAG, "📄 检查文件: $fileName - ${if (exists) "✅" else "❌"}")
                
                exists  // 只检查存在性
            }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ 检查模型文件失败: ${e.message}")
            false
        }
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        // 检查模型状态，如果未加载则返回false而不是抛异常
        when (val currentState = _state.value) {
            WakeState.Loaded -> {
                // 模型已加载，继续处理
            }
            WakeState.Loading -> {
                // 模型正在加载中，返回false但不报错
                return false
            }
            WakeState.NotLoaded, WakeState.NotDownloaded -> {
                DebugLogger.logWakeWord(TAG, "⏳ SherpaOnnx model not ready, current state: $currentState")
                return false
            }
            is WakeState.Downloading -> {
                DebugLogger.logWakeWord(TAG, "⏳ SherpaOnnx model downloading, progress: ${currentState.progress}")
                return false
            }
            is WakeState.ErrorDownloading -> {
                DebugLogger.logWakeWordError(TAG, "❌ SherpaOnnx model download error: ${currentState.throwable.message}")
                return false
            }
            is WakeState.ErrorLoading -> {
                DebugLogger.logWakeWordError(TAG, "❌ SherpaOnnx model in error state: ${currentState.throwable.message}")
                return false
            }
            WakeState.NoMicOrNotificationPermission -> {
                DebugLogger.logWakeWordError(TAG, "❌ No microphone or notification permission")
                return false
            }
        }

        if (audio16bitPcm.size != frameSize()) {
            DebugLogger.logWakeWordError(TAG, "❌ Invalid frame size: ${audio16bitPcm.size}, expected: ${frameSize()}")
            throw IllegalArgumentException(
                "SherpaOnnx can only process audio frames of ${frameSize()} samples"
            )
        }

        // Convert ShortArray to FloatArray for SherpaOnnx
        val audioFloat = FloatArray(audio16bitPcm.size) { i -> audio16bitPcm[i].toFloat() / 32768.0f }

        return try {
            // 计算音频幅度用于调试
            val amplitude = audio16bitPcm.maxOfOrNull { abs(it.toInt()) }?.toFloat() ?: 0.0f

            // 处理音频帧并获取检测结果
            val detected = measureTimeAndLog(TAG, "Process SherpaOnnx audio frame") {
                processSherpaFrame(audioFloat)
            }

            // SherpaOnnx does not directly provide confidence per frame.
            // We might need to infer it or use a fixed value for logging.
            val confidence = if (detected) 1.0f else 0.0f // Placeholder

            // 保存有音频信号的音频数据用于调试
            if (amplitude > 0.0f) {
                AudioDebugSaver.saveWakeAudio(appContext, audio16bitPcm, amplitude, confidence)
            }

            // 记录检测结果
            if (confidence > 0.0f) { // 只记录有效检测结果
                DebugLogger.logWakeWordDetection(TAG, confidence, 0.25f, detected)
            }
            DebugLogger.logAudioStats(TAG, audio16bitPcm.size, amplitude, 0.25f)

            detected
        } catch (t: Throwable) {
            DebugLogger.logWakeWordError(TAG, "❌ Error processing SherpaOnnx audio frame: ${t.message}", t)
            _state.value = WakeState.ErrorLoading(t) // Transition to error state
            throw t
        }
    }

    private fun processSherpaFrame(audioFloat: FloatArray): Boolean {
        stream?.acceptWaveform(audioFloat, sampleRate = 16000)

        var detected = false
        while (keywordSpotter?.isReady(stream!!) == true) {
            keywordSpotter?.decode(stream!!)
            val result = keywordSpotter?.getResult(stream!!)
            val keyword = result?.keyword ?: ""

            if (keyword.isNotEmpty()) {
                DebugLogger.logWakeWord(TAG, "🎯 SherpaOnnx KWS detected keyword: '$keyword'")
                detected = true
                keywordSpotter?.reset(stream!!) // Reset stream after detection
                break // Exit loop after first detection
            }
        }
        return detected
    }

    override fun frameSize(): Int {
        // SherpaOnnx typically uses 16kHz audio. Frame size depends on internal buffer.
        // A common frame size for 16kHz audio is 1600 samples (100ms).
        // This should match the AudioRecord buffer size.
        return 1600 // Example frame size, adjust as needed
    }

    override fun destroy() {
        DebugLogger.logWakeWord(TAG, "🧹 Destroying SherpaOnnxWakeDevice resources")
        
        try {
            // 安全释放stream资源
            stream?.let { s ->
                try {
                    s.release()
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "释放OnlineStream时出错", e)
                }
            }
            stream = null
            
            // 安全释放KeywordSpotter资源
            keywordSpotter?.let { kws ->
                try {
                    kws.release()
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "释放KeywordSpotter时出错", e)
                }
            }
            keywordSpotter = null
            
            // 取消协程作用域
            try {
                scope.cancel()
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "取消协程作用域时出错", e)
            }
            
            _state.value = WakeState.NotLoaded
            DebugLogger.logWakeWord(TAG, "✅ SherpaOnnxWakeDevice资源释放完成")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ 销毁SherpaOnnxWakeDevice失败", e)
        }
    }

    override fun isHeyDicio(): Boolean {
        // SherpaOnnx can support multiple keywords. For simplicity, we'll consider it
        // not "Hey Dicio" if it's enabled, as it's a custom KWS.
        return false
    }

    companion object {
        val TAG = SherpaOnnxWakeDevice::class.simpleName ?: "SherpaOnnxWakeDevice"
    }
}