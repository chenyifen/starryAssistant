package org.stypox.dicio.util

import org.stypox.dicio.BuildConfig

/**
 * 性能监控配置管理器
 * 
 * 设计原则：
 * - 全局控制开关
 * - 零侵入性
 * - 可完全禁用
 * - 智能默认配置
 */
object PerformanceMonitorConfig {
    
    /**
     * 性能监控级别
     */
    enum class MonitoringLevel {
        DISABLED,    // 完全禁用
        LIGHTWEIGHT, // 轻量级监控（推荐）
        STANDARD,    // 标准监控
        ADVANCED     // 高级监控（仅调试模式）
    }
    
    /**
     * 全局开关：是否启用性能监控
     * 
     * 规则：
     * - 生产环境默认禁用
     * - 调试环境默认启用轻量级监控
     * - 用户可以通过设置覆盖
     */
    @JvmStatic
    fun isPerformanceMonitoringEnabled(): Boolean {
        return try {
            // 检查系统属性，允许通过adb动态控制
            val systemProperty = System.getProperty("dicio.performance.monitor", "auto")
            when (systemProperty) {
                "true", "1", "on" -> true
                "false", "0", "off" -> false
                "auto" -> BuildConfig.DEBUG // 自动模式：调试版本启用
                else -> BuildConfig.DEBUG
            }
        } catch (e: Exception) {
            // 异常时使用默认策略
            BuildConfig.DEBUG
        }
    }
    
    /**
     * 获取推荐的监控级别
     */
    @JvmStatic
    fun getRecommendedMonitoringLevel(): MonitoringLevel {
        return when {
            !isPerformanceMonitoringEnabled() -> MonitoringLevel.DISABLED
            BuildConfig.DEBUG -> MonitoringLevel.LIGHTWEIGHT // 调试模式使用轻量级
            else -> MonitoringLevel.DISABLED // 生产环境禁用
        }
    }
    
    /**
     * 检查是否应该显示性能监控UI
     */
    @JvmStatic
    fun shouldShowPerformanceUI(): Boolean {
        val level = getRecommendedMonitoringLevel()
        return level != MonitoringLevel.DISABLED
    }
    
    /**
     * 获取更新间隔（毫秒）
     */
    @JvmStatic
    fun getUpdateInterval(): Long {
        return when (getRecommendedMonitoringLevel()) {
            MonitoringLevel.DISABLED -> Long.MAX_VALUE
            MonitoringLevel.LIGHTWEIGHT -> 3000L // 3秒
            MonitoringLevel.STANDARD -> 2000L    // 2秒
            MonitoringLevel.ADVANCED -> 1000L    // 1秒
        }
    }
    
    /**
     * 获取采样间隔（毫秒）
     */
    @JvmStatic
    fun getSamplingInterval(): Long {
        return when (getRecommendedMonitoringLevel()) {
            MonitoringLevel.DISABLED -> Long.MAX_VALUE
            MonitoringLevel.LIGHTWEIGHT -> 5000L // 5秒采样一次
            MonitoringLevel.STANDARD -> 3000L    // 3秒采样一次
            MonitoringLevel.ADVANCED -> 1000L    // 1秒采样一次
        }
    }
    
    /**
     * 是否启用详细日志
     */
    @JvmStatic
    fun isDetailedLoggingEnabled(): Boolean {
        return BuildConfig.DEBUG && getRecommendedMonitoringLevel() != MonitoringLevel.DISABLED
    }
    
    /**
     * 动态调整配置（用于运行时优化）
     */
    @JvmStatic
    fun adjustForSystemLoad(cpuUsage: Float, memoryUsage: Float): MonitoringLevel {
        val currentLevel = getRecommendedMonitoringLevel()
        
        // 如果系统负载过高，自动降级
        return when {
            cpuUsage > 85f || memoryUsage > 90f -> {
                // 高负载时禁用监控
                MonitoringLevel.DISABLED
            }
            cpuUsage > 70f || memoryUsage > 80f -> {
                // 中等负载时使用轻量级监控
                if (currentLevel == MonitoringLevel.ADVANCED || currentLevel == MonitoringLevel.STANDARD) {
                    MonitoringLevel.LIGHTWEIGHT
                } else {
                    currentLevel
                }
            }
            else -> currentLevel
        }
    }
    
    /**
     * 获取配置摘要（用于调试）
     */
    @JvmStatic
    fun getConfigSummary(): String {
        return buildString {
            appendLine("Performance Monitor Configuration:")
            appendLine("  Enabled: ${isPerformanceMonitoringEnabled()}")
            appendLine("  Level: ${getRecommendedMonitoringLevel()}")
            appendLine("  Show UI: ${shouldShowPerformanceUI()}")
            appendLine("  Update Interval: ${getUpdateInterval()}ms")
            appendLine("  Sampling Interval: ${getSamplingInterval()}ms")
            appendLine("  Detailed Logging: ${isDetailedLoggingEnabled()}")
            appendLine("  Build Type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        }
    }
}
