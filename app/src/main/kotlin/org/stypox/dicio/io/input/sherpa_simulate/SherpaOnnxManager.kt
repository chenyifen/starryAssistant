/*
 * Sherpa-ONNX 单例管理器
 * 参考官方demo: SimulateStreamingAsr.kt
 * 负责集中管理 OfflineRecognizer 和 VAD 的初始化
 */

package org.stypox.dicio.io.input.sherpa_simulate

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig

/**
 * Sherpa-ONNX组件管理器（单例模式）
 * 参考官方demo实现，确保线程安全和生命周期正确
 */
object SherpaOnnxManager {
    
    private const val TAG = "SherpaOnnxManager"
    
    private var _recognizer: OfflineRecognizer? = null
    val recognizer: OfflineRecognizer?
        get() = _recognizer
    
    private var _vad: Vad? = null
    val vad: Vad?
        get() = _vad
    
    /**
     * 初始化 OfflineRecognizer
     * 完全参考官方demo，使用assets中的模型
     */
    @Synchronized
    fun initOfflineRecognizer(assetManager: AssetManager): Boolean {
        if (_recognizer != null) {
            Log.d(TAG, "✅ OfflineRecognizer 已初始化，跳过")
            return true
        }
        
        return try {
            Log.i(TAG, "🔧 开始初始化 Sherpa-ONNX OfflineRecognizer...")
            
            // 直接使用assets中的模型路径（与官方demo一致）
            val modelDir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
            val modelPath = "$modelDir/model.int8.onnx"
            val tokensPath = "$modelDir/tokens.txt"
            
            Log.d(TAG, "📂 模型路径:")
            Log.d(TAG, "   模型: $modelPath (Assets)")
            Log.d(TAG, "   Tokens: $tokensPath (Assets)")
            
            // 创建配置（完全参考官方demo）
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelPath,
                        useInverseTextNormalization = true
                    ),
                    tokens = tokensPath,
                    numThreads = 2,
                    provider = "cpu",
                    debug = false
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )
            
            // 使用AssetManager加载（与官方demo一致）
            Log.d(TAG, "   📂 使用AssetManager加载模型")
            _recognizer = OfflineRecognizer(
                assetManager = assetManager,
                config = config
            )
            
            Log.i(TAG, "✅ Sherpa-ONNX OfflineRecognizer 初始化成功")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ OfflineRecognizer 初始化失败", e)
            _recognizer = null
            false
        }
    }
    
    /**
     * 初始化 VAD
     * 完全参考官方demo的方式
     */
    @Synchronized
    fun initVad(assetManager: AssetManager): Boolean {
        if (_vad != null) {
            Log.d(TAG, "✅ VAD 已初始化，跳过")
            return true
        }
        
        return try {
            Log.i(TAG, "🔧 开始初始化 Sherpa-ONNX VAD...")
            
            // 创建VAD配置（完全遵循官方demo）
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "silero_vad.onnx",  // assets根目录
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 5.0f
                ),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )
            
            Log.d(TAG, "📂 VAD模型: silero_vad.onnx (位于assets根目录)")
            
            // 参考官方demo，使用AssetManager创建VAD
            _vad = Vad(
                assetManager = assetManager,
                config = config
            )
            
            Log.i(TAG, "✅ Sherpa-ONNX VAD 初始化成功")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ VAD 初始化失败", e)
            _vad = null
            false
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return _recognizer != null && _vad != null
    }
    
    /**
     * 释放资源
     */
    @Synchronized
    fun release() {
        Log.i(TAG, "🗑️ 释放 Sherpa-ONNX 资源...")
        
        _recognizer?.release()
        _recognizer = null
        
        _vad?.release()
        _vad = null
        
        Log.i(TAG, "✅ 资源释放完成")
    }
}

