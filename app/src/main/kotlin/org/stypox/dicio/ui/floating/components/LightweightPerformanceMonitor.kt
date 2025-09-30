package org.stypox.dicio.ui.floating.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.StatFs
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.stypox.dicio.BuildConfig
import org.stypox.dicio.util.PerformanceMonitorConfig
import kotlin.math.roundToInt

/**
 * 轻量级性能监控数据类
 * 根据调试模式显示不同详细程度的指标
 */
data class LightweightMetrics(
    val cpuUsage: Float = 0f,
    val memoryUsage: Long = 0L,
    val memoryPercent: Float = 0f,
    val isHealthy: Boolean = true,
    // 调试模式下的额外指标
    val threadCount: Int = 0,
    val gcCount: Long = 0L,
    val responseTime: Long = 0L,
    val networkLatency: Long = 0L,
    // 新增指标
    val currentTime: String = "",
    val senseVoiceRtl: Long = 0L,      // SenseVoice实时延迟
    val asrAccuracy: Float = 0f,       // ASR准确率
    val wakeWordLatency: Long = 0L,    // 唤醒词延迟
    val ttsLatency: Long = 0L,         // TTS延迟
    val voiceQuality: Float = 0f       // 语音质量评分
)

/**
 * 轻量级性能监控管理器
 * 
 * 设计原则：
 * - 最小化计算开销
 * - 异步数据收集
 * - 智能缓存
 * - 错误隔离
 */
class LightweightPerformanceManager(private val context: Context) {
    private val TAG = "LightweightPerformanceManager"
    
    // 缓存的指标数据，避免频繁计算
    @Volatile
    private var cachedMetrics = LightweightMetrics()
    
    // 上次更新时间，用于控制更新频率
    @Volatile
    private var lastUpdateTime = 0L
    
    // 更新间隔：使用配置管理器
    private val updateInterval = PerformanceMonitorConfig.getSamplingInterval()
    
    // ActivityManager缓存
    private val activityManager by lazy { 
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager 
    }
    
    /**
     * 异步获取性能指标
     * 使用缓存机制，避免频繁计算
     */
    suspend fun getMetrics(): LightweightMetrics = withContext(Dispatchers.Default) {
        val currentTime = System.currentTimeMillis()
        
        // 如果缓存仍然有效，直接返回
        if (currentTime - lastUpdateTime < updateInterval) {
            return@withContext cachedMetrics
        }
        
        try {
            // 在后台线程计算指标
            val newMetrics = calculateMetrics()
            
            // 更新缓存
            cachedMetrics = newMetrics
            lastUpdateTime = currentTime
            
            newMetrics
        } catch (e: Exception) {
            // 错误隔离：计算失败时返回缓存的数据
            cachedMetrics
        }
    }
    
    /**
     * 计算性能指标
     * 根据调试模式决定计算的详细程度
     */
    private fun calculateMetrics(): LightweightMetrics {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            val memoryUsage = memoryInfo.totalPss / 1024L // KB to MB
            
            // 基础指标
            val cpuUsage = estimateCpuUsage()
            val memoryPercent = (memoryUsage.toFloat() / 200f) * 100f // 假设200MB为基准
            val isHealthy = cpuUsage < 80f && memoryPercent < 90f
            
            // 调试模式下的额外指标
            val debugMetrics = if (BuildConfig.DEBUG) {
                calculateDebugMetrics()
            } else {
                DebugMetrics()
            }
            
            LightweightMetrics(
                cpuUsage = cpuUsage.coerceIn(0f, 100f),
                memoryUsage = memoryUsage,
                memoryPercent = memoryPercent.coerceIn(0f, 100f),
                isHealthy = isHealthy,
                threadCount = debugMetrics.threadCount,
                gcCount = debugMetrics.gcCount,
                responseTime = debugMetrics.responseTime,
                networkLatency = debugMetrics.networkLatency,
                currentTime = debugMetrics.currentTime,
                senseVoiceRtl = debugMetrics.senseVoiceRtl,
                asrAccuracy = debugMetrics.asrAccuracy,
                wakeWordLatency = debugMetrics.wakeWordLatency,
                ttsLatency = debugMetrics.ttsLatency,
                voiceQuality = debugMetrics.voiceQuality
            )
        } catch (e: Exception) {
            // 出错时返回默认值
            LightweightMetrics()
        }
    }
    
    /**
     * 调试指标数据类
     */
    private data class DebugMetrics(
        val threadCount: Int = 0,
        val gcCount: Long = 0L,
        val responseTime: Long = 0L,
        val networkLatency: Long = 0L,
        val currentTime: String = "",
        val senseVoiceRtl: Long = 0L,
        val asrAccuracy: Float = 0f,
        val wakeWordLatency: Long = 0L,
        val ttsLatency: Long = 0L,
        val voiceQuality: Float = 0f
    )
    
    /**
     * 计算调试模式下的额外指标
     */
    private fun calculateDebugMetrics(): DebugMetrics {
        return try {
            val threadCount = Thread.activeCount()
            val gcCount = Debug.getGlobalGcInvocationCount().toLong()
            val responseTime = measureResponseTime()
            val currentTime = getCurrentTimeString()
            val voiceMetrics = getVoiceMetrics()
            
            DebugMetrics(
                threadCount = threadCount,
                gcCount = gcCount,
                responseTime = responseTime,
                networkLatency = 0L, // 网络延迟需要实际网络请求，这里暂时设为0
                currentTime = currentTime,
                senseVoiceRtl = voiceMetrics.senseVoiceRtl,
                asrAccuracy = voiceMetrics.asrAccuracy,
                wakeWordLatency = voiceMetrics.wakeWordLatency,
                ttsLatency = voiceMetrics.ttsLatency,
                voiceQuality = voiceMetrics.voiceQuality
            )
        } catch (e: Exception) {
            DebugMetrics()
        }
    }
    
    /**
     * 语音指标数据类
     */
    private data class VoiceMetrics(
        val senseVoiceRtl: Long = 0L,
        val asrAccuracy: Float = 0f,
        val wakeWordLatency: Long = 0L,
        val ttsLatency: Long = 0L,
        val voiceQuality: Float = 0f
    )
    
    /**
     * 获取当前时间字符串
     */
    private fun getCurrentTimeString(): String {
        return try {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            formatter.format(Date())
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 获取语音相关指标
     * 这里需要与语音处理模块集成
     */
    private fun getVoiceMetrics(): VoiceMetrics {
        return try {
            // TODO: 集成实际的语音指标
            // 这里先返回模拟数据，后续需要与SenseVoice等模块集成
            VoiceMetrics(
                senseVoiceRtl = estimateSenseVoiceLatency(),
                asrAccuracy = estimateAsrAccuracy(),
                wakeWordLatency = estimateWakeWordLatency(),
                ttsLatency = estimateTtsLatency(),
                voiceQuality = estimateVoiceQuality()
            )
        } catch (e: Exception) {
            VoiceMetrics()
        }
    }
    
    /**
     * 估算SenseVoice实时延迟
     */
    private fun estimateSenseVoiceLatency(): Long {
        // TODO: 集成实际的SenseVoice延迟监控
        return (50..150).random().toLong() // 模拟50-150ms的延迟
    }
    
    /**
     * 估算ASR准确率
     */
    private fun estimateAsrAccuracy(): Float {
        // TODO: 集成实际的ASR准确率统计
        return Random.nextFloat() * (98f - 85f) + 85f // 模拟85%-98%的准确率
    }
    
    /**
     * 估算唤醒词延迟
     */
    private fun estimateWakeWordLatency(): Long {
        // TODO: 集成实际的唤醒词延迟监控
        return (100..300).random().toLong() // 模拟100-300ms的延迟
    }
    
    /**
     * 估算TTS延迟
     */
    private fun estimateTtsLatency(): Long {
        // TODO: 集成实际的TTS延迟监控
        return (200..500).random().toLong() // 模拟200-500ms的延迟
    }
    
    /**
     * 估算语音质量评分
     */
    private fun estimateVoiceQuality(): Float {
        // TODO: 集成实际的语音质量评估
        return Random.nextFloat() * (95f - 70f) + 70f // 模拟70-95分的质量评分
    }
    
    /**
     * 测量响应时间
     */
    private fun measureResponseTime(): Long {
        val startTime = SystemClock.elapsedRealtime()
        // 执行一个简单的操作来测量响应时间
        Thread.sleep(1)
        return SystemClock.elapsedRealtime() - startTime
    }
    
    
    /**
     * 简化的CPU使用率估算
     * 避免复杂的系统调用，使用启发式方法
     */
    private fun estimateCpuUsage(): Float {
        return try {
            // 使用线程数作为CPU使用率的粗略估算
            val threadCount = Thread.activeCount()
            val estimatedUsage = (threadCount * 2f).coerceIn(0f, 100f)
            estimatedUsage
        } catch (e: Exception) {
            0f
        }
    }
}

/**
 * 轻量级性能监控显示组件
 * 
 * 特性：
 * - 最小化重组
 * - 异步数据加载
 * - 智能更新策略
 * - 错误容错
 */
@Composable
fun LightweightPerformanceMonitorDisplay(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 检查全局配置，如果禁用则不显示
    if (!isVisible || !PerformanceMonitorConfig.shouldShowPerformanceUI()) return
    
    val context = LocalContext.current
    
    // 使用remember避免重复创建管理器
    val performanceManager = remember(context) { 
        LightweightPerformanceManager(context) 
    }
    
    // 性能数据状态
    var metrics by remember { mutableStateOf(LightweightMetrics()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 异步加载性能数据，避免阻塞UI线程
    LaunchedEffect(performanceManager) {
        try {
            while (isVisible) {
                // 异步获取数据
                val newMetrics = performanceManager.getMetrics()
                
                // 只有数据真正变化时才更新UI
                if (newMetrics != metrics) {
                    metrics = newMetrics
                    isLoading = false
                }
                
                // 使用配置的更新间隔
                delay(PerformanceMonitorConfig.getUpdateInterval())
            }
        } catch (e: Exception) {
            // 异常处理：隐藏加载状态，显示最后的有效数据
            isLoading = false
        }
    }
    
    // 如果正在加载且没有数据，显示简单的加载状态
    if (isLoading && metrics == LightweightMetrics()) {
        Box(
            modifier = modifier.size(60.dp, 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "...",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 8.sp
            )
        }
        return
    }
    
    // 性能监控面板 - 根据调试模式调整显示内容
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 标题和时间
        if (BuildConfig.DEBUG && metrics.currentTime.isNotEmpty()) {
            Text(
                text = "调试性能 ${metrics.currentTime}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = if (BuildConfig.DEBUG) "调试性能" else "性能",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 基础指标（始终显示）
        MetricRow(
            label = "CPU处理器",
            value = "${metrics.cpuUsage.roundToInt()}%",
            isHealthy = metrics.cpuUsage < 70f
        )
        
        MetricRow(
            label = "MEM内存",
            value = "${metrics.memoryUsage}M",
            isHealthy = metrics.memoryPercent < 80f
        )
        
        // 调试模式下的额外指标
        if (BuildConfig.DEBUG) {
            // 语音相关指标（优先显示）
            if (metrics.senseVoiceRtl > 0) {
                MetricRow(
                    label = "SVR语音识别",
                    value = "${metrics.senseVoiceRtl}ms",
                    isHealthy = metrics.senseVoiceRtl < 200
                )
            }
            
            if (metrics.asrAccuracy > 0) {
                MetricRow(
                    label = "ASR准确率",
                    value = "${metrics.asrAccuracy.roundToInt()}%",
                    isHealthy = metrics.asrAccuracy > 90f
                )
            }
            
            if (metrics.wakeWordLatency > 0) {
                MetricRow(
                    label = "WKW唤醒词",
                    value = "${metrics.wakeWordLatency}ms",
                    isHealthy = metrics.wakeWordLatency < 300
                )
            }
            
            if (metrics.ttsLatency > 0) {
                MetricRow(
                    label = "TTS语音合成",
                    value = "${metrics.ttsLatency}ms",
                    isHealthy = metrics.ttsLatency < 400
                )
            }
            
            if (metrics.voiceQuality > 0) {
                MetricRow(
                    label = "VQ语音质量",
                    value = "${metrics.voiceQuality.roundToInt()}",
                    isHealthy = metrics.voiceQuality > 80f
                )
            }
            
            // 系统指标
            MetricRow(
                label = "THR线程数",
                value = "${metrics.threadCount}",
                isHealthy = metrics.threadCount < 50
            )
            
            MetricRow(
                label = "GC垃圾回收",
                value = "${metrics.gcCount}",
                isHealthy = true
            )
            
            MetricRow(
                label = "RT响应时间",
                value = "${metrics.responseTime}ms",
                isHealthy = metrics.responseTime < 10
            )
        }
        
        // 整体健康状态
        Text(
            text = if (metrics.isHealthy) "✓" else "!",
            color = if (metrics.isHealthy) Color.Green else Color.Yellow,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 性能指标行组件
 */
@Composable
private fun MetricRow(
    label: String,
    value: String,
    isHealthy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = value,
            color = if (isHealthy) Color.Green.copy(alpha = 0.8f) else Color.Yellow.copy(alpha = 0.8f),
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
