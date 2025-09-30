package org.stypox.dicio.util

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.stypox.dicio.ui.floating.components.CommercialPerformanceMetrics
import org.stypox.dicio.ui.floating.components.PerformanceAlert
import org.stypox.dicio.ui.floating.components.PerformanceAlertLevel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

/**
 * 性能数据记录
 */
data class PerformanceDataPoint(
    val timestamp: Long,
    val metrics: CommercialPerformanceMetrics,
    val alerts: List<PerformanceAlert>
)

/**
 * 性能报告配置
 */
data class PerformanceReportConfig(
    val enableDataCollection: Boolean = true,
    val enableFileExport: Boolean = true,
    val enableRealTimeAlerts: Boolean = true,
    val dataRetentionHours: Int = 24,
    val reportIntervalMinutes: Int = 60,
    val alertThresholds: Map<String, Float> = mapOf(
        "cpu_critical" to 85f,
        "memory_critical" to 90f,
        "latency_critical" to 500f,
        "error_rate_critical" to 5f
    )
)

/**
 * 商用级性能报告服务
 * 
 * 功能特性：
 * - 性能数据持久化存储
 * - 定期性能报告生成
 * - 实时告警通知
 * - 数据导出功能
 * - 趋势分析
 */
class PerformanceReportingService(
    private val context: Context,
    private val config: PerformanceReportConfig = PerformanceReportConfig()
) {
    private val TAG = "PerformanceReportingService"
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 数据存储
    private val dataPoints = ConcurrentLinkedQueue<PerformanceDataPoint>()
    private val maxDataPoints = config.dataRetentionHours * 60 // 每分钟一个数据点
    
    // 报告生成器
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    // 状态流
    private val _performanceData = MutableStateFlow<List<PerformanceDataPoint>>(emptyList())
    val performanceData: StateFlow<List<PerformanceDataPoint>> = _performanceData.asStateFlow()
    
    private val _latestAlerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val latestAlerts: StateFlow<List<PerformanceAlert>> = _latestAlerts.asStateFlow()
    
    init {
        if (config.enableDataCollection) {
            startDataCollection()
        }
        
        if (config.enableFileExport) {
            startPeriodicReporting()
        }
    }
    
    /**
     * 记录性能数据点
     */
    fun recordDataPoint(metrics: CommercialPerformanceMetrics, alerts: List<PerformanceAlert>) {
        if (!config.enableDataCollection) return
        
        val dataPoint = PerformanceDataPoint(
            timestamp = System.currentTimeMillis(),
            metrics = metrics,
            alerts = alerts
        )
        
        // 添加数据点
        dataPoints.offer(dataPoint)
        
        // 保持数据点数量在限制内
        while (dataPoints.size > maxDataPoints) {
            dataPoints.poll()
        }
        
        // 更新状态流
        _performanceData.value = dataPoints.toList()
        _latestAlerts.value = alerts
        
        // 检查实时告警
        if (config.enableRealTimeAlerts) {
            checkRealTimeAlerts(metrics, alerts)
        }
        
        DebugLogger.logDebug(TAG, "📊 Performance data recorded: CPU=${metrics.cpuUsage.roundToInt()}%, Memory=${metrics.memoryUsage}MB, E2E=${metrics.endToEndLatency}ms")
    }
    
    /**
     * 生成性能报告
     */
    fun generateReport(
        startTime: Long = System.currentTimeMillis() - 3600000, // 默认最近1小时
        endTime: Long = System.currentTimeMillis()
    ): String {
        val filteredData = dataPoints.filter { it.timestamp in startTime..endTime }
        
        if (filteredData.isEmpty()) {
            return "No performance data available for the specified time range."
        }
        
        return buildString {
            appendLine("=== Dicio商用性能分析报告 ===")
            appendLine("报告生成时间: ${dateFormat.format(Date())}")
            appendLine("数据时间范围: ${dateFormat.format(Date(startTime))} - ${dateFormat.format(Date(endTime))}")
            appendLine("数据点数量: ${filteredData.size}")
            appendLine()
            
            // 统计摘要
            val metrics = filteredData.map { it.metrics }
            appendLine("📊 性能统计摘要:")
            appendLine("  平均端到端延迟: ${metrics.map { it.endToEndLatency }.average().roundToInt()}ms")
            appendLine("  平均CPU使用率: ${metrics.map { it.cpuUsage }.average().roundToInt()}%")
            appendLine("  平均内存使用: ${metrics.map { it.memoryUsage }.average().roundToInt()}MB")
            appendLine("  平均错误率: ${metrics.map { it.errorRate }.average()}%")
            appendLine("  平均质量评分: ${metrics.map { it.qualityScore }.average().roundToInt()}/100")
            appendLine()
            
            // SLA合规性分析
            val slaCompliantCount = metrics.count { it.slaCompliance }
            val slaComplianceRate = (slaCompliantCount.toFloat() / metrics.size) * 100f
            appendLine("📋 SLA合规性分析:")
            appendLine("  合规数据点: $slaCompliantCount / ${metrics.size}")
            appendLine("  合规率: ${slaComplianceRate.roundToInt()}%")
            appendLine("  目标合规率: ≥ 99%")
            appendLine("  合规状态: ${if (slaComplianceRate >= 99f) "✅ 达标" else "❌ 未达标"}")
            appendLine()
            
            // 性能趋势分析
            appendLine("📈 性能趋势分析:")
            val recentMetrics = metrics.takeLast(10)
            val olderMetrics = metrics.take(10)
            
            if (recentMetrics.isNotEmpty() && olderMetrics.isNotEmpty()) {
                val latencyTrend = recentMetrics.map { it.endToEndLatency }.average() - olderMetrics.map { it.endToEndLatency }.average()
                val cpuTrend = recentMetrics.map { it.cpuUsage }.average() - olderMetrics.map { it.cpuUsage }.average()
                val memoryTrend = recentMetrics.map { it.memoryUsage }.average() - olderMetrics.map { it.memoryUsage }.average()
                
                appendLine("  延迟趋势: ${if (latencyTrend > 0) "↗️ 上升" else "↘️ 下降"} (${latencyTrend.roundToInt()}ms)")
                appendLine("  CPU趋势: ${if (cpuTrend > 0) "↗️ 上升" else "↘️ 下降"} (${cpuTrend.roundToInt()}%)")
                appendLine("  内存趋势: ${if (memoryTrend > 0) "↗️ 上升" else "↘️ 下降"} (${memoryTrend.roundToInt()}MB)")
            }
            appendLine()
            
            // 告警统计
            val allAlerts = filteredData.flatMap { it.alerts }
            if (allAlerts.isNotEmpty()) {
                val criticalAlerts = allAlerts.count { it.level == PerformanceAlertLevel.CRITICAL || it.level == PerformanceAlertLevel.EMERGENCY }
                val warningAlerts = allAlerts.count { it.level == PerformanceAlertLevel.WARNING }
                
                appendLine("🚨 告警统计:")
                appendLine("  严重告警: $criticalAlerts 次")
                appendLine("  警告告警: $warningAlerts 次")
                appendLine("  总告警数: ${allAlerts.size} 次")
                
                // 最频繁的告警类型
                val alertsByMetric = allAlerts.groupBy { it.metric }
                val topAlert = alertsByMetric.maxByOrNull { it.value.size }
                if (topAlert != null) {
                    appendLine("  最频繁告警: ${topAlert.key} (${topAlert.value.size} 次)")
                }
                appendLine()
            }
            
            // 性能建议
            appendLine("💡 性能优化建议:")
            val avgLatency = metrics.map { it.endToEndLatency }.average()
            val avgCpu = metrics.map { it.cpuUsage }.average()
            val avgMemory = metrics.map { it.memoryPercent }.average()
            val avgErrorRate = metrics.map { it.errorRate }.average()
            
            if (avgLatency > 300) {
                appendLine("  - 端到端延迟偏高，建议优化语音处理流程")
            }
            if (avgCpu > 70) {
                appendLine("  - CPU使用率偏高，建议优化算法或增加异步处理")
            }
            if (avgMemory > 80) {
                appendLine("  - 内存使用率偏高，建议检查内存泄漏或优化缓存策略")
            }
            if (avgErrorRate > 1) {
                appendLine("  - 错误率偏高，建议加强异常处理和容错机制")
            }
            if (slaComplianceRate < 99) {
                appendLine("  - SLA合规率未达标，建议重点关注性能瓶颈")
            }
            
            appendLine()
            appendLine("报告生成完成 ✅")
        }
    }
    
    /**
     * 导出性能数据到文件
     */
    suspend fun exportToFile(
        fileName: String? = null,
        format: ExportFormat = ExportFormat.TXT
    ): String = withContext(Dispatchers.IO) {
        try {
            val actualFileName = fileName ?: "dicio_performance_${fileNameFormat.format(Date())}.${format.extension}"
            val file = File(context.getExternalFilesDir("performance_reports"), actualFileName)
            
            // 确保目录存在
            file.parentFile?.mkdirs()
            
            val content = when (format) {
                ExportFormat.TXT -> generateReport()
                ExportFormat.CSV -> generateCsvReport()
                ExportFormat.JSON -> generateJsonReport()
            }
            
            FileWriter(file).use { writer ->
                writer.write(content)
            }
            
            DebugLogger.logDebug(TAG, "📄 Performance report exported to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            DebugLogger.logDebug(TAG, "❌ Failed to export performance report: ${e.message}")
            throw e
        }
    }
    
    /**
     * 获取性能统计摘要
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val recentDataList = dataPoints.toList().takeLast(60) // 最近60个数据点
        val metrics = recentDataList.map { it.metrics }
        
        return PerformanceSummary(
            dataPointCount = recentDataList.size,
            averageLatency = if (metrics.isNotEmpty()) metrics.map { it.endToEndLatency.toDouble() }.average() else 0.0,
            averageCpuUsage = if (metrics.isNotEmpty()) metrics.map { it.cpuUsage.toDouble() }.average() else 0.0,
            averageMemoryUsage = if (metrics.isNotEmpty()) metrics.map { it.memoryUsage.toDouble() }.average() else 0.0,
            averageErrorRate = if (metrics.isNotEmpty()) metrics.map { it.errorRate.toDouble() }.average() else 0.0,
            slaComplianceRate = if (metrics.isNotEmpty()) (metrics.count { it.slaCompliance }.toFloat() / metrics.size) * 100f else 100f,
            alertCount = recentDataList.sumOf { it.alerts.size },
            criticalAlertCount = recentDataList.sumOf { dataPoint -> dataPoint.alerts.count { it.level == PerformanceAlertLevel.CRITICAL || it.level == PerformanceAlertLevel.EMERGENCY } }
        )
    }
    
    /**
     * 清理历史数据
     */
    fun clearHistoryData() {
        dataPoints.clear()
        _performanceData.value = emptyList()
        _latestAlerts.value = emptyList()
        DebugLogger.logDebug(TAG, "🗑️ Performance history data cleared")
    }
    
    /**
     * 停止服务
     */
    fun stop() {
        serviceScope.cancel()
        DebugLogger.logDebug(TAG, "🛑 Performance reporting service stopped")
    }
    
    // ===== 私有方法 =====
    
    private fun startDataCollection() {
        // 这里可以添加定期数据收集逻辑
        DebugLogger.logDebug(TAG, "📊 Performance data collection started")
    }
    
    private fun startPeriodicReporting() {
        serviceScope.launch {
            while (isActive) {
                delay(config.reportIntervalMinutes * 60 * 1000L)
                try {
                    val reportPath = exportToFile()
                    DebugLogger.logDebug(TAG, "📄 Periodic report generated: $reportPath")
                } catch (e: Exception) {
                    DebugLogger.logDebug(TAG, "❌ Failed to generate periodic report: ${e.message}")
                }
            }
        }
    }
    
    private fun checkRealTimeAlerts(metrics: CommercialPerformanceMetrics, alerts: List<PerformanceAlert>) {
        // 检查关键指标是否超过阈值
        val criticalAlerts = alerts.filter { 
            it.level == PerformanceAlertLevel.CRITICAL || it.level == PerformanceAlertLevel.EMERGENCY 
        }
        
        if (criticalAlerts.isNotEmpty()) {
            DebugLogger.logDebug(TAG, "🚨 Critical performance alerts detected: ${criticalAlerts.size}")
            // 这里可以添加实时通知逻辑
        }
    }
    
    private fun generateCsvReport(): String {
        val data = dataPoints.toList()
        return buildString {
            // CSV头部
            appendLine("timestamp,endToEndLatency,voiceProcessingLatency,intentAnalysisLatency,cpuUsage,memoryUsage,memoryPercent,errorRate,qualityScore,slaCompliance,alertCount")
            
            // 数据行
            data.forEach { dataPoint ->
                val m = dataPoint.metrics
                appendLine("${dataPoint.timestamp},${m.endToEndLatency},${m.voiceProcessingLatency},${m.intentAnalysisLatency},${m.cpuUsage},${m.memoryUsage},${m.memoryPercent},${m.errorRate},${m.qualityScore},${m.slaCompliance},${dataPoint.alerts.size}")
            }
        }
    }
    
    private fun generateJsonReport(): String {
        // 简化的JSON生成，实际项目中应使用JSON库
        val data = dataPoints.toList()
        return buildString {
            appendLine("{")
            appendLine("  \"reportGeneratedAt\": ${System.currentTimeMillis()},")
            appendLine("  \"dataPoints\": [")
            
            data.forEachIndexed { index, dataPoint ->
                val m = dataPoint.metrics
                appendLine("    {")
                appendLine("      \"timestamp\": ${dataPoint.timestamp},")
                appendLine("      \"metrics\": {")
                appendLine("        \"endToEndLatency\": ${m.endToEndLatency},")
                appendLine("        \"voiceProcessingLatency\": ${m.voiceProcessingLatency},")
                appendLine("        \"intentAnalysisLatency\": ${m.intentAnalysisLatency},")
                appendLine("        \"cpuUsage\": ${m.cpuUsage},")
                appendLine("        \"memoryUsage\": ${m.memoryUsage},")
                appendLine("        \"errorRate\": ${m.errorRate},")
                appendLine("        \"qualityScore\": ${m.qualityScore},")
                appendLine("        \"slaCompliance\": ${m.slaCompliance}")
                appendLine("      },")
                appendLine("      \"alertCount\": ${dataPoint.alerts.size}")
                append("    }")
                if (index < data.size - 1) appendLine(",")
                else appendLine()
            }
            
            appendLine("  ]")
            appendLine("}")
        }
    }
}

/**
 * 导出格式枚举
 */
enum class ExportFormat(val extension: String) {
    TXT("txt"),
    CSV("csv"),
    JSON("json")
}

/**
 * 性能统计摘要
 */
data class PerformanceSummary(
    val dataPointCount: Int,
    val averageLatency: Double,
    val averageCpuUsage: Double,
    val averageMemoryUsage: Double,
    val averageErrorRate: Double,
    val slaComplianceRate: Float,
    val alertCount: Int,
    val criticalAlertCount: Int
)
