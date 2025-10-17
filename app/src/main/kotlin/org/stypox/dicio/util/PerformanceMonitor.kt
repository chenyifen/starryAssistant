package org.stypox.dicio.util

import android.util.Log

/**
 * 性能监控工具类
 * 用于追踪语音识别各个阶段的耗时
 */
class PerformanceMonitor(private val tag: String = "PerformanceMonitor") {
    private var startTime = 0L
    private val checkpoints = mutableListOf<Checkpoint>()
    private var lastCheckpointTime = 0L
    
    data class Checkpoint(
        val name: String,
        val absoluteTime: Long,  // 从开始到现在的总时间
        val deltaTime: Long       // 与上一个检查点的时间差
    )
    
    /**
     * 开始计时
     */
    fun start(description: String = "开始") {
        startTime = System.currentTimeMillis()
        lastCheckpointTime = startTime
        checkpoints.clear()
        Log.d(tag, "⏱️ [$description] 性能监控开始")
    }
    
    /**
     * 添加检查点
     */
    fun checkpoint(name: String) {
        val now = System.currentTimeMillis()
        val absoluteTime = now - startTime
        val deltaTime = now - lastCheckpointTime
        
        checkpoints.add(Checkpoint(name, absoluteTime, deltaTime))
        lastCheckpointTime = now
        
        Log.d(tag, "⏱️ [$name] +${deltaTime}ms (总计:${absoluteTime}ms)")
    }
    
    /**
     * 生成性能报告
     */
    fun report() {
        val totalTime = System.currentTimeMillis() - startTime
        
        Log.d(tag, "")
        Log.d(tag, "========== 📊 性能报告 ==========")
        checkpoints.forEachIndexed { index, checkpoint ->
            val percentage = if (totalTime > 0) {
                (checkpoint.deltaTime * 100.0 / totalTime).toInt()
            } else 0
            Log.d(tag, "${index + 1}. ${checkpoint.name}")
            Log.d(tag, "   └─ 耗时: ${checkpoint.deltaTime}ms ($percentage%) | 累计: ${checkpoint.absoluteTime}ms")
        }
        Log.d(tag, "")
        Log.d(tag, "🏁 总耗时: ${totalTime}ms")
        Log.d(tag, "==================================")
        Log.d(tag, "")
    }
    
    /**
     * 重置监控器
     */
    fun reset() {
        startTime = 0L
        lastCheckpointTime = 0L
        checkpoints.clear()
    }
    
    companion object {
        // 全局单例，用于追踪完整的语音识别流程
        private val globalMonitor = PerformanceMonitor("🎯VoiceRecognition")
        
        /**
         * 获取全局监控器实例
         */
        fun global(): PerformanceMonitor = globalMonitor
    }
}

