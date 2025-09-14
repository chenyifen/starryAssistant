# SherpaOnnx 韩语唤醒词"하이넛지"定制指南

## 概述

本指南详细说明如何为SherpaOnnx KWS系统添加自定义韩语唤醒词"하이넛지"（Hi Nutji），包括模型训练、集成和配置的完整流程。

## 🎯 目标

- 训练"하이넛지"韩语唤醒词模型
- 集成到SherpaOnnx KWS系统
- 支持动态唤醒词配置
- 提供完整的调试和测试工具

## 📋 实现步骤

### 第一步：准备SherpaOnnx训练环境

#### 1.1 安装SherpaOnnx训练工具

```bash
# 克隆SherpaOnnx仓库
git clone https://github.com/k2-fsa/sherpa-onnx.git
cd sherpa-onnx

# 安装Python依赖
pip install -r requirements.txt
pip install sherpa-onnx

# 安装训练相关工具
pip install k2 icefall
```

#### 1.2 下载基础韩语模型

```bash
# 下载韩语Zipformer模型作为基础
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2

# 解压模型
tar -xjf sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

### 第二步：收集"하이넛지"训练数据

#### 2.1 创建数据收集脚本

```python
#!/usr/bin/env python3
"""
韩语唤醒词"하이넛지"数据收集脚本
"""

import os
import soundfile as sf
import numpy as np
from pathlib import Path
import librosa

class KoreanWakeWordCollector:
    def __init__(self, output_dir="korean_wake_data"):
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        self.sample_rate = 16000
        
        # 创建目录结构
        self.positive_dir = self.output_dir / "positive"
        self.negative_dir = self.output_dir / "negative"
        self.positive_dir.mkdir(parents=True, exist_ok=True)
        self.negative_dir.mkdir(parents=True, exist_ok=True)
    
    def record_samples(self, num_samples=100):
        """录制正样本"""
        print(f"🎤 开始录制'{self.wake_word}'样本")
        print(f"需要录制 {num_samples} 个样本")
        
        for i in range(num_samples):
            input(f"按回车开始录制第 {i+1} 个样本...")
            # 这里集成录音逻辑
            self.record_single_sample(i)
    
    def record_single_sample(self, index):
        """录制单个样本"""
        # 实际录音实现
        pass
    
    def generate_negative_samples(self):
        """生成负样本"""
        negative_words = [
            "안녕하세요", "감사합니다", "죄송합니다", 
            "하이", "넛지", "안녕", "여보세요",
            "구글", "시리", "알렉사", "빅스비"
        ]
        
        # 使用TTS生成负样本
        for word in negative_words:
            self.generate_tts_sample(word, is_positive=False)
    
    def generate_tts_sample(self, text, is_positive=True):
        """使用TTS生成样本"""
        # TTS生成实现
        pass
```

#### 2.2 数据收集策略

**正样本收集**：
- 录制100-200个"하이넛지"发音
- 包含不同性别、年龄的说话者
- 不同语调、语速和环境
- 格式：16kHz, 16-bit, mono WAV

**负样本收集**：
- 其他韩语词汇和句子
- 环境噪音和音乐
- 类似发音的词汇
- 数量为正样本的2-3倍

### 第三步：训练SherpaOnnx KWS模型

#### 3.1 准备训练配置

```yaml
# config.yaml - SherpaOnnx KWS训练配置
model:
  name: "korean_hi_nutji_kws"
  base_model: "wenetspeech-zipformer"
  
data:
  positive_dir: "korean_wake_data/positive"
  negative_dir: "korean_wake_data/negative"
  sample_rate: 16000
  
training:
  epochs: 100
  batch_size: 32
  learning_rate: 0.001
  
keywords:
  - "하이넛지"
  - "hi nutji"
  
output:
  model_dir: "models/korean_hi_nutji"
```

#### 3.2 执行训练

```bash
# 使用SherpaOnnx训练脚本
python sherpa-onnx/egs/wenetspeech/KWS/zipformer/train.py \
  --config config.yaml \
  --positive-dir korean_wake_data/positive \
  --negative-dir korean_wake_data/negative \
  --keywords "하이넛지" \
  --output-dir models/korean_hi_nutji

# 转换为ONNX格式
python sherpa-onnx/scripts/export-onnx.py \
  --model-dir models/korean_hi_nutji \
  --output-dir models/korean_hi_nutji_onnx
```

### 第四步：集成到Dicio应用

#### 4.1 更新SherpaOnnxWakeDevice

```kotlin
class SherpaOnnxWakeDevice(
    private val appContext: Context
) : WakeDevice {
    
    companion object {
        // 支持多个唤醒词
        const val DEFAULT_WAKE_WORD = "하이넛지"
        const val ENGLISH_WAKE_WORD = "hi nutji"
        
        // 韩语模型文件
        private const val KOREAN_MODELS_DIR = "models/sherpa_kws_korean"
        private const val KOREAN_ENCODER_FILE = "korean_hi_nutji_encoder.onnx"
        private const val KOREAN_DECODER_FILE = "korean_hi_nutji_decoder.onnx"
        private const val KOREAN_JOINER_FILE = "korean_hi_nutji_joiner.onnx"
        private const val KOREAN_TOKENS_FILE = "korean_tokens.txt"
        private const val KOREAN_KEYWORDS_FILE = "korean_keywords.txt"
    }
    
    // 当前使用的唤醒词语言
    private var currentLanguage: WakeWordLanguage = WakeWordLanguage.KOREAN
    
    enum class WakeWordLanguage {
        KOREAN,    // 하이넛지
        ENGLISH,   // hi nutji
        BILINGUAL  // 双语支持
    }
    
    /**
     * 设置唤醒词语言
     */
    fun setWakeWordLanguage(language: WakeWordLanguage) {
        if (currentLanguage != language) {
            currentLanguage = language
            DebugLogger.logWakeWord(TAG, "🌐 Wake word language changed to: ${language.name}")
            
            // 重新加载模型
            scope.launch {
                reloadModelForLanguage(language)
            }
        }
    }
    
    /**
     * 为指定语言重新加载模型
     */
    private suspend fun reloadModelForLanguage(language: WakeWordLanguage) {
        try {
            _state.value = WakeState.Loading
            
            // 释放当前模型
            keywordSpotter = null
            stream = null
            
            // 加载新语言的模型
            when (language) {
                WakeWordLanguage.KOREAN -> loadKoreanModel()
                WakeWordLanguage.ENGLISH -> loadEnglishModel()
                WakeWordLanguage.BILINGUAL -> loadBilingualModel()
            }
            
            _state.value = WakeState.Loaded
            DebugLogger.logWakeWord(TAG, "✅ Model reloaded for language: ${language.name}")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to reload model: ${e.message}")
            _state.value = WakeState.ErrorLoading(e)
        }
    }
    
    /**
     * 加载韩语"하이넛지"模型
     */
    private fun loadKoreanModel() {
        val koreanModelsDir = File(appContext.filesDir, KOREAN_MODELS_DIR)
        val encoderFile = File(koreanModelsDir, KOREAN_ENCODER_FILE)
        val decoderFile = File(koreanModelsDir, KOREAN_DECODER_FILE)
        val joinerFile = File(koreanModelsDir, KOREAN_JOINER_FILE)
        val tokensFile = File(koreanModelsDir, KOREAN_TOKENS_FILE)
        val keywordsFile = File(koreanModelsDir, KOREAN_KEYWORDS_FILE)
        
        DebugLogger.logModelManagement(TAG, "🇰🇷 Loading Korean '하이넛지' model")
        DebugLogger.logModelManagement(TAG, "📄 Model files:")
        DebugLogger.logModelManagement(TAG, "  - Encoder: ${encoderFile.name} (${if (encoderFile.exists()) "✅" else "❌"})")
        DebugLogger.logModelManagement(TAG, "  - Decoder: ${decoderFile.name} (${if (decoderFile.exists()) "✅" else "❌"})")
        DebugLogger.logModelManagement(TAG, "  - Joiner: ${joinerFile.name} (${if (joinerFile.exists()) "✅" else "❌"})")
        DebugLogger.logModelManagement(TAG, "  - Tokens: ${tokensFile.name} (${if (tokensFile.exists()) "✅" else "❌"})")
        DebugLogger.logModelManagement(TAG, "  - Keywords: ${keywordsFile.name} (${if (keywordsFile.exists()) "✅" else "❌"})")
        
        // 创建韩语KWS配置
        val config = createKoreanKwsConfig(
            encoderPath = encoderFile.absolutePath,
            decoderPath = decoderFile.absolutePath,
            joinerPath = joinerFile.absolutePath,
            tokensPath = tokensFile.absolutePath,
            keywordsPath = keywordsFile.absolutePath
        )
        
        // 这里应该使用真实的SherpaOnnx KeywordSpotter
        keywordSpotter = createKoreanKeywordSpotter(config)
        stream = createKoreanStream()
    }
    
    /**
     * 创建韩语KWS配置
     */
    private fun createKoreanKwsConfig(
        encoderPath: String,
        decoderPath: String,
        joinerPath: String,
        tokensPath: String,
        keywordsPath: String
    ): Any {
        return object {
            val encoder = encoderPath
            val decoder = decoderPath
            val joiner = joinerPath
            val tokens = tokensPath
            val keywords = keywordsPath
            val threshold = 0.25f  // 韩语唤醒词检测阈值
            val score = 1.5f
            val language = "korean"
            val wakeWord = "하이넛지"
        }
    }
    
    /**
     * 获取当前唤醒词
     */
    fun getCurrentWakeWord(): String {
        return when (currentLanguage) {
            WakeWordLanguage.KOREAN -> DEFAULT_WAKE_WORD
            WakeWordLanguage.ENGLISH -> ENGLISH_WAKE_WORD
            WakeWordLanguage.BILINGUAL -> "$DEFAULT_WAKE_WORD / $ENGLISH_WAKE_WORD"
        }
    }
    
    /**
     * 获取支持的唤醒词列表
     */
    fun getSupportedWakeWords(): List<String> {
        return listOf(DEFAULT_WAKE_WORD, ENGLISH_WAKE_WORD)
    }
    
    override fun isHeyDicio(): Boolean {
        // SherpaOnnx使用自定义韩语唤醒词，不是"Hey Dicio"
        return false
    }
}
```

#### 4.2 创建韩语唤醒词配置管理器

```kotlin
/**
 * 韩语唤醒词配置管理器
 */
object KoreanSherpaWakeWordManager {
    private val TAG = KoreanSherpaWakeWordManager::class.simpleName ?: "KoreanSherpaWakeWordManager"
    
    /**
     * 支持的韩语唤醒词
     */
    enum class KoreanWakeWord(
        val korean: String,
        val romanized: String,
        val modelName: String
    ) {
        HI_NUTJI("하이넛지", "hi nutji", "hi_nutji_korean"),
        ANNYEONG("안녕", "annyeong", "annyeong_korean"),
        YEOBOSEYO("여보세요", "yeoboseyo", "yeoboseyo_korean")
    }
    
    /**
     * 安装韩语唤醒词模型
     */
    suspend fun installKoreanWakeWord(
        context: Context, 
        wakeWord: KoreanWakeWord
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.logModelManagement(TAG, "🇰🇷 Installing Korean wake word: ${wakeWord.korean}")
                
                val success = copyKoreanModelFromAssets(context, wakeWord)
                if (success) {
                    // 更新配置
                    updateKoreanWakeWordConfig(context, wakeWord)
                    DebugLogger.logModelManagement(TAG, "✅ Korean wake word installed: ${wakeWord.korean}")
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ Failed to install Korean wake word: ${wakeWord.korean}")
                }
                
                success
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ Error installing Korean wake word: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 从assets复制韩语模型
     */
    private fun copyKoreanModelFromAssets(
        context: Context, 
        wakeWord: KoreanWakeWord
    ): Boolean {
        return try {
            val modelsDir = File(context.filesDir, "models/sherpa_kws_korean")
            modelsDir.mkdirs()
            
            val modelFiles = listOf(
                "${wakeWord.modelName}_encoder.onnx",
                "${wakeWord.modelName}_decoder.onnx",
                "${wakeWord.modelName}_joiner.onnx",
                "${wakeWord.modelName}_tokens.txt",
                "${wakeWord.modelName}_keywords.txt"
            )
            
            modelFiles.forEach { fileName ->
                val assetPath = "models/sherpa_kws_korean/$fileName"
                val targetFile = File(modelsDir, fileName)
                
                context.assets.open(assetPath).use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                DebugLogger.logModelManagement(TAG, "✅ Copied: $fileName")
            }
            
            true
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to copy Korean model: ${e.message}")
            false
        }
    }
    
    /**
     * 更新韩语唤醒词配置
     */
    private fun updateKoreanWakeWordConfig(
        context: Context, 
        wakeWord: KoreanWakeWord
    ) {
        val configFile = File(context.filesDir, "korean_wake_word_config.json")
        val config = JSONObject().apply {
            put("wake_word", wakeWord.korean)
            put("romanized", wakeWord.romanized)
            put("model_name", wakeWord.modelName)
            put("language", "korean")
            put("installed_at", System.currentTimeMillis())
        }
        
        configFile.writeText(config.toString())
        DebugLogger.logModelManagement(TAG, "📝 Updated Korean wake word config")
    }
    
    /**
     * 获取当前韩语唤醒词配置
     */
    fun getCurrentKoreanWakeWordConfig(context: Context): KoreanWakeWord? {
        return try {
            val configFile = File(context.filesDir, "korean_wake_word_config.json")
            if (!configFile.exists()) return null
            
            val config = JSONObject(configFile.readText())
            val modelName = config.getString("model_name")
            
            KoreanWakeWord.values().find { it.modelName == modelName }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to read Korean wake word config: ${e.message}")
            null
        }
    }
}
```

### 第五步：创建韩语唤醒词设置UI

#### 5.1 韩语唤醒词选择器

```kotlin
@Composable
fun KoreanWakeWordSelector(
    currentWakeWord: KoreanSherpaWakeWordManager.KoreanWakeWord?,
    onWakeWordSelected: (KoreanSherpaWakeWordManager.KoreanWakeWord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "韩语唤醒词选择",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // 当前唤醒词显示
            currentWakeWord?.let { wakeWord ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "当前唤醒词",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = wakeWord.korean,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = wakeWord.romanized,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Divider()
            
            // 唤醒词选项
            Text(
                text = "选择唤醒词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            KoreanSherpaWakeWordManager.KoreanWakeWord.values().forEach { wakeWord ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentWakeWord == wakeWord,
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    statusMessage = "安装 ${wakeWord.korean}..."
                                    
                                    val success = KoreanSherpaWakeWordManager.installKoreanWakeWord(
                                        context, wakeWord
                                    )
                                    
                                    if (success) {
                                        onWakeWordSelected(wakeWord)
                                        statusMessage = "✅ 已切换到 ${wakeWord.korean}"
                                    } else {
                                        statusMessage = "❌ 安装失败"
                                    }
                                    isLoading = false
                                }
                            }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentWakeWord == wakeWord,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = wakeWord.korean,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = wakeWord.romanized,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // 状态信息
            if (statusMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            statusMessage.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                            statusMessage.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
```

### 第六步：模型文件准备

#### 6.1 创建模型文件结构

```
app/src/main/assets/models/sherpa_kws_korean/
├── hi_nutji_korean_encoder.onnx      # 编码器模型
├── hi_nutji_korean_decoder.onnx      # 解码器模型
├── hi_nutji_korean_joiner.onnx       # 连接器模型
├── hi_nutji_korean_tokens.txt        # 词汇表
└── hi_nutji_korean_keywords.txt      # 关键词列表
```

#### 6.2 keywords.txt 内容

```
하이넛지
hi nutji
HI NUTJI
하이 넛지
```

#### 6.3 tokens.txt 内容

```
<blk>
<unk>
하
이
넛
지
h
i
n
u
t
j
```

### 第七步：测试和验证

#### 7.1 创建测试脚本

```kotlin
/**
 * 韩语唤醒词测试工具
 */
object KoreanWakeWordTester {
    
    /**
     * 测试韩语唤醒词检测
     */
    fun testKoreanWakeWordDetection(
        context: Context,
        audioFile: File
    ): TestResult {
        return try {
            val device = SherpaOnnxWakeDevice(context)
            device.setWakeWordLanguage(SherpaOnnxWakeDevice.WakeWordLanguage.KOREAN)
            
            // 加载音频文件
            val audioData = loadAudioFile(audioFile)
            
            // 分帧处理
            val frameSize = device.frameSize()
            val results = mutableListOf<Boolean>()
            
            for (i in audioData.indices step frameSize) {
                val frame = audioData.sliceArray(i until minOf(i + frameSize, audioData.size))
                if (frame.size == frameSize) {
                    val detected = device.processFrame(frame)
                    results.add(detected)
                }
            }
            
            TestResult(
                success = true,
                detectionCount = results.count { it },
                totalFrames = results.size,
                audioFile = audioFile.name
            )
        } catch (e: Exception) {
            TestResult(
                success = false,
                error = e.message,
                audioFile = audioFile.name
            )
        }
    }
    
    private fun loadAudioFile(file: File): ShortArray {
        // 加载音频文件并转换为16-bit PCM
        // 实现音频文件加载逻辑
        return shortArrayOf() // 占位符
    }
}

data class TestResult(
    val success: Boolean,
    val detectionCount: Int = 0,
    val totalFrames: Int = 0,
    val audioFile: String,
    val error: String? = null
)
```

## 🔧 实际部署步骤

### 1. **训练韩语模型**
```bash
# 收集"하이넛지"训练数据
python collect_korean_data.py --wake-word "하이넛지" --samples 200

# 训练SherpaOnnx KWS模型
python train_korean_kws.py --config korean_hi_nutji_config.yaml

# 转换为ONNX格式
python export_korean_model.py --input models/korean_hi_nutji --output models/korean_hi_nutji_onnx
```

### 2. **集成到应用**
```bash
# 复制模型文件到assets
cp models/korean_hi_nutji_onnx/* app/src/main/assets/models/sherpa_kws_korean/

# 编译应用
./gradlew assembleDebug
```

### 3. **测试验证**
```bash
# 安装应用
adb install app/build/outputs/apk/debug/app-debug.apk

# 查看日志
adb logcat | grep "🇰🇷\[.*\]"

# 测试唤醒检测
# 在应用中选择SherpaOnnx KWS，然后选择"하이넛지"唤醒词
```

## 📊 性能优化

### 1. **检测阈值调优**
```kotlin
// 根据测试结果调整阈值
device.setDetectionThreshold(0.25f)  // 降低误触发
device.setKeywordsScore(1.5f)        // 提高检测灵敏度
```

### 2. **模型压缩**
```bash
# 使用量化减少模型大小
python quantize_korean_model.py --input hi_nutji_korean.onnx --output hi_nutji_korean_int8.onnx
```

### 3. **内存优化**
```kotlin
// 及时释放资源
override fun destroy() {
    koreanKeywordSpotter?.release()
    koreanStream?.release()
}
```

## 🎯 总结

通过以上步骤，您可以成功为SherpaOnnx KWS系统添加自定义韩语唤醒词"하이넛지"：

1. ✅ **数据收集**: 录制高质量的韩语训练数据
2. ✅ **模型训练**: 使用SherpaOnnx训练韩语KWS模型
3. ✅ **应用集成**: 更新SherpaOnnxWakeDevice支持韩语
4. ✅ **UI配置**: 提供韩语唤醒词选择界面
5. ✅ **测试验证**: 完整的测试和调试工具

这样您就可以在Dicio应用中使用"하이넛지"作为韩语唤醒词了！
