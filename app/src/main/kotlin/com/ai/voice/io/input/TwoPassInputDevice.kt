/*
 * Dicio Two-Pass Speech Recognition
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ai.voice.io.input

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import com.ai.voice.di.LocaleManager
import com.ai.voice.io.input.sensevoice.AudioBuffer
import com.ai.voice.io.input.sensevoice.SenseVoiceModelManager
import com.ai.voice.io.input.sensevoice.SenseVoiceRecognizer
import com.ai.voice.io.input.vosk.VoskInputDevice
import com.ai.voice.io.input.vosk.VoskListener
import com.ai.voice.util.DebugLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 两阶段语音识别输入设备
 * 第一阶段：Vosk实时识别，提供即时反馈
 * 第二阶段：SenseVoice离线识别，提供准确的最终结果
 */
class TwoPassInputDevice(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val localeManager: LocaleManager,
) : SttInputDevice {

    companion object {
        private const val TAG = "TwoPassInputDevice"
        
        // 音频录制配置常量
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        
        // 二阶段识别触发条件
        private const val MIN_AUDIO_DURATION_FOR_SECOND_PASS = 1.0f // 最少1秒音频
        private const val SECOND_PASS_DELAY_MS = 500L // 延迟500ms后进行第二阶段识别
    }

    // 第一阶段：Vosk实时识别
    private val voskDevice = VoskInputDevice(appContext, okHttpClient, localeManager)
    
    // 第二阶段：SenseVoice识别器
    private var senseVoiceRecognizer: SenseVoiceRecognizer? = null
    
    // 音频缓冲区
    private val audioBuffer = AudioBuffer()
    
    // SenseVoice健康检查
    private var senseVoiceFailureCount = 0
    private var lastSenseVoiceFailureTime = 0L
    private val maxFailureCount = 3
    private val failureCooldownMs = 30000L // 30秒冷却期
    
    // UI状态管理（继承自Vosk状态）
    private val _uiState = MutableStateFlow<SttState>(SttState.NotInitialized)
    override val uiState: StateFlow<SttState> = _uiState.asStateFlow()
    
    // 控制标志
    private val isInitialized = AtomicBoolean(false)
    private val isListening = AtomicBoolean(false)
    private var secondPassJob: Job? = null
    private var eventListener: ((InputEvent) -> Unit)? = null
    
    // 音频录制相关
    private var audioRecord: AudioRecord? = null
    private var audioRecordingJob: Job? = null
    private val isAudioRecording = AtomicBoolean(false)
    
    init {
        Log.d(TAG, "🎯 TwoPassInputDevice 正在初始化...")
        Log.d(TAG, "📍 双识别模式已激活：Vosk (实时) + SenseVoice (精准)")
        
        val scope = CoroutineScope(Dispatchers.Default)
        
        // 初始化SenseVoice识别器
        Log.d(TAG, "🚀 启动SenseVoice初始化协程...")
        scope.launch {
            initializeSenseVoice()
        }
        
        // 监听Vosk状态变化
        scope.launch {
            voskDevice.uiState.collect { voskState ->
                _uiState.value = voskState
                handleVoskStateChange(voskState)
            }
        }
    }
    
    /**
     * 初始化SenseVoice识别器
     */
    private suspend fun initializeSenseVoice() {
        Log.d(TAG, "🔄 开始初始化SenseVoice识别器...")
        
        try {
            // 第一步：检查模型可用性
            Log.d(TAG, "📋 步骤1: 检查SenseVoice模型可用性...")
            val isSenseVoiceAvailable = SenseVoiceModelManager.isModelAvailable(appContext)
            Log.d(TAG, "📋 模型检查结果: ${if (isSenseVoiceAvailable) "✅ 可用" else "❌ 不可用"}")
            
            if (isSenseVoiceAvailable) {
                // 第二步：获取模型路径信息
                Log.d(TAG, "📋 步骤2: 获取模型路径信息...")
                val modelPaths = SenseVoiceModelManager.getModelPaths(appContext)
                if (modelPaths != null) {
                    Log.d(TAG, "📂 模型路径信息:")
                    Log.d(TAG, "   - 模型文件: ${modelPaths.modelPath}")
                    Log.d(TAG, "   - Tokens文件: ${modelPaths.tokensPath}")
                    Log.d(TAG, "   - 是否量化: ${modelPaths.isQuantized}")
                    Log.d(TAG, "   - 来源: ${if (modelPaths.isFromAssets) "Assets" else "外部存储"}")
                } else {
                    Log.e(TAG, "❌ 获取模型路径失败")
                }
                
                // 第三步：创建识别器
                Log.d(TAG, "📋 步骤3: 创建SenseVoice识别器...")
                senseVoiceRecognizer = try {
                    SenseVoiceRecognizer.create(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ SenseVoice识别器创建异常", e)
                    recordSenseVoiceFailure() // 记录初始化失败
                    null
                }
                
                if (senseVoiceRecognizer != null) {
                    Log.d(TAG, "✅ SenseVoice识别器初始化成功！")
                    Log.d(TAG, "🎯 双识别模式已激活 (Vosk + SenseVoice)")
                    isInitialized.set(true)
                    
                    // 获取模型详细信息
                    val modelInfo = SenseVoiceModelManager.getModelInfo(appContext)
                    Log.d(TAG, "📊 $modelInfo")
                } else {
                    Log.e(TAG, "❌ SenseVoice识别器创建失败，仅使用Vosk")
                    Log.d(TAG, "🔄 回退到单一Vosk识别模式")
                    recordSenseVoiceFailure() // 记录初始化失败
                }
            } else {
                Log.w(TAG, "⚠️ SenseVoice模型不可用，仅使用Vosk")
                Log.d(TAG, "💡 要启用双识别模式，请确保SenseVoice模型文件存在")
                Log.d(TAG, "📁 withModels版本: app内置模型")
                Log.d(TAG, "📁 noModels版本: 需要模型文件在 /storage/emulated/0/Dicio/models/sensevoice/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化SenseVoice过程中出现异常", e)
            Log.d(TAG, "🔄 异常恢复: 继续使用Vosk单一识别")
            recordSenseVoiceFailure() // 记录初始化异常
        }
        
        Log.d(TAG, "🏁 SenseVoice初始化流程完成")
    }
    
    /**
     * 处理Vosk状态变化
     */
    private fun handleVoskStateChange(state: SttState) {
        when (state) {
            is SttState.Listening -> {
                isListening.set(true)
                audioBuffer.clear() // 清空之前的音频缓冲区
                DebugLogger.logRecognition(TAG, "开始两阶段识别")
                
                // 启动并行音频录制用于SenseVoice
                startAudioRecording()
            }
            is SttState.Loaded -> {
                if (isListening.get()) {
                    isListening.set(false)
                    DebugLogger.logRecognition(TAG, "结束两阶段识别")
                    
                    // 停止音频录制
                    stopAudioRecording()
                }
            }
            else -> {
                // 其他状态保持原样
            }
        }
    }
    
    /**
     * 处理Vosk最终识别结果
     */
    private fun handleVoskFinalResult(resultJson: String) {
        try {
            DebugLogger.logRecognition(TAG, "Vosk第一阶段结果: $resultJson")
            
            // 简单解析JSON结果（实际应使用JSON库）
            val text = extractTextFromVoskJson(resultJson)
            val confidence = extractConfidenceFromVoskJson(resultJson)
            
            if (text.isNotBlank()) {
                // 发送第一阶段最终结果事件
                eventListener?.invoke(InputEvent.Final(listOf(Pair(text, confidence))))
                
                // 触发第二阶段识别
                triggerSecondPassRecognition(text, confidence)
            } else {
                // 没有识别到内容
                eventListener?.invoke(InputEvent.None)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理Vosk结果失败", e)
            eventListener?.invoke(InputEvent.Error(e))
        }
    }
    
    /**
     * 从Vosk JSON结果中提取文本（简单实现）
     */
    private fun extractTextFromVoskJson(json: String): String {
        return try {
            val textMatch = Regex(""""text"\s*:\s*"([^"]*)"""").find(json)
            textMatch?.groupValues?.get(1)?.trim() ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "解析Vosk JSON文本失败", e)
            ""
        }
    }
    
    /**
     * 从Vosk JSON结果中提取置信度（简单实现）
     */
    private fun extractConfidenceFromVoskJson(json: String): Float {
        return try {
            val confidenceMatch = Regex(""""confidence"\s*:\s*([0-9.]+)""").find(json)
            confidenceMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
        } catch (e: Exception) {
            Log.w(TAG, "解析Vosk JSON置信度失败", e)
            1.0f
        }
    }
    
    /**
     * 触发第二阶段识别
     */
    private fun triggerSecondPassRecognition(firstPassText: String, firstPassConfidence: Float) {
        Log.d(TAG, "🎯 触发第二阶段识别")
        Log.d(TAG, "   📝 第一阶段结果: \"$firstPassText\" (置信度: ${String.format("%.3f", firstPassConfidence)})")
        
        // 取消之前的第二阶段任务
        secondPassJob?.cancel()
        
        secondPassJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "⏱️ 延迟 ${SECOND_PASS_DELAY_MS}ms 确保音频完整...")
                delay(SECOND_PASS_DELAY_MS)
                
                // 添加超时处理，如果第二阶段识别超时，使用第一阶段结果
                val timeoutJob = launch {
                    delay(10000L) // 10秒超时
                    Log.w(TAG, "⚠️ 第二阶段识别超时，使用第一阶段结果")
                    withContext(Dispatchers.Main) {
                        eventListener?.invoke(InputEvent.Final(listOf(Pair(firstPassText, firstPassConfidence))))
                    }
                }
                
                // 检查音频时长
                val bufferInfo = audioBuffer.getBufferInfo()
                Log.d(TAG, "📊 音频缓冲区状态: $bufferInfo")
                
                if (!audioBuffer.hasMinimumAudio(MIN_AUDIO_DURATION_FOR_SECOND_PASS)) {
                    Log.w(TAG, "⚠️ 音频时长不足(< ${MIN_AUDIO_DURATION_FOR_SECOND_PASS}s)，使用第一阶段结果")
                    timeoutJob.cancel()
                    withContext(Dispatchers.Main) {
                        eventListener?.invoke(InputEvent.Final(listOf(Pair(firstPassText, firstPassConfidence))))
                    }
                    return@launch
                }
                
                // 等待SenseVoice初始化完成
                var recognizer = senseVoiceRecognizer
                if (recognizer == null) {
                    Log.d(TAG, "⏳ SenseVoice识别器正在初始化，等待完成...")
                    
                    // 最多等待10秒让SenseVoice初始化完成
                    val maxWaitTime = 10000L
                    val startWaitTime = System.currentTimeMillis()
                    
                    while (recognizer == null && (System.currentTimeMillis() - startWaitTime) < maxWaitTime) {
                        delay(100) // 每100ms检查一次
                        recognizer = senseVoiceRecognizer
                    }
                    
                    if (recognizer == null) {
                        Log.w(TAG, "⚠️ SenseVoice识别器初始化超时，使用第一阶段结果")
                        Log.d(TAG, "💡 请检查SenseVoice初始化是否成功")
                        timeoutJob.cancel()
                        withContext(Dispatchers.Main) {
                            eventListener?.invoke(InputEvent.Final(listOf(Pair(firstPassText, firstPassConfidence))))
                        }
                        return@launch
                    } else {
                        val waitTime = System.currentTimeMillis() - startWaitTime
                        Log.d(TAG, "✅ SenseVoice识别器初始化完成，等待时间: ${waitTime}ms")
                    }
                }
                
                Log.d(TAG, "✅ SenseVoice识别器可用，开始第二阶段识别")
                
                val secondPassStartTime = System.currentTimeMillis()
                Log.d(TAG, "🚀 开始第二阶段SenseVoice识别...")
                
                // 获取处理后的音频数据
                Log.d(TAG, "📡 获取并预处理音频数据...")
                val audioData = audioBuffer.getProcessedAudio()
                if (audioData.isEmpty()) {
                    Log.e(TAG, "❌ 音频数据为空，使用第一阶段结果")
                    timeoutJob.cancel()
                    withContext(Dispatchers.Main) {
                        eventListener?.invoke(InputEvent.Final(listOf(Pair(firstPassText, firstPassConfidence))))
                    }
                    return@launch
                }
                
                Log.d(TAG, "📊 音频数据: ${audioData.size}样本 (${String.format("%.2f", audioData.size.toFloat()/16000)}秒)")
                
                // 获取音频质量统计
                val qualityStats = audioBuffer.getAudioQualityStats()
                Log.d(TAG, "🎵 音频质量: $qualityStats")
                Log.d(TAG, "🎵 质量评估: ${if (qualityStats.isGoodQuality()) "✅ 良好" else "⚠️ 一般"}")
                
                // 运行SenseVoice识别
                Log.d(TAG, "🧠 执行SenseVoice推理...")
                val secondPassText = try {
                    // 检查SenseVoice健康状态
                    if (senseVoiceFailureCount >= maxFailureCount) {
                        Log.w(TAG, "⚠️ SenseVoice故障次数过多，跳过第二阶段")
                        ""
                    } else {
                        val result = recognizer.recognize(audioData)
                        recordSenseVoiceSuccess() // 记录成功
                        result
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ SenseVoice识别异常", e)
                    recordSenseVoiceFailure() // 记录失败
                    "" // 返回空结果，使用第一阶段结果
                }
                val secondPassTime = System.currentTimeMillis() - secondPassStartTime
                
                DebugLogger.logRecognition(TAG, "第二阶段识别完成: \"$secondPassText\" (${secondPassTime}ms)")
                
                // 选择最终结果
                val finalText = if (secondPassText.isNotBlank()) secondPassText else firstPassText
                val finalConfidence = if (secondPassText.isNotBlank() && secondPassText != firstPassText) {
                    // 如果二阶段改进了结果，提高置信度
                    kotlin.math.min(firstPassConfidence + 0.1f, 1.0f)
                } else {
                    firstPassConfidence
                }
                
                DebugLogger.logRecognition(TAG, "🎯 最终识别结果: \"$finalText\" (置信度: ${String.format("%.3f", finalConfidence)})")
                
                // 取消超时任务
                timeoutJob.cancel()
                
                // 发送最终的二阶段结果事件
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.Final(listOf(Pair(finalText, finalConfidence))))
                }
                
                // 日志对比结果
                val improved = secondPassText.isNotBlank() && secondPassText != firstPassText
                DebugLogger.logRecognition(TAG, 
                    "识别对比 - 第一阶段: \"$firstPassText\" -> " +
                    "第二阶段: \"$secondPassText\" (${if (improved) "改进" else "无改进"})")
                
                // 清空音频缓冲区
                audioBuffer.clear()
                
            } catch (e: CancellationException) {
                DebugLogger.logRecognition(TAG, "第二阶段识别被取消")
                // 如果被取消，可能是由于新的识别开始，不需要回退
            } catch (e: Exception) {
                Log.e(TAG, "第二阶段识别失败，回退到第一阶段结果", e)
                recordSenseVoiceFailure() // 记录失败
                // 第二阶段识别失败，使用第一阶段结果
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.Final(listOf(Pair(firstPassText, firstPassConfidence))))
                }
            }
        }
    }
    
    // SttInputDevice接口实现
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        this.eventListener = thenStartListeningEventListener
        return voskDevice.tryLoad { event ->
            // 处理两阶段识别逻辑
            handleVoskEvent(event)
        }
    }
    
    override fun stopListening() {
        voskDevice.stopListening()
        
        // 取消进行中的第二阶段识别
        secondPassJob?.cancel()
    }
    
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        this.eventListener = eventListener
        voskDevice.onClick { event ->
            // 处理两阶段识别逻辑
            handleVoskEvent(event)
        }
    }
    
    override suspend fun destroy() {
        try {
            // 停止音频录制
            stopAudioRecording()
            
            // 取消第二阶段任务
            secondPassJob?.cancel()
            
            // 释放SenseVoice识别器
            senseVoiceRecognizer?.release()
            senseVoiceRecognizer = null
            
            // 释放Vosk设备
            voskDevice.destroy()
            
            // 清空缓冲区
            audioBuffer.clear()
            
            DebugLogger.logRecognition(TAG, "两阶段识别设备资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放两阶段识别设备资源失败", e)
        }
    }
    
    /**
     * 处理Vosk设备的所有事件
     */
    private fun handleVoskEvent(event: InputEvent) {
        when (event) {
            is InputEvent.Final -> {
                DebugLogger.logRecognition(TAG, "收到Vosk最终结果事件")
                
                // 触发第二阶段识别，但不立即传递第一阶段结果
                if (event.utterances.isNotEmpty()) {
                    val firstPassText = event.utterances.first().first
                    val firstPassConfidence = event.utterances.first().second
                    
                    DebugLogger.logRecognition(TAG, "Vosk第一阶段结果: \"$firstPassText\" (置信度: $firstPassConfidence)")
                    
                    // 如果SenseVoice可用，等待第二阶段识别完成
                    if (isSenseVoiceAvailable()) {
                        triggerSecondPassRecognition(firstPassText, firstPassConfidence)
                    } else {
                        // 如果SenseVoice不可用，直接传递第一阶段结果
                        val reason = when {
                            senseVoiceRecognizer == null -> "未初始化"
                            senseVoiceFailureCount >= maxFailureCount -> "失败次数过多(${senseVoiceFailureCount}/$maxFailureCount)"
                            else -> "未知原因"
                        }
                        DebugLogger.logRecognition(TAG, "SenseVoice不可用($reason)，直接使用Vosk结果")
                        eventListener?.invoke(event)
                    }
                } else {
                    // 如果Vosk没有识别到内容，直接传递
                    eventListener?.invoke(event)
                }
            }
            is InputEvent.Partial -> {
                // 部分结果直接传递，提供实时反馈
                eventListener?.invoke(event)
            }
            else -> {
                // 其他事件直接传递
                eventListener?.invoke(event)
            }
        }
    }
    
    /**
     * 启动音频录制（用于SenseVoice）
     */
    private fun startAudioRecording() {
        if (isAudioRecording.get()) {
            DebugLogger.logAudio(TAG, "音频录制已在进行中，跳过启动")
            return
        }
        
        try {
            // 创建AudioRecord
            val bufferSizeInBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (bufferSizeInBytes == AudioRecord.ERROR || bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ 无法获取AudioRecord缓冲区大小")
                return
            }
            
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord初始化失败")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            // 启动录制
            audioRecord?.startRecording()
            isAudioRecording.set(true)
            
            DebugLogger.logAudio(TAG, "🎙️ 开始并行音频录制（用于SenseVoice）")
            
            // 启动音频处理协程
            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                processAudioData()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动音频录制失败", e)
            cleanupAudioRecord()
        }
    }
    
    /**
     * 停止音频录制
     */
    private fun stopAudioRecording() {
        if (!isAudioRecording.get()) {
            return
        }
        
        DebugLogger.logAudio(TAG, "🛑 停止并行音频录制")
        isAudioRecording.set(false)
        
        // 取消音频处理协程
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        
        cleanupAudioRecord()
    }
    
    /**
     * 清理AudioRecord资源
     */
    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理AudioRecord资源失败", e)
        }
    }
    
    /**
     * 处理音频数据（在IO线程中运行）
     */
    private suspend fun processAudioData() {
        val bufferSize = 1024 // 每次读取的样本数
        val buffer = ShortArray(bufferSize)
        
        DebugLogger.logAudio(TAG, "🔄 开始音频数据处理循环")
        
        while (isAudioRecording.get() && !Thread.currentThread().isInterrupted) {
            try {
                val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readSamples > 0) {
                    // 转换为Float数组（SenseVoice需要）
                    val floatBuffer = FloatArray(readSamples) { i ->
                        buffer[i].toFloat() / 32768.0f // 转换到 [-1.0, 1.0]
                    }
                    
                    // 添加到音频缓冲区
                    audioBuffer.addAudioChunk(floatBuffer)
                    
                } else if (readSamples < 0) {
                    Log.e(TAG, "❌ 读取音频数据错误: $readSamples")
                    break
                }
                
                // 让出CPU避免占用过高
                yield()
                
            } catch (e: Exception) {
                if (isAudioRecording.get()) { // 只有在还在录音时才报告错误
                    Log.e(TAG, "❌ 处理音频数据异常", e)
                }
                break
            }
        }
        
        DebugLogger.logAudio(TAG, "🏁 音频数据处理循环结束")
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        val senseVoiceInfo = senseVoiceRecognizer?.getInfo() ?: "SenseVoice: 不可用"
        val bufferInfo = audioBuffer.getBufferInfo()
        
        return "TwoPassDevice(Vosk + $senseVoiceInfo, $bufferInfo)"
    }
    
    /**
     * 检查是否支持SenseVoice
     */
    fun isSenseVoiceAvailable(): Boolean {
        // 检查基本可用性
        if (senseVoiceRecognizer == null) {
            return false
        }
        
        // 检查健康状态
        val currentTime = System.currentTimeMillis()
        if (senseVoiceFailureCount >= maxFailureCount) {
            if (currentTime - lastSenseVoiceFailureTime < failureCooldownMs) {
                Log.d(TAG, "⚠️ SenseVoice暂时禁用中，冷却剩余: ${(failureCooldownMs - (currentTime - lastSenseVoiceFailureTime))/1000}秒")
                return false
            } else {
                // 冷却期结束，重置失败计数
                Log.d(TAG, "🔄 SenseVoice冷却期结束，重新启用")
                senseVoiceFailureCount = 0
                lastSenseVoiceFailureTime = 0L
            }
        }
        
        return true
    }
    
    /**
     * 记录SenseVoice识别失败
     */
    private fun recordSenseVoiceFailure() {
        senseVoiceFailureCount++
        lastSenseVoiceFailureTime = System.currentTimeMillis()
        Log.w(TAG, "⚠️ SenseVoice识别失败，失败次数: $senseVoiceFailureCount/$maxFailureCount")
        
        if (senseVoiceFailureCount >= maxFailureCount) {
            Log.w(TAG, "❌ SenseVoice失败次数过多，暂时禁用${failureCooldownMs/1000}秒")
        }
    }
    
    /**
     * 记录SenseVoice识别成功
     */
    private fun recordSenseVoiceSuccess() {
        if (senseVoiceFailureCount > 0) {
            Log.d(TAG, "✅ SenseVoice识别成功，重置失败计数")
            senseVoiceFailureCount = 0
            lastSenseVoiceFailureTime = 0L
        }
    }
}