package com.ai.voice.io.wake.onnx

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object WakeWordTestRunner {
    private const val TAG = "WakeWordTestRunner"
    
    fun checkAndRunTests(context: Context, wakeDevice: HiNudgeOnnxV8WakeDevice) {
        val triggerFile = File(context.getExternalFilesDir(null), "run_wake_test")
        
        if (triggerFile.exists()) {
            Log.i(TAG, "检测到测试触发文件，开始执行测试...")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    runTests(context, wakeDevice)
                    triggerFile.delete()
                    Log.i(TAG, "测试完成，删除触发文件")
                } catch (e: Exception) {
                    Log.e(TAG, "测试执行失败", e)
                }
            }
        }
    }
    
    private fun runTests(context: Context, wakeDevice: HiNudgeOnnxV8WakeDevice) {
        val tester = WakeWordTester(context, wakeDevice)
        val testBaseDir = File(context.getExternalFilesDir(null), "wake_test")
        
        if (!testBaseDir.exists()) {
            Log.e(TAG, "测试目录不存在: ${testBaseDir.absolutePath}")
            Log.i(TAG, "请创建目录并放入WAV文件: $testBaseDir")
            return
        }
        
        val positiveDir = File(testBaseDir, "positive")
        val negativeDir = File(testBaseDir, "negative")
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "开始唤醒词测试")
        Log.i(TAG, "========================================")
        
        // 测试正样本
        if (positiveDir.exists()) {
            Log.i(TAG, "测试正样本...")
            val positiveResult = tester.testDirectory(positiveDir, maxSamples = 20)
            val recall = positiveResult["rate"] as Float
            Log.i(TAG, "正样本结果: ${positiveResult["detected"]}/${positiveResult["total"]}, 召回率: ${"%.1f".format(recall)}%")
        } else {
            Log.w(TAG, "正样本目录不存在: ${positiveDir.absolutePath}")
        }
        
        // 测试负样本
        if (negativeDir.exists()) {
            Log.i(TAG, "测试负样本...")
            val negativeResult = tester.testDirectory(negativeDir, maxSamples = 20)
            val falsePositiveRate = negativeResult["rate"] as Float
            Log.i(TAG, "负样本结果: ${negativeResult["detected"]}/${negativeResult["total"]}, 误报率: ${"%.1f".format(falsePositiveRate)}%")
        } else {
            Log.w(TAG, "负样本目录不存在: ${negativeDir.absolutePath}")
        }
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "测试完成")
        Log.i(TAG, "========================================")
    }
}

