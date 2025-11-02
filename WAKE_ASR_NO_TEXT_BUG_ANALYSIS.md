# Wake后ASR无文本输出Bug分析

## 问题现象

**症状：**
- 唤醒成功后开始ASR监听
- 检测到语音（有2087ms音频数据）
- 但完全没有任何识别结果（partialText=''）
- UI直接返回IDLE，没有保持LISTENING状态
- **这是概率性问题，多轮对话后必现**

## 日志证据

```log
02:29:55.645 D SenseVoiceInputDevice: 🎙️ 开始语音监听...
02:29:55.646 I AudioResourceManager: ✅ [ASR_DEVICE] 成功获取麦克风资源
02:29:55.974 D SenseVoiceInputDevice: 🎤 检测到语音开始
02:29:58.045 D SenseVoiceInputDevice: 🔇 检测到静音超时(2000ms)，停止监听 (partialText='')
02:29:58.048 E SenseVoiceInputDevice: ❌ AudioRecord状态异常
02:29:58.061 D SenseVoiceInputDevice: 🚀 开始最终识别，音频长度: 36864样本，语音时长: 2087ms
```

**关键异常：**
1. ❌ 从头到尾没有任何 `"SenseVoice识别结果"` 日志
2. ❌ partialText始终为空字符串
3. ❌ 有2087ms（36864样本）的音频数据，但没有产生任何文本
4. ❌ Final识别也没有输出结果

---

## 根本原因

### 🔴 Bug #1: `lastPartialRecognitionTime` 未重置

**位置：** `SenseVoiceInputDevice.kt` 第1178-1201行

**问题代码：**
```kotlin
private fun resetVadState() {
    speechDetected = false
    speechStartTime = 0L
    lastSpeechTime = 0L
    synchronized(audioBuffer) {
        audioBuffer.clear()
        bufferOffset = 0
    }
    partialText = ""
    isPartialResultAdded = false
    
    // 🆕 重置高分提前结束相关状态
    lastStablePartialText = ""
    partialStableCount = 0
    stablePartialConfirmTime = 0L
    isWaitingForEarlyStop = false
    
    // 重置VAD状态
    try {
        vad?.reset()
    } catch (e: Exception) {
        Log.w(TAG, "重置VAD状态失败", e)
    }
    
    // ❌ 缺少：lastPartialRecognitionTime = 0L
}
```

**影响：**
- 上一轮对话的 `lastPartialRecognitionTime` 残留
- 如果时间戳不匹配，可能导致 `elapsed` 计算异常
- Partial识别可能永远不会被触发

**触发条件检查（第850-853行）：**
```kotlin
val elapsed = currentTime - lastPartialRecognitionTime
if (elapsed > PARTIAL_RECOGNITION_COOLDOWN_MS && audioBuffer.size >= SAMPLE_RATE / 2) {
    performPartialRecognition()
}
```

如果 `lastPartialRecognitionTime` 是上一轮的时间戳，elapsed可能不符合预期。

---

### 🔴 Bug #2: SenseVoiceRecognizer状态污染

**观察：**
- 日志中没有看到 `"⏭️ Recognizer不可用"` → recognizer不是null
- 但也没有看到 `"SenseVoice识别结果"` → recognize()返回了空或异常

**可能原因：**

**SenseVoiceRecognizer.kt** 第148-212行分析：
```kotlin
suspend fun recognize(audioData: FloatArray): String {
    return withContext(Dispatchers.IO) {
        recognitionMutex.withLock {
            try {
                if (audioData.isEmpty()) {  // ✅ 不是这个原因（有36864样本）
                    return@withLock ""
                }
                
                val currentRecognizer = recognizer
                if (currentRecognizer == null) {  // ✅ 不是这个原因（没看到警告日志）
                    Log.w(TAG, "⚠️ Recognizer已被释放，跳过识别")
                    return@withLock ""
                }
                
                val stream = try {
                    val createdStream = currentRecognizer.createStream()
                    createdStream
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 创建stream失败", e)  // ❓ 可能是这里
                    return@withLock ""
                }
                
                try {
                    stream.acceptWaveform(audioDataCopy, SAMPLE_RATE)
                    currentRecognizer.decode(stream)
                    val result = currentRecognizer.getResult(stream)
                    
                    stream.release()
                    
                    val rawText = result.text.trim()
                    val filteredText = filterToKorean(rawText)
                    
                    DebugLogger.logRecognition(TAG, "SenseVoice识别结果: \"$filteredText\"")
                    
                    filteredText
                } catch (e: Exception) {
                    stream.release()
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "SenseVoice识别过程异常", e)  // ❓ 或者是这里
                ""
            }
        }
    }
}
```

**多轮对话后可能的问题：**
1. **Stream资源泄漏**：虽然代码有释放，但如果前一轮异常退出，可能有残留
2. **Recognizer内部状态异常**：多次调用后可能进入异常状态
3. **音频格式不匹配**：audioBuffer数据可能被污染
4. **异常被静默吞掉**：第206行catch了所有异常但只返回空字符串

---

### 🔴 Bug #3: audioBuffer可能被污染

**位置：** `SenseVoiceInputDevice.kt` 第824-831行

```kotlin
synchronized(audioBuffer) {
    audioBuffer.addAll(samples.toList())
    // 如果缓冲区太大，移除旧数据
    while (audioBuffer.size > maxBufferSize) {
        audioBuffer.removeAt(0)
        if (bufferOffset > 0) bufferOffset--
    }
}
```

**问题：**
- `resetVadState()` 重置了 `audioBuffer` 和 `bufferOffset`
- 但如果在重置后，还有残留的音频帧在 `samplesChannel` 中
- 这些帧可能是上一轮对话的数据
- 导致新一轮识别时，开始的数据是脏数据

---

## 多轮对话后为什么必现？

### 累积效应分析

1. **第1轮对话：** 正常
   - lastPartialRecognitionTime = 1000ms
   - 识别成功，结束

2. **第2轮对话：** 正常
   - lastPartialRecognitionTime = 15000ms
   - 识别成功，结束

3. **第3轮对话：** 开始出现问题
   - lastPartialRecognitionTime = 50000ms（上一轮残留）
   - 当前时间可能是 System.currentTimeMillis() = 1673663395645ms
   - elapsed = 1673663395645 - 50000 = 一个巨大的数字 ✅ 满足条件
   - 但是... **问题可能不在这里**

### 真正的问题：Stream累积或Recognizer状态

**假设：**
1. 第1-2轮对话正常，stream正确释放
2. 第3轮对话时，某个异常导致stream没有释放
3. 第4轮对话时，recognizer内部已经有残留的stream
4. 创建新stream失败或识别失败
5. 异常被catch，返回空字符串
6. 没有日志输出（因为在catch块中）

---

## 修复方案

### 修复1：重置 lastPartialRecognitionTime ⭐⭐⭐

**文件：** `SenseVoiceInputDevice.kt` 第1178行

```kotlin
private fun resetVadState() {
    speechDetected = false
    speechStartTime = 0L
    lastSpeechTime = 0L
    
    // 🔥 修复：重置Partial识别时间戳
    lastPartialRecognitionTime = 0L
    
    synchronized(audioBuffer) {
        audioBuffer.clear()
        bufferOffset = 0
    }
    partialText = ""
    isPartialResultAdded = false
    
    // 重置高分提前结束相关状态
    lastStablePartialText = ""
    partialStableCount = 0
    stablePartialConfirmTime = 0L
    isWaitingForEarlyStop = false
    
    // 重置VAD状态
    try {
        vad?.reset()
    } catch (e: Exception) {
        Log.w(TAG, "重置VAD状态失败", e)
    }
}
```

---

### 修复2：增强异常日志 ⭐⭐

**文件：** `SenseVoiceRecognizer.kt` 第206-209行

**修改前：**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "SenseVoice识别过程异常", e)
    ""
}
```

**修改后：**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "❌ SenseVoice识别过程异常", e)
    Log.e(TAG, "   音频数据长度: ${audioData.size}")
    Log.e(TAG, "   Recognizer状态: ${recognizer != null}")
    e.printStackTrace()
    ""
}
```

**同时在第173-175行增强日志：**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "❌ 创建stream失败 (多轮对话后可能资源泄漏)", e)
    e.printStackTrace()
    return@withLock ""
}
```

---

### 修复3：确保samplesChannel清空 ⭐

**文件：** `SenseVoiceInputDevice.kt` 第1178行

```kotlin
private fun resetVadState() {
    speechDetected = false
    speechStartTime = 0L
    lastSpeechTime = 0L
    lastPartialRecognitionTime = 0L  // 🆕
    
    synchronized(audioBuffer) {
        audioBuffer.clear()
        bufferOffset = 0
    }
    
    // 🔥 清空音频通道，防止旧数据残留
    samplesChannel.cancel()
    samplesChannel = Channel(capacity = Channel.UNLIMITED)
    
    partialText = ""
    isPartialResultAdded = false
    
    lastStablePartialText = ""
    partialStableCount = 0
    stablePartialConfirmTime = 0L
    isWaitingForEarlyStop = false
    
    try {
        vad?.reset()
    } catch (e: Exception) {
        Log.w(TAG, "重置VAD状态失败", e)
    }
}
```

---

### 修复4：添加Recognizer健康检查 ⭐⭐

**文件：** `SenseVoiceInputDevice.kt` 新增方法

```kotlin
/**
 * 检查并重置Recognizer状态（多轮对话后可能需要）
 */
private suspend fun checkAndResetRecognizerIfNeeded() {
    recognizerMutex.withLock {
        val recognizer = senseVoiceRecognizer
        if (recognizer == null) {
            Log.w(TAG, "⚠️ Recognizer为null，尝试重新初始化")
            initializeComponents()
            return
        }
        
        // 测试识别器是否正常工作
        try {
            val testAudio = FloatArray(SAMPLE_RATE) { 0f }
            val testResult = recognizer.recognize(testAudio)
            Log.d(TAG, "✅ Recognizer健康检查通过")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Recognizer健康检查失败，需要重新初始化", e)
            // 重新初始化
            senseVoiceRecognizer?.destroy()
            senseVoiceRecognizer = null
            initializeComponents()
        }
    }
}
```

**在 startListening() 开始时调用：**
```kotlin
private fun startListening(): Boolean {
    if (!isInitialized.get() || senseVoiceRecognizer == null) {
        Log.e(TAG, "❌ SenseVoice未准备好，无法开始监听")
        return false
    }
    
    // 🔥 多轮对话后检查recognizer健康状态
    scope.launch {
        checkAndResetRecognizerIfNeeded()
    }
    
    // ... 其余代码
}
```

---

## 验证方法

### 测试用例

```bash
# 连续进行5轮对话
for i in {1..5}; do
    echo "=== 第${i}轮对话 ==="
    # 唤醒
    adb shell "am broadcast -a com.ai.voice.test.WAKE"
    sleep 1
    
    # 说话（播放测试音频）
    adb shell "am broadcast -a com.ai.voice.test.SPEAK --es text '화면켜줘'"
    sleep 3
    
    # 检查日志
    adb logcat -d | grep "SenseVoice识别结果" | tail -1
done
```

### 关键日志检查

```bash
# 修复前：多轮对话后应该看到
❌ 没有"SenseVoice识别结果"日志
❌ partialText='' 

# 修复后：每轮对话都应该看到
✅ D SenseVoiceInputDevice: 🔄 状态重置: lastPartialRecognitionTime=0
✅ D 🎤[SenseVoiceRecognizer]: SenseVoice识别结果: "화면 켜줘"
✅ partialText有内容
```

---

## 优先级

| 修复 | 优先级 | 难度 | 影响 |
|------|--------|------|------|
| 修复1: 重置lastPartialRecognitionTime | 🔴 高 | 低 | 直接解决时间戳问题 |
| 修复2: 增强异常日志 | 🟡 中 | 低 | 帮助诊断根本原因 |
| 修复3: 清空samplesChannel | 🟡 中 | 中 | 防止脏数据 |
| 修复4: Recognizer健康检查 | 🟢 低 | 高 | 兜底保护机制 |

**建议执行顺序：**
1. 先做修复1（最简单，可能直接解决问题）
2. 再做修复2（增强日志，观察是否还有其他问题）
3. 如果问题仍存在，做修复3
4. 如果仍然不行，做修复4

---

## 补充调试

### 临时调试日志

在 `performPartialRecognition()` 开始处添加：

```kotlin
private suspend fun performPartialRecognition() {
    try {
        val currentTime = System.currentTimeMillis()
        
        // 🔥 临时调试日志
        Log.d(TAG, """
            🔍 [DEBUG] performPartialRecognition 被调用
               - currentTime: $currentTime
               - lastPartialRecognitionTime: $lastPartialRecognitionTime
               - elapsed: ${currentTime - lastPartialRecognitionTime}ms
               - audioBuffer.size: ${audioBuffer.size}
               - recognizer: ${senseVoiceRecognizer != null}
        """.trimIndent())
        
        lastPartialRecognitionTime = currentTime
        // ... 其余代码
    }
}
```

---

## 总结

**根本原因：**
- `resetVadState()` 未重置 `lastPartialRecognitionTime`
- 可能还有 SenseVoiceRecognizer 状态污染
- samplesChannel 可能有残留数据

**核心修复：**
- 在 `resetVadState()` 中添加 `lastPartialRecognitionTime = 0L`
- 增强异常日志以诊断深层问题
- 清空 samplesChannel 防止脏数据

**预期效果：**
- 每轮对话都能正常识别
- 不再出现 partialText='' 的情况
- 日志中能看到完整的识别过程

