/*
 * Sherpa-ONNX 单例管理器
 * 参考官方demo: SimulateStreamingAsr.kt
 * 负责集中管理 OfflineRecognizer 和 VAD 的初始化
 * 
 * 修改说明：使用SenseVoiceModelManager统一管理模型路径
 */

package com.ai.voice.io.input.sherpa_simulate

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import kotlinx.coroutines.runBlocking
import com.ai.voice.io.input.sensevoice.SenseVoiceModelManager
import com.ai.voice.io.input.sensevoice.VadModelManager

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
     * 使用SenseVoiceModelManager统一管理模型
     */
    @Synchronized
    fun initOfflineRecognizer(context: Context): Boolean {
        if (_recognizer != null) {
            Log.d(TAG, "✅ OfflineRecognizer 已初始化，跳过")
            return true
        }
        
        return try {
            Log.i(TAG, "🔧 开始初始化 Sherpa-ONNX OfflineRecognizer...")
            
            // 使用SenseVoiceModelManager获取模型路径
            val modelPaths = runBlocking {
                SenseVoiceModelManager.getModelPaths(context)
            }
            
            if (modelPaths == null) {
                Log.e(TAG, "❌ 未找到可用的SenseVoice模型")
                Log.e(TAG, "💡 请确保已下载SenseVoice模型到:")
                Log.e(TAG, "   1. Assets目录: app/src/main/assets/models/asr/sensevoice/")
                Log.e(TAG, "   2. 或外部存储: /storage/emulated/0/Android/data/.../models/sensevoice/")
                return false
            }
            
            Log.d(TAG, "📂 模型路径:")
            Log.d(TAG, "   模型: ${modelPaths.modelPath}")
            Log.d(TAG, "   Tokens: ${modelPaths.tokensPath}")
            Log.d(TAG, "   来源: ${if (modelPaths.isFromAssets) "Assets" else "外部存储"}")
            Log.d(TAG, "   类型: ${if (modelPaths.isQuantized) "量化模型(INT8)" else "普通模型"}")
            
            // 创建配置
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelPaths.modelPath,
                        language = "auto",  // 使用自动语言检测模式
                        useInverseTextNormalization = true
                    ),
                    tokens = modelPaths.tokensPath,
                    numThreads = 2,
                    provider = "cpu",
                    debug = false
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )
            
            // 根据模型来源选择加载方式
            _recognizer = if (modelPaths.isFromAssets) {
                Log.d(TAG, "   📂 使用AssetManager加载模型")
                OfflineRecognizer(
                    assetManager = context.assets,
                    config = config
                )
            } else {
                Log.d(TAG, "   💾 从文件系统加载模型")
                OfflineRecognizer(
                    assetManager = null,
                    config = config
                )
            }
            
            Log.i(TAG, "✅ Sherpa-ONNX OfflineRecognizer 初始化成功")
            Log.i(TAG, "🌍 支持语言: 中文、英文、日文、韩文、粤语 (自动检测)")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ OfflineRecognizer 初始化失败", e)
            Log.e(TAG, "💡 错误详情: ${e.message}")
            _recognizer = null
            false
        }
    }
    
    /**
     * 初始化 VAD
     * 使用VadModelManager统一管理模型
     */
    @Synchronized
    fun initVad(context: Context): Boolean {
        if (_vad != null) {
            Log.d(TAG, "✅ VAD 已初始化，跳过")
            return true
        }
        
        return try {
            Log.i(TAG, "🔧 开始初始化 Sherpa-ONNX VAD...")
            
            // 检查VAD模型是否可用
            if (!VadModelManager.isVadModelAvailable(context)) {
                Log.w(TAG, "⚠️ VAD模型不可用，将使用能量检测作为替代")
                return false
            }
            
            // 获取VAD配置
            val config = VadModelManager.createVadConfig(context)
            if (config == null) {
                Log.w(TAG, "⚠️ VAD配置创建失败")
                return false
            }
            
            val modelPaths = VadModelManager.getVadModelPaths(context)
            if (modelPaths == null) {
                Log.w(TAG, "⚠️ VAD模型路径获取失败")
                return false
            }
            
            Log.d(TAG, "📂 VAD模型: ${modelPaths.modelPath}")
            Log.d(TAG, "   来源: ${if (modelPaths.isFromAssets) "Assets" else "外部存储"}")
            
            // 根据模型来源选择加载方式
            _vad = if (modelPaths.isFromAssets) {
                Log.d(TAG, "   📂 从Assets加载VAD模型")
                Vad(context.assets, config)
            } else {
                Log.d(TAG, "   💾 从文件系统加载VAD模型")
                Vad(null, config)
            }
            
            Log.i(TAG, "✅ Sherpa-ONNX VAD 初始化成功")
            Log.i(TAG, "📊 ${VadModelManager.getVadModelInfo(context)}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ VAD 初始化失败: ${e.message}", e)
            Log.w(TAG, "⚠️ 将使用能量检测作为替代")
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

