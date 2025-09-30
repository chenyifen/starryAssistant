package org.stypox.dicio.ui.floating.components

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay
import org.stypox.dicio.util.DebugLogger
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * 性能监控数据类
 */
data class PerformanceMetrics(
    val cpuUsage: Float = 0f,           // CPU使用率 (%)
    val memoryUsage: Long = 0L,         // 内存使用量 (MB)
    val memoryTotal: Long = 0L,         // 总内存 (MB)
    val memoryPercent: Float = 0f,      // 内存使用百分比
    val startupTime: Long = 0L,         // 启动耗时 (ms)
    val responseTime: Long = 0L,        // 响应时间 (ms)
    val fps: Int = 0,                   // 帧率
    val isRealtime: Boolean = true      // 实时性状态
)

/**
 * 性能监控器
 * 
 * 功能：
 * - CPU使用率监控
 * - 内存使用监控
 * - 启动耗时统计
 * - 响应时间监控
 * - 实时性评估
 */
class PerformanceMonitorManager(private val context: Context) {
    private val TAG = "PerformanceMonitor"
    
    // 启动时间记录
    private val startupStartTime = SystemClock.elapsedRealtime()
    private var startupEndTime: Long? = null
    
    // CPU监控相关
    private var lastCpuTime = 0L
    private var lastAppCpuTime = 0L
    
    // 响应时间监控
    private var lastActionTime = SystemClock.elapsedRealtime()
    
    // ActivityManager用于内存监控
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    /**
     * 标记启动完成
     */
    fun markStartupComplete() {
        if (startupEndTime == null) {
            startupEndTime = SystemClock.elapsedRealtime()
            DebugLogger.logPerformance(TAG, "🚀 Startup completed", getStartupTime())
        }
    }
    
    /**
     * 标记用户操作开始（用于响应时间计算）
     */
    fun markActionStart() {
        lastActionTime = SystemClock.elapsedRealtime()
    }
    
    /**
     * 获取当前性能指标
     */
    fun getCurrentMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            cpuUsage = getCpuUsage(),
            memoryUsage = getMemoryUsage(),
            memoryTotal = getTotalMemory(),
            memoryPercent = getMemoryPercent(),
            startupTime = getStartupTime(),
            responseTime = getResponseTime(),
            fps = getFps(),
            isRealtime = isRealtime()
        )
    }
    
    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Float {
        return try {
            val currentTime = SystemClock.elapsedRealtime()
            val currentAppCpuTime = Process.getElapsedCpuTime()
            
            if (lastCpuTime > 0) {
                val totalTime = currentTime - lastCpuTime
                val appTime = currentAppCpuTime - lastAppCpuTime
                
                val usage = if (totalTime > 0) {
                    (appTime.toFloat() / totalTime.toFloat()) * 100f
                } else 0f
                
                lastCpuTime = currentTime
                lastAppCpuTime = currentAppCpuTime
                
                usage.coerceIn(0f, 100f)
            } else {
                lastCpuTime = currentTime
                lastAppCpuTime = currentAppCpuTime
                0f
            }
        } catch (e: Exception) {
            DebugLogger.logDebug(TAG, "Failed to get CPU usage: ${e.message}")
            0f
        }
    }
    
    /**
     * 获取内存使用量 (MB)
     */
    private fun getMemoryUsage(): Long {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            
            // 获取PSS内存（包含共享内存的比例分配）
            val pssMemory = memoryInfo.totalPss
            pssMemory / 1024L // 转换为MB
        } catch (e: Exception) {
            DebugLogger.logDebug(TAG, "Failed to get memory usage: ${e.message}")
            0L
        }
    }
    
    /**
     * 获取总内存 (MB)
     */
    private fun getTotalMemory(): Long {
        return try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / (1024 * 1024) // 转换为MB
        } catch (e: Exception) {
            DebugLogger.logDebug(TAG, "Failed to get total memory: ${e.message}")
            0L
        }
    }
    
    /**
     * 获取内存使用百分比
     */
    private fun getMemoryPercent(): Float {
        val used = getMemoryUsage()
        val total = getTotalMemory()
        return if (total > 0) {
            (used.toFloat() / total.toFloat()) * 100f
        } else 0f
    }
    
    /**
     * 获取启动耗时
     */
    private fun getStartupTime(): Long {
        return startupEndTime?.let { endTime ->
            endTime - startupStartTime
        } ?: (SystemClock.elapsedRealtime() - startupStartTime)
    }
    
    /**
     * 获取响应时间
     */
    private fun getResponseTime(): Long {
        return SystemClock.elapsedRealtime() - lastActionTime
    }
    
    /**
     * 获取帧率（简化实现）
     */
    private fun getFps(): Int {
        // 简化实现：基于CPU使用率和内存使用率估算
        val cpuUsage = getCpuUsage()
        val memoryPercent = getMemoryPercent()
        
        return when {
            cpuUsage < 30f && memoryPercent < 50f -> 60
            cpuUsage < 50f && memoryPercent < 70f -> 45
            cpuUsage < 70f && memoryPercent < 85f -> 30
            else -> 15
        }
    }
    
    /**
     * 评估实时性
     */
    private fun isRealtime(): Boolean {
        val responseTime = getResponseTime()
        val cpuUsage = getCpuUsage()
        val memoryPercent = getMemoryPercent()
        
        // 实时性判断标准：响应时间<100ms，CPU<80%，内存<90%
        return responseTime < 100 && cpuUsage < 80f && memoryPercent < 90f
    }
}

/**
 * 性能监控显示组件
 */
@Composable
fun PerformanceMonitorDisplay(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    val context = LocalContext.current
    val performanceManager = remember { PerformanceMonitorManager(context) }
    
    // 性能数据状态
    var metrics by remember { mutableStateOf(PerformanceMetrics()) }
    
    // 定期更新性能数据
    LaunchedEffect(Unit) {
        performanceManager.markStartupComplete()
        
        while (true) {
            metrics = performanceManager.getCurrentMetrics()
            delay(1000) // 每秒更新一次
        }
    }
    
    // 性能监控面板
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题
            Text(
                text = "性能监控",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // CPU使用率
            PerformanceMetricRow(
                label = "CPU",
                value = "${metrics.cpuUsage.roundToInt()}%",
                color = when {
                    metrics.cpuUsage < 30f -> Color.Green
                    metrics.cpuUsage < 70f -> Color.Yellow
                    else -> Color.Red
                }
            )
            
            // 内存使用
            PerformanceMetricRow(
                label = "内存",
                value = "${metrics.memoryUsage}MB",
                color = when {
                    metrics.memoryPercent < 50f -> Color.Green
                    metrics.memoryPercent < 80f -> Color.Yellow
                    else -> Color.Red
                }
            )
            
            // 启动耗时
            PerformanceMetricRow(
                label = "启动",
                value = "${metrics.startupTime}ms",
                color = when {
                    metrics.startupTime < 1000 -> Color.Green
                    metrics.startupTime < 3000 -> Color.Yellow
                    else -> Color.Red
                }
            )
            
            // 响应时间
            PerformanceMetricRow(
                label = "响应",
                value = "${metrics.responseTime}ms",
                color = when {
                    metrics.responseTime < 100 -> Color.Green
                    metrics.responseTime < 500 -> Color.Yellow
                    else -> Color.Red
                }
            )
            
            // 实时性状态
            PerformanceMetricRow(
                label = "实时",
                value = if (metrics.isRealtime) "✓" else "✗",
                color = if (metrics.isRealtime) Color.Green else Color.Red
            )
        }
    }
}

/**
 * 性能指标行组件
 */
@Composable
private fun PerformanceMetricRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = value,
            color = color,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
