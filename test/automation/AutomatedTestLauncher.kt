package org.stypox.dicio.test.automation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.stypox.dicio.audio.AudioResourceCoordinator
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.ui.floating.FloatingWindowViewModel
import javax.inject.Inject

/**
 * 自动化测试启动器
 * 
 * 提供简单的API来启动和管理语音助手测试
 */
@HiltViewModel
class AutomatedTestLauncher @Inject constructor(
    private val context: Context,
    private val audioCoordinator: AudioResourceCoordinator,
    private val skillEvaluator: SkillEvaluator,
    private val sttInputDevice: SttInputDeviceWrapper,
    private val wakeDevice: WakeDeviceWrapper,
    private val skillContext: SkillContextInternal
) : ViewModel() {
    
    companion object {
        private const val TAG = "AutomatedTestLauncher"
    }
    
    private var testFramework: VoiceAssistantTestFramework? = null
    private var testRunner: TestRunner? = null
    
    private val _testState = MutableStateFlow(TestState.IDLE)
    val testState: StateFlow<TestState> = _testState.asStateFlow()
    
    private val _testResults = MutableStateFlow<TestExecutionResult?>(null)
    val testResults: StateFlow<TestExecutionResult?> = _testResults.asStateFlow()
    
    enum class TestState {
        IDLE,
        INITIALIZING,
        RUNNING,
        COMPLETED,
        ERROR
    }
    
    /**
     * 初始化测试系统
     */
    fun initializeTestSystem(floatingWindowViewModel: FloatingWindowViewModel) {
        if (testFramework != null) {
            Log.w(TAG, "测试系统已初始化")
            return
        }
        
        Log.i(TAG, "🔧 初始化自动化测试系统...")
        
        testFramework = VoiceAssistantTestFramework(
            context = context,
            audioCoordinator = audioCoordinator,
            skillEvaluator = skillEvaluator,
            sttInputDevice = sttInputDevice,
            wakeDevice = wakeDevice,
            floatingWindowViewModel = floatingWindowViewModel
        )
        
        testRunner = TestRunner(context, testFramework!!)
        
        Log.i(TAG, "✅ 测试系统初始化完成")
    }
    
    /**
     * 运行完整测试套件
     */
    fun runFullTestSuite() {
        if (testFramework == null || testRunner == null) {
            Log.e(TAG, "❌ 测试系统未初始化")
            _testState.value = TestState.ERROR
            return
        }
        
        if (_testState.value == TestState.RUNNING) {
            Log.w(TAG, "⚠️ 测试正在运行中")
            return
        }
        
        viewModelScope.launch {
            try {
                _testState.value = TestState.RUNNING
                _testResults.value = null
                
                Log.i(TAG, "🚀 开始运行完整测试套件...")
                
                val result = testRunner!!.runTests()
                
                _testResults.value = result
                _testState.value = TestState.COMPLETED
                
                Log.i(TAG, "✅ 测试套件执行完成，成功率: ${result.testSuiteResult?.successRate?.times(100) ?: 0}%")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 测试执行异常", e)
                _testState.value = TestState.ERROR
                _testResults.value = TestExecutionResult(
                    success = false,
                    testSuiteResult = null,
                    reportPath = null,
                    executionTime = 0L,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * 运行特定类别的测试
     */
    fun runCategoryTest(category: VoiceAssistantTestFramework.TestCategory) {
        if (testRunner == null) {
            Log.e(TAG, "❌ 测试系统未初始化")
            return
        }
        
        viewModelScope.launch {
            try {
                _testState.value = TestState.RUNNING
                
                Log.i(TAG, "🎯 运行${category.name}类别测试...")
                
                val result = testRunner!!.runCategoryTests(category)
                
                _testResults.value = result
                _testState.value = TestState.COMPLETED
                
                Log.i(TAG, "✅ ${category.name}测试完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 类别测试执行异常", e)
                _testState.value = TestState.ERROR
            }
        }
    }
    
    /**
     * 运行快速验证测试
     */
    fun runQuickValidation() {
        viewModelScope.launch {
            try {
                _testState.value = TestState.RUNNING
                
                Log.i(TAG, "⚡ 运行快速验证测试...")
                
                // 运行核心功能测试
                val categories = setOf(
                    VoiceAssistantTestFramework.TestCategory.STATE_MACHINE,
                    VoiceAssistantTestFramework.TestCategory.AUDIO_PIPELINE
                )
                
                val result = testRunner!!.runTests(categories)
                
                _testResults.value = result
                _testState.value = TestState.COMPLETED
                
                Log.i(TAG, "✅ 快速验证完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 快速验证异常", e)
                _testState.value = TestState.ERROR
            }
        }
    }
    
    /**
     * 停止当前测试
     */
    fun stopCurrentTest() {
        if (_testState.value == TestState.RUNNING) {
            Log.i(TAG, "🛑 停止当前测试...")
            _testState.value = TestState.IDLE
            // 这里可以添加实际的测试停止逻辑
        }
    }
    
    /**
     * 清理测试系统
     */
    fun cleanup() {
        Log.i(TAG, "🧹 清理测试系统...")
        
        testFramework?.cleanup()
        testFramework = null
        testRunner = null
        
        _testState.value = TestState.IDLE
        _testResults.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}

/**
 * 测试配置
 */
data class TestConfiguration(
    val categories: Set<VoiceAssistantTestFramework.TestCategory> = VoiceAssistantTestFramework.TestCategory.values().toSet(),
    val generateReport: Boolean = true,
    val enableStressTests: Boolean = false,
    val maxTestDuration: Long = 300000L, // 5分钟
    val logLevel: TestRunner.LogLevel = TestRunner.LogLevel.INFO
)

/**
 * 简化的测试API
 */
object VoiceAssistantTester {
    
    private var launcher: AutomatedTestLauncher? = null
    
    /**
     * 初始化测试器
     */
    fun initialize(launcher: AutomatedTestLauncher) {
        this.launcher = launcher
    }
    
    /**
     * 运行所有测试
     */
    fun runAllTests() {
        launcher?.runFullTestSuite() ?: Log.e("VoiceAssistantTester", "测试器未初始化")
    }
    
    /**
     * 运行状态机测试
     */
    fun runStateMachineTests() {
        launcher?.runCategoryTest(VoiceAssistantTestFramework.TestCategory.STATE_MACHINE)
            ?: Log.e("VoiceAssistantTester", "测试器未初始化")
    }
    
    /**
     * 运行音频管道测试
     */
    fun runAudioPipelineTests() {
        launcher?.runCategoryTest(VoiceAssistantTestFramework.TestCategory.AUDIO_PIPELINE)
            ?: Log.e("VoiceAssistantTester", "测试器未初始化")
    }
    
    /**
     * 运行UI测试
     */
    fun runUITests() {
        launcher?.runCategoryTest(VoiceAssistantTestFramework.TestCategory.UI_INTEGRATION)
            ?: Log.e("VoiceAssistantTester", "测试器未初始化")
    }
    
    /**
     * 运行快速验证
     */
    fun runQuickValidation() {
        launcher?.runQuickValidation() ?: Log.e("VoiceAssistantTester", "测试器未初始化")
    }
    
    /**
     * 获取测试状态
     */
    fun getTestState(): StateFlow<AutomatedTestLauncher.TestState>? {
        return launcher?.testState
    }
    
    /**
     * 获取测试结果
     */
    fun getTestResults(): StateFlow<TestExecutionResult?>? {
        return launcher?.testResults
    }
}
