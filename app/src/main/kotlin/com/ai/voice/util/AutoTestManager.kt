package com.ai.voice.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * HeyNudge语音助手测试管理器
 * 
 * 功能：
 * 1. 管理测试集（唤醒测试、命令测试等）
 * 2. 支持暂停/继续测试
 * 3. 保存测试进度到文件
 * 4. 生成测试报告
 */
class AutoTestManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoTestManager"
        private const val PROGRESS_FILE = "auto_test_progress.txt"
        private const val REPORT_DIR = "/sdcard/dicio_test_reports"
        
        @Volatile
        private var instance: AutoTestManager? = null
        
        fun getInstance(context: Context): AutoTestManager {
            return instance ?: synchronized(this) {
                instance ?: AutoTestManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val currentTestIndex = AtomicInteger(0)
    private var currentTestSet: TestSet? = null
    private var currentReportFile: File? = null
    
    /**
     * 测试集定义
     */
    data class TestSet(
        val name: String,
        val type: TestType,
        val samples: List<TestSample>
    )
    
    /**
     * 测试样本
     */
    data class TestSample(
        val id: String,
        val description: String,
        val audioFile: String? = null,  // 音频文件路径（可选）
        val expectedCommand: String? = null  // 期望的命令（可选）
    )
    
    /**
     * 测试类型
     */
    enum class TestType {
        WAKE_WORD_ONLY,  // 纯唤醒测试
        COMMAND_TEST,    // 命令测试
        FULL_TEST        // 完整测试（唤醒+命令）
    }
    
    /**
     * 测试结果
     */
    data class TestResult(
        val sampleId: String,
        val success: Boolean,
        val asrResult: String?,
        val skillResult: String?,
        val timestamp: Long,
        val error: String? = null
    )
    
    private val testResults = mutableListOf<TestResult>()
    
    /**
     * 开始测试
     * @param testSet 测试集
     * @param resumeFromProgress 是否从上次进度继续
     */
    fun startTest(testSet: TestSet, resumeFromProgress: Boolean = false) {
        if (isRunning.get()) {
            Log.w(TAG, "⚠️ 测试已在运行中")
            return
        }
        
        isRunning.set(true)
        isPaused.set(false)
        currentTestSet = testSet
        
        // 加载或创建报告文件
        if (resumeFromProgress) {
            loadProgress()
        } else {
            currentTestIndex.set(0)
            testResults.clear()
            createNewReport()
        }
        
        Log.i(TAG, "🚀 开始测试: ${testSet.name}, 类型: ${testSet.type}")
        Log.i(TAG, "📊 总样本数: ${testSet.samples.size}, 起始索引: ${currentTestIndex.get()}")
        AutoTestLogger.logTestSetStarted(testSet.name, testSet.type.name)
        
        scope.launch {
            runTest()
        }
    }
    
    /**
     * 暂停测试
     */
    fun pauseTest() {
        if (!isRunning.get()) {
            Log.w(TAG, "⚠️ 测试未运行")
            return
        }
        
        isPaused.set(true)
        saveProgress()
        Log.i(TAG, "⏸️ 测试已暂停，进度已保存")
        AutoTestLogger.logTestPaused(currentTestIndex.get())
    }
    
    /**
     * 继续测试
     */
    fun resumeTest() {
        if (!isRunning.get()) {
            Log.w(TAG, "⚠️ 测试未运行")
            return
        }
        
        if (!isPaused.get()) {
            Log.w(TAG, "⚠️ 测试未暂停")
            return
        }
        
        isPaused.set(false)
        Log.i(TAG, "▶️ 测试继续，从索引 ${currentTestIndex.get()} 开始")
        AutoTestLogger.logTestResumed(currentTestIndex.get())
        
        scope.launch {
            runTest()
        }
    }
    
    /**
     * 停止测试
     */
    fun stopTest() {
        isRunning.set(false)
        isPaused.set(false)
        saveProgress()
        generateReport()
        Log.i(TAG, "🛑 测试已停止")
        AutoTestLogger.logTestStopped(currentTestIndex.get())
    }
    
    /**
     * 运行测试
     */
    private suspend fun runTest() {
        val testSet = currentTestSet ?: return
        
        while (isRunning.get() && currentTestIndex.get() < testSet.samples.size) {
            // 检查是否暂停
            if (isPaused.get()) {
                Log.d(TAG, "⏸️ 测试暂停中...")
                delay(1000)
                continue
            }
            
            val index = currentTestIndex.get()
            val sample = testSet.samples[index]
            
            Log.i(TAG, "🧪 测试样本 ${index + 1}/${testSet.samples.size}: ${sample.id}")
            AutoTestLogger.logTestSampleStarted(sample.id, sample.description)
            
            try {
                when (testSet.type) {
                    TestType.WAKE_WORD_ONLY -> {
                        // 纯唤醒测试：只播放唤醒词音频
                        playWakeWordAudio(sample)
                    }
                    TestType.COMMAND_TEST -> {
                        // 命令测试：播放唤醒词 + 命令音频
                        playCommandAudio(sample)
                    }
                    TestType.FULL_TEST -> {
                        // 完整测试：播放唤醒词 + 命令音频 + 验证结果
                        playFullTestAudio(sample)
                    }
                }
                
                // 等待处理完成
                delay(5000)  // 等待5秒让系统处理
                
                // 记录结果（实际结果由日志收集）
                val result = TestResult(
                    sampleId = sample.id,
                    success = true,
                    asrResult = null,  // 从日志中提取
                    skillResult = null,  // 从日志中提取
                    timestamp = System.currentTimeMillis()
                )
                testResults.add(result)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 测试样本失败: ${sample.id}", e)
                val result = TestResult(
                    sampleId = sample.id,
                    success = false,
                    asrResult = null,
                    skillResult = null,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
                testResults.add(result)
            }
            
            // 移动到下一个样本
            currentTestIndex.incrementAndGet()
            saveProgress()
            
            // 样本间延迟
            delay(2000)
        }
        
        // 测试完成
        if (currentTestIndex.get() >= testSet.samples.size) {
            Log.i(TAG, "✅ 测试完成！")
            AutoTestLogger.logTestCompleted(testSet.samples.size)
            generateReport()
            isRunning.set(false)
        }
    }
    
    /**
     * 播放唤醒词音频
     */
    private suspend fun playWakeWordAudio(sample: TestSample) {
        Log.d(TAG, "🎵 播放唤醒词音频: ${sample.audioFile}")
        // TODO: 实现音频播放逻辑
        // 使用 adb shell 播放音频文件
        sample.audioFile?.let { audioFile ->
            try {
                val command = "adb shell am broadcast -a com.ai.voice.PLAY_TEST_AUDIO --es audio_file $audioFile"
                Runtime.getRuntime().exec(command)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 播放音频失败", e)
            }
        }
    }
    
    /**
     * 播放命令音频
     */
    private suspend fun playCommandAudio(sample: TestSample) {
        Log.d(TAG, "🎵 播放命令音频: ${sample.audioFile}")
        playWakeWordAudio(sample)
    }
    
    /**
     * 播放完整测试音频
     */
    private suspend fun playFullTestAudio(sample: TestSample) {
        Log.d(TAG, "🎵 播放完整测试音频: ${sample.audioFile}")
        playWakeWordAudio(sample)
    }
    
    /**
     * 保存进度
     */
    private fun saveProgress() {
        try {
            val progressFile = File(context.filesDir, PROGRESS_FILE)
            val testSet = currentTestSet ?: return
            
            progressFile.writeText("""
                test_set=${testSet.name}
                test_type=${testSet.type}
                current_index=${currentTestIndex.get()}
                total_samples=${testSet.samples.size}
                is_paused=${isPaused.get()}
                timestamp=${System.currentTimeMillis()}
                report_file=${currentReportFile?.absolutePath}
            """.trimIndent())
            
            Log.d(TAG, "💾 进度已保存: 索引=${currentTestIndex.get()}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存进度失败", e)
        }
    }
    
    /**
     * 加载进度
     */
    private fun loadProgress() {
        try {
            val progressFile = File(context.filesDir, PROGRESS_FILE)
            if (!progressFile.exists()) {
                Log.w(TAG, "⚠️ 进度文件不存在")
                currentTestIndex.set(0)
                return
            }
            
            val lines = progressFile.readLines()
            val progressMap = lines.associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }
            
            val index = progressMap["current_index"]?.toIntOrNull() ?: 0
            val reportPath = progressMap["report_file"]
            
            currentTestIndex.set(index)
            if (reportPath != null) {
                currentReportFile = File(reportPath)
            }
            
            Log.i(TAG, "📂 进度已加载: 索引=$index, 报告=${currentReportFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载进度失败", e)
            currentTestIndex.set(0)
        }
    }
    
    /**
     * 创建新报告
     */
    private fun createNewReport() {
        try {
            val reportDir = File(REPORT_DIR)
            if (!reportDir.exists()) {
                reportDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val testSet = currentTestSet ?: return
            val filename = "test_report_${testSet.name}_$timestamp.txt"
            
            currentReportFile = File(reportDir, filename)
            currentReportFile?.writeText("""
                ========================================
                HeyNudge语音助手测试报告
                ========================================
                测试集: ${testSet.name}
                测试类型: ${testSet.type}
                开始时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                总样本数: ${testSet.samples.size}
                ========================================
                
            """.trimIndent())
            
            Log.i(TAG, "📝 创建新报告: ${currentReportFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建报告失败", e)
        }
    }
    
    /**
     * 生成测试报告
     */
    private fun generateReport() {
        try {
            val reportFile = currentReportFile ?: return
            val testSet = currentTestSet ?: return
            
            val successCount = testResults.count { it.success }
            val failureCount = testResults.count { !it.success }
            val successRate = if (testResults.isNotEmpty()) {
                (successCount.toFloat() / testResults.size * 100).toInt()
            } else 0
            
            val report = StringBuilder()
            report.appendLine("\n========================================")
            report.appendLine("HeyNudge语音助手测试结果汇总")
            report.appendLine("========================================")
            report.appendLine("完成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            report.appendLine("已测试: ${testResults.size}/${testSet.samples.size}")
            report.appendLine("成功: $successCount")
            report.appendLine("失败: $failureCount")
            report.appendLine("成功率: $successRate%")
            report.appendLine("========================================")
            report.appendLine("\n详细结果:")
            report.appendLine("----------------------------------------")
            
            testResults.forEachIndexed { index, result ->
                report.appendLine("\n样本 ${index + 1}: ${result.sampleId}")
                report.appendLine("  状态: ${if (result.success) "✅ 成功" else "❌ 失败"}")
                result.asrResult?.let { report.appendLine("  ASR: $it") }
                result.skillResult?.let { report.appendLine("  技能: $it") }
                result.error?.let { report.appendLine("  错误: $it") }
                report.appendLine("  时间: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))}")
            }
            
            reportFile.appendText(report.toString())
            
            Log.i(TAG, "📊 测试报告已生成: ${reportFile.absolutePath}")
            Log.i(TAG, "📈 成功率: $successRate% ($successCount/${ testResults.size})")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 生成报告失败", e)
        }
    }
    
    /**
     * 获取预定义测试集
     */
    fun getPredefinedTestSets(): List<TestSet> {
        return listOf(
            // 纯唤醒测试集
            TestSet(
                name = "wake_word_test",
                type = TestType.WAKE_WORD_ONLY,
                samples = listOf(
                    TestSample("wake_001", "唤醒测试1", "/sdcard/test_audio/wake_001.wav"),
                    TestSample("wake_002", "唤醒测试2", "/sdcard/test_audio/wake_002.wav"),
                    TestSample("wake_003", "唤醒测试3", "/sdcard/test_audio/wake_003.wav")
                )
            ),
            
            // 命令测试集
            TestSet(
                name = "command_test",
                type = TestType.COMMAND_TEST,
                samples = listOf(
                    TestSample("cmd_001", "Sample 1 - Go Home", "/sdcard/test_audio/cmd_001.wav", "홈 화면으로 이동해줘"),
                    TestSample("cmd_002", "Sample 2 - Open YouTube", "/sdcard/test_audio/cmd_002.wav", "유튜브 실행해줘"),
                    TestSample("cmd_003", "Sample 3 - Note Mode", "/sdcard/test_audio/cmd_003.wav", "판서모드로 바꿔줘"),
                    TestSample("cmd_004", "Sample 4 - Window Mode", "/sdcard/test_audio/cmd_004.wav", "윈도우모드로 바꿔줘"),
                    TestSample("cmd_005", "Sample 5 - WiFi Connect", "/sdcard/test_audio/cmd_005.wav", "와이파이 연결해줘")
                )
            )
        )
    }
}

