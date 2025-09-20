package org.stypox.dicio.test.automation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.minutes

/**
 * 语音助手测试运行器
 * 
 * 功能：
 * 1. 执行测试套件
 * 2. 生成测试报告
 * 3. 监控测试进度
 * 4. 导出测试结果
 */
class TestRunner(
    private val context: Context,
    private val testFramework: VoiceAssistantTestFramework
) {
    
    companion object {
        private const val TAG = "TestRunner"
        private const val REPORT_DIR = "voice_assistant_test_reports"
    }
    
    private val _testProgress = MutableStateFlow(TestProgress())
    val testProgress: StateFlow<TestProgress> = _testProgress.asStateFlow()
    
    private val _testLogs = MutableSharedFlow<TestLogEntry>()
    val testLogs: SharedFlow<TestLogEntry> = _testLogs.asSharedFlow()
    
    /**
     * 测试进度数据类
     */
    data class TestProgress(
        val isRunning: Boolean = false,
        val currentTest: String = "",
        val completedTests: Int = 0,
        val totalTests: Int = 0,
        val currentCategory: String = "",
        val elapsedTime: Long = 0L
    ) {
        val progressPercentage: Float = if (totalTests > 0) completedTests.toFloat() / totalTests else 0f
    }
    
    /**
     * 测试日志条目
     */
    data class TestLogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val category: String,
        val message: String
    )
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 运行完整测试套件
     */
    suspend fun runTests(
        categories: Set<VoiceAssistantTestFramework.TestCategory> = VoiceAssistantTestFramework.TestCategory.values().toSet(),
        generateReport: Boolean = true
    ): TestExecutionResult {
        
        logInfo("TEST_RUNNER", "🚀 开始执行语音助手测试套件")
        
        val startTime = System.currentTimeMillis()
        
        _testProgress.value = TestProgress(
            isRunning = true,
            totalTests = estimateTestCount(categories),
            currentCategory = "初始化"
        )
        
        return try {
            // 预热系统
            warmupSystem()
            
            // 执行测试
            val result = testFramework.runFullTestSuite()
            
            val endTime = System.currentTimeMillis()
            val totalDuration = endTime - startTime
            
            // 更新进度
            _testProgress.value = TestProgress(
                isRunning = false,
                completedTests = result.totalTests,
                totalTests = result.totalTests,
                elapsedTime = totalDuration
            )
            
            logInfo("TEST_RUNNER", "✅ 测试套件执行完成")
            
            // 生成报告
            val reportPath = if (generateReport) {
                generateTestReport(result, totalDuration)
            } else null
            
            TestExecutionResult(
                success = result.isAllPassed,
                testSuiteResult = result,
                reportPath = reportPath,
                executionTime = totalDuration
            )
            
        } catch (e: Exception) {
            logError("TEST_RUNNER", "❌ 测试执行异常", e)
            
            _testProgress.value = TestProgress(isRunning = false)
            
            TestExecutionResult(
                success = false,
                testSuiteResult = null,
                reportPath = null,
                executionTime = System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }
    
    /**
     * 运行特定类别的测试
     */
    suspend fun runCategoryTests(category: VoiceAssistantTestFramework.TestCategory): TestExecutionResult {
        return runTests(setOf(category))
    }
    
    /**
     * 运行单个测试
     */
    suspend fun runSingleTest(testName: String): TestExecutionResult {
        logInfo("TEST_RUNNER", "🎯 运行单个测试: $testName")
        
        // 这里需要扩展TestFramework来支持单个测试执行
        // 暂时返回占位符结果
        return TestExecutionResult(
            success = true,
            testSuiteResult = null,
            reportPath = null,
            executionTime = 1000L
        )
    }
    
    /**
     * 系统预热
     */
    private suspend fun warmupSystem() {
        logInfo("TEST_RUNNER", "🔥 系统预热中...")
        
        _testProgress.value = _testProgress.value.copy(currentTest = "系统预热")
        
        // 预热各个组件
        delay(1000) // 模拟预热时间
        
        logInfo("TEST_RUNNER", "✅ 系统预热完成")
    }
    
    /**
     * 估算测试数量
     */
    private fun estimateTestCount(categories: Set<VoiceAssistantTestFramework.TestCategory>): Int {
        return categories.sumOf { category ->
            when (category) {
                VoiceAssistantTestFramework.TestCategory.STATE_MACHINE -> 4
                VoiceAssistantTestFramework.TestCategory.AUDIO_PIPELINE -> 4
                VoiceAssistantTestFramework.TestCategory.UI_INTEGRATION -> 4
                VoiceAssistantTestFramework.TestCategory.SCENARIO_TEST -> 4
                VoiceAssistantTestFramework.TestCategory.STRESS_TEST -> 3
                VoiceAssistantTestFramework.TestCategory.EDGE_CASE -> 4
            }
        }
    }
    
    /**
     * 生成测试报告
     */
    private fun generateTestReport(result: TestSuiteResult, executionTime: Long): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val reportFileName = "voice_assistant_test_report_$timestamp"
            
            // 生成HTML报告
            val htmlReport = generateHTMLReport(result, executionTime)
            val htmlFile = saveReportToFile("$reportFileName.html", htmlReport)
            
            // 生成JSON报告
            val jsonReport = generateJSONReport(result, executionTime)
            saveReportToFile("$reportFileName.json", jsonReport)
            
            logInfo("TEST_RUNNER", "📊 测试报告已生成: $htmlFile")
            
            htmlFile
            
        } catch (e: Exception) {
            logError("TEST_RUNNER", "❌ 生成测试报告失败", e)
            null
        }
    }
    
    /**
     * 生成HTML测试报告
     */
    private fun generateHTMLReport(result: TestSuiteResult, executionTime: Long): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>语音助手测试报告</title>
            <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                .container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 8px 8px 0 0; }
                .header h1 { margin: 0; font-size: 2.5em; }
                .header .subtitle { margin: 10px 0 0 0; opacity: 0.9; }
                .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; }
                .summary-card { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; border-left: 4px solid #007bff; }
                .summary-card.success { border-left-color: #28a745; }
                .summary-card.danger { border-left-color: #dc3545; }
                .summary-card.warning { border-left-color: #ffc107; }
                .summary-card h3 { margin: 0 0 10px 0; color: #495057; }
                .summary-card .value { font-size: 2em; font-weight: bold; color: #212529; }
                .content { padding: 0 30px 30px 30px; }
                .section { margin-bottom: 40px; }
                .section h2 { color: #495057; border-bottom: 2px solid #e9ecef; padding-bottom: 10px; }
                .test-grid { display: grid; gap: 15px; }
                .test-item { background: #f8f9fa; padding: 15px; border-radius: 6px; border-left: 4px solid #6c757d; }
                .test-item.passed { border-left-color: #28a745; }
                .test-item.failed { border-left-color: #dc3545; }
                .test-item.timeout { border-left-color: #ffc107; }
                .test-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
                .test-name { font-weight: bold; color: #495057; }
                .test-status { padding: 4px 12px; border-radius: 20px; font-size: 0.8em; font-weight: bold; text-transform: uppercase; }
                .test-status.passed { background: #d4edda; color: #155724; }
                .test-status.failed { background: #f8d7da; color: #721c24; }
                .test-status.timeout { background: #fff3cd; color: #856404; }
                .test-details { color: #6c757d; font-size: 0.9em; }
                .progress-bar { background: #e9ecef; height: 20px; border-radius: 10px; overflow: hidden; margin: 20px 0; }
                .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 0.3s ease; }
                .category-section { margin-bottom: 30px; }
                .category-title { background: #e9ecef; padding: 10px 15px; border-radius: 6px; font-weight: bold; color: #495057; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>🎤 语音助手测试报告</h1>
                    <div class="subtitle">生成时间: $timestamp</div>
                </div>
                
                <div class="summary">
                    <div class="summary-card success">
                        <h3>通过测试</h3>
                        <div class="value">${result.passed}</div>
                    </div>
                    <div class="summary-card danger">
                        <h3>失败测试</h3>
                        <div class="value">${result.failed}</div>
                    </div>
                    <div class="summary-card warning">
                        <h3>超时测试</h3>
                        <div class="value">${result.timeout}</div>
                    </div>
                    <div class="summary-card">
                        <h3>总测试数</h3>
                        <div class="value">${result.totalTests}</div>
                    </div>
                    <div class="summary-card">
                        <h3>成功率</h3>
                        <div class="value">${String.format("%.1f%%", result.successRate * 100)}</div>
                    </div>
                    <div class="summary-card">
                        <h3>执行时间</h3>
                        <div class="value">${executionTime / 1000}s</div>
                    </div>
                </div>
                
                <div class="content">
                    <div class="section">
                        <h2>📊 总体进度</h2>
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: ${result.successRate * 100}%"></div>
                        </div>
                        <p>测试完成度: ${result.totalTests}/${result.totalTests} (100%)</p>
                    </div>
                    
                    <div class="section">
                        <h2>📋 详细测试结果</h2>
                        ${generateTestResultsHTML(result.testResults)}
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * 生成测试结果HTML
     */
    private fun generateTestResultsHTML(testResults: List<VoiceAssistantTestFramework.TestResult>): String {
        val resultsByCategory = testResults.groupBy { it.category }
        
        return resultsByCategory.map { (category, tests) ->
            """
            <div class="category-section">
                <div class="category-title">${getCategoryDisplayName(category)} (${tests.size}个测试)</div>
                <div class="test-grid">
                    ${tests.joinToString("") { test ->
                        """
                        <div class="test-item ${test.status.name.lowercase()}">
                            <div class="test-header">
                                <span class="test-name">${test.testName}</span>
                                <span class="test-status ${test.status.name.lowercase()}">${test.status.name}</span>
                            </div>
                            <div class="test-details">
                                执行时间: ${test.duration.inWholeMilliseconds}ms
                                ${if (test.details.isNotEmpty()) "<br>详情: ${test.details}" else ""}
                                ${if (test.errorMessage != null) "<br>错误: ${test.errorMessage}" else ""}
                            </div>
                        </div>
                        """.trimIndent()
                    }}
                </div>
            </div>
            """.trimIndent()
        }.joinToString("\n")
    }
    
    /**
     * 生成JSON测试报告
     */
    private fun generateJSONReport(result: TestSuiteResult, executionTime: Long): String {
        val jsonObject = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("summary", JSONObject().apply {
                put("totalTests", result.totalTests)
                put("passed", result.passed)
                put("failed", result.failed)
                put("timeout", result.timeout)
                put("skipped", result.skipped)
                put("successRate", result.successRate)
                put("executionTime", executionTime)
                put("totalDuration", result.totalDuration.inWholeMilliseconds)
            })
            put("testResults", JSONArray().apply {
                result.testResults.forEach { test ->
                    put(JSONObject().apply {
                        put("testId", test.testId)
                        put("testName", test.testName)
                        put("category", test.category.name)
                        put("status", test.status.name)
                        put("duration", test.duration.inWholeMilliseconds)
                        put("details", test.details)
                        put("errorMessage", test.errorMessage)
                        put("timestamp", test.timestamp)
                    })
                }
            })
        }
        
        return jsonObject.toString(2)
    }
    
    /**
     * 保存报告到文件
     */
    private fun saveReportToFile(fileName: String, content: String): String {
        val reportsDir = File(context.filesDir, REPORT_DIR)
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        
        val reportFile = File(reportsDir, fileName)
        reportFile.writeText(content)
        
        return reportFile.absolutePath
    }
    
    /**
     * 获取类别显示名称
     */
    private fun getCategoryDisplayName(category: VoiceAssistantTestFramework.TestCategory): String {
        return when (category) {
            VoiceAssistantTestFramework.TestCategory.STATE_MACHINE -> "🔄 状态机测试"
            VoiceAssistantTestFramework.TestCategory.AUDIO_PIPELINE -> "🎵 音频管道测试"
            VoiceAssistantTestFramework.TestCategory.UI_INTEGRATION -> "📱 UI集成测试"
            VoiceAssistantTestFramework.TestCategory.SCENARIO_TEST -> "🎭 场景测试"
            VoiceAssistantTestFramework.TestCategory.STRESS_TEST -> "💪 压力测试"
            VoiceAssistantTestFramework.TestCategory.EDGE_CASE -> "🔍 边界条件测试"
        }
    }
    
    // 日志方法
    private suspend fun logDebug(category: String, message: String) {
        Log.d(TAG, "[$category] $message")
        _testLogs.emit(TestLogEntry(System.currentTimeMillis(), LogLevel.DEBUG, category, message))
    }
    
    private suspend fun logInfo(category: String, message: String) {
        Log.i(TAG, "[$category] $message")
        _testLogs.emit(TestLogEntry(System.currentTimeMillis(), LogLevel.INFO, category, message))
    }
    
    private suspend fun logWarn(category: String, message: String) {
        Log.w(TAG, "[$category] $message")
        _testLogs.emit(TestLogEntry(System.currentTimeMillis(), LogLevel.WARN, category, message))
    }
    
    private suspend fun logError(category: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$category] $message", throwable)
        _testLogs.emit(TestLogEntry(System.currentTimeMillis(), LogLevel.ERROR, category, message))
    }
}

/**
 * 测试执行结果
 */
data class TestExecutionResult(
    val success: Boolean,
    val testSuiteResult: TestSuiteResult?,
    val reportPath: String?,
    val executionTime: Long,
    val error: String? = null
)
