package com.ai.voice.util

import android.util.Log

/**
 * 自动化测试日志工具类
 * 
 * 用于输出统一格式的测试日志，供自动化测试脚本解析
 * 所有日志使用 "AutoTest" 标签，方便 adb logcat 过滤
 */
object AutoTestLogger {
    private const val TAG = "AutoTest"
    
    /**
     * 记录UI状态变化 - 关键日志，用于检测状态卡住
     */
    fun logStateChange(fromState: String, toState: String, asrRunning: Boolean) {
        Log.i(TAG, "[STATE] $fromState -> $toState (asr=$asrRunning)")
        when (toState) {
            "IDLE" -> Log.i(TAG, "[STATE_IDLE]")
            "LISTENING" -> Log.i(TAG, "[STATE_LISTENING]")
            "SPEAKING" -> Log.i(TAG, "[STATE_SPEAKING]")
            "THINKING" -> Log.i(TAG, "[STATE_THINKING]")
            "ERROR" -> Log.i(TAG, "[STATE_ERROR]")
        }
    }
    


    /**
     * 记录唤醒词检测成功
     */
    fun logWakeupDetected() {
        Log.i(TAG, "[WAKE] detected")
    }
    
    /**
     * 记录ASR识别结果
     * @param text 识别的文本
     */
    fun logAsrResult(text: String) {
        Log.i(TAG, "ASR结果: $text")
    }
    
    /**
     * 记录技能执行
     * @param skillId 技能ID
     * @param result 执行结果描述
     * @param subSkillId 子技能ID（可选，如app_launcher的google、browser等）
     */
    fun logSkillExecuted(skillId: String, result: String, subSkillId: String? = null) {
        val skillDisplayName = if (subSkillId != null) {
            "$skillId:$subSkillId"
        } else {
            skillId
        }
        Log.i(TAG, "技能执行: $skillDisplayName, 结果: $result")
    }
    
    /**
     * 记录自动化测试启动
     */
    fun logTestStarted() {
        Log.i(TAG, "收到自动化测试启动指令")
    }
    
    /**
     * 记录TTS开始播放
     * @param text TTS文本内容
     */
    fun logTtsStarted(text: String) {
        Log.i(TAG, "TTS开始: $text")
    }
    
    /**
     * 记录TTS播放完成
     */
    fun logTtsCompleted() {
        Log.i(TAG, "TTS完成")
    }
    
    /**
     * 记录测试错误
     * @param message 错误信息
     */
    fun logTestError(message: String) {
        Log.e(TAG, "测试错误: $message")
    }
    
    /**
     * 记录用户点击悬浮球
     */
    fun logOrbClicked() {
        Log.i(TAG, "悬浮球被点击")
    }
    
    /**
     * 记录ASR开始监听
     */
    fun logAsrListeningStarted() {
        Log.i(TAG, "ASR开始监听")
    }
    
    /**
     * 记录ASR停止监听
     */
    fun logAsrListeningStopped() {
        Log.i(TAG, "ASR停止监听")
    }
    
    /**
     * 记录进入唤醒监听状态
     */
    fun logWakeListeningStarted() {
        Log.i(TAG, "进入唤醒监听状态")
    }
    
    /**
     * 记录退出唤醒监听状态
     */
    fun logWakeListeningStopped() {
        Log.i(TAG, "退出唤醒监听状态")
    }
    
    /**
     * 记录测试集开始
     */
    fun logTestSetStarted(testSetName: String, testType: String) {
        Log.i(TAG, "测试集开始: $testSetName, 类型: $testType")
    }
    
    /**
     * 记录测试暂停
     */
    fun logTestPaused(currentIndex: Int) {
        Log.i(TAG, "测试暂停: 当前索引=$currentIndex")
    }
    
    /**
     * 记录测试继续
     */
    fun logTestResumed(currentIndex: Int) {
        Log.i(TAG, "测试继续: 从索引=$currentIndex")
    }
    
    /**
     * 记录测试停止
     */
    fun logTestStopped(currentIndex: Int) {
        Log.i(TAG, "测试停止: 当前索引=$currentIndex")
    }
    
    /**
     * 记录测试完成
     */
    fun logTestCompleted(totalSamples: Int) {
        Log.i(TAG, "测试完成: 总样本数=$totalSamples")
    }
    
    /**
     * 记录测试样本开始
     */
    fun logTestSampleStarted(sampleId: String, description: String) {
        Log.i(TAG, "测试样本开始: $sampleId - $description")
    }
    
    /**
     * 记录测试样本成功
     */
    fun logTestSampleSuccess(sampleId: String, asrResult: String?, skillResult: String?) {
        Log.i(TAG, "测试样本成功: $sampleId, ASR=$asrResult, 技能=$skillResult")
    }
    
    /**
     * 记录测试样本失败及原因
     */
    fun logTestSampleFailed(sampleId: String, reason: String, expected: String? = null, actual: String? = null) {
        val msg = buildString {
            append("测试样本失败: $sampleId")
            append(", 原因: $reason")
            expected?.let { append(", 期望: $it") }
            actual?.let { append(", 实际: $it") }
        }
        Log.e(TAG, msg)
    }
    
    /**
     * 记录状态卡住 - 测试模式11：发现到问题后立即停止
     */
    fun logStateStuck(state: String, durationMs: Long, asrRunning: Boolean) {
        Log.e(TAG, "[STUCK] state=$state duration=${durationMs}ms asr=$asrRunning")
    }
    
    /**
     * 记录静音超时异常 - ASR后至少要4s才能结束listening状态
     */
    fun logSilenceTimeoutAbnormal(asrTime: Long, listeningEndTime: Long, durationMs: Long) {
        Log.e(TAG, "[SILENCE_TIMEOUT_ABNORMAL] asrTime=$asrTime listeningEndTime=$listeningEndTime duration=${durationMs}ms (<4s)")
    }
    
    /**
     * 记录VAD检测
     */
    fun logVadDetected(isSpeech: Boolean) {
        Log.d(TAG, "[VAD] detected=${if (isSpeech) "SPEECH" else "SILENCE"}")
    }
    
    /**
     * 记录静音超时重置
     */
    fun logSilenceTimeoutReset() {
        Log.d(TAG, "[SILENCE_TIMEOUT] reset")
    }
    
    /**
     * 记录静音超时触发
     */
    fun logSilenceTimeoutTriggered(durationMs: Long) {
        Log.i(TAG, "[SILENCE_TIMEOUT] triggered after ${durationMs}ms")
    }
    
    /**
     * 记录音频数据接收
     */
    fun logAudioDataReceived(samples: Int, isStarted: Boolean) {
        Log.d(TAG, "[AUDIO] received=$samples samples isStarted=$isStarted")
    }
    
    /**
     * 记录ASR启动但未接收音频数据
     */
    fun logAsrStartedButNoAudio(durationMs: Long) {
        Log.w(TAG, "[ASR_NO_AUDIO] isStarted=true but no audio received for ${durationMs}ms")
    }
}

