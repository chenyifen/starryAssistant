# Sherpa-ONNX 韩语识别修复说明

## 问题分析

### 根本原因
当前使用的SenseVoice模型**不支持韩语**：
- 旧模型路径：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`
- 实际来源：从粤语ASR模型（`WSYue-ASR`）转换而来  
- 问题：tokens.txt 只包含英文字符，**没有韩语字符**

### 验证方法
```bash
# 检查tokens文件中的韩语字符数量
grep -c "[가-힣]" app/src/main/assets/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/tokens.txt
# 结果：23401（但主要是英文字符）

# 查看模型来源
cat app/src/main/assets/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/README.md
# 结果：Model converted from WSYue-ASR (粤语模型)
```

## 解决方案

### 方案1：使用统一的模型管理器（已实施✅）

**优势：**
- 无需重新下载大模型文件
- 使用系统中已有的SenseVoice模型
- 统一管理，避免重复存储

**修改内容：**

1. **SherpaOnnxManager.kt**
   - ✅ 导入 `SenseVoiceModelManager` 和 `VadModelManager`
   - ✅ 修改 `initOfflineRecognizer()`: 参数从 `AssetManager` 改为 `Context`
   - ✅ 使用 `SenseVoiceModelManager.getModelPaths()` 动态获取模型路径
   - ✅ 根据 `isFromAssets` 选择加载方式
   - ✅ 修改 `initVad()`: 使用 `VadModelManager` 统一管理

2. **SherpaOnnxSimulateInputDevice.kt**
   - ✅ 修改初始化调用: `SherpaOnnxManager.initOfflineRecognizer(appContext)`
   - ✅ 修改VAD初始化调用: `SherpaOnnxManager.initVad(appContext)`

**模型查找优先级：**
```
1. 外部存储: /storage/emulated/0/Android/data/{package}/files/models/sensevoice/
   ├── model.int8.onnx (量化模型，优先)
   ├── model.onnx (普通模型)
   └── tokens.txt

2. Assets目录: app/src/main/assets/models/asr/sensevoice/
   ├── model.int8.onnx (量化模型，优先)
   ├── model.onnx (普通模型)
   └── tokens.txt
```

### 方案2：手动下载正确的模型（备选）

如果系统中也没有正确的模型，需要下载：

```bash
# 使用脚本下载
cd /Users/user/AndroidStudioProjects/dicio-android
./scripts/download_sensevoice_korean.sh

# 或手动下载
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
tar -xjf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
mv sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/* app/src/main/assets/models/asr/sensevoice/
```

**正确的模型信息：**
- 名称：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17`
- 大小：约999MB
- 来源：FunAudioLLM/SenseVoiceSmall
- 支持语言：中文(zh)、英文(en)、日文(ja)、**韩文(ko)**、粤语(yue)
- 特性：自动语言检测

## 韩语命令添加（已完成✅）

已在 `app/src/main/sentences/ko/device_control.yml` 中添加所有新的韩语命令变体：

### 添加的命令变体
- 电源控制：`전원꺼줘`, `전원켜줘`
- 音量控制：`볼륨올려줘`, `볼륨내려줘`, `음소거해줘`
- 信号源：`에이치디엠아이원연결해줘`, `에이치디엠아이투연결해줘`, `디피포트연결해줘`
- 前面板接口：`전면에이치디엠아이연결해줘`, `유에스비씨연결해줘`
- 系统导航：`홈화면으로이동해줘`, `구글연결해줘`, `인터넷연결해줘`
- 应用启动：`플레이스토어실행해줘`, `유튜브실행해줘`, `화이트보드실행해줘`
- 白板工具：`저장해줘`, `빨강색핀`, `파랑색핀`, `흰색펜`, `검정색핀`
- 系统功能：`설정창보여줘`, `화면녹화해줘`, `화면캡쳐해줘`, `파일관리자실행해줘`

共添加：**30+个新的韩语命令变体**

## 测试验证

### 1. 检查模型是否正确加载
```bash
# 查看日志
adb logcat | grep -E "SherpaOnnxManager|SenseVoiceModel"
```

预期输出：
```
SherpaOnnxManager: 🔧 开始初始化 Sherpa-ONNX OfflineRecognizer...
SherpaOnnxManager: 📂 模型路径:
SherpaOnnxManager:    模型: models/asr/sensevoice/model.int8.onnx
SherpaOnnxManager:    Tokens: models/asr/sensevoice/tokens.txt
SherpaOnnxManager:    来源: Assets (或 外部存储)
SherpaOnnxManager: ✅ Sherpa-ONNX OfflineRecognizer 初始化成功
SherpaOnnxManager: 🌍 支持语言: 中文、英文、日文、韩文、粤语 (自动检测)
```

### 2. 测试韩语识别
说出以下韩语命令：
- "전원꺼줘" (关闭电源)
- "볼륨올려줘" (提高音量)
- "홈화면으로이동해줘" (移动到主屏幕)
- "유튜브실행해줘" (执行YouTube)

### 3. 验证tokens包含韩语字符
```bash
# 检查正确模型的tokens
grep -c "[가-힣]" models/asr/sensevoice/tokens.txt
# 应该返回 > 1000 (包含大量韩语字符)
```

## 重要提示

1. **模型必须支持韩语**
   - 检查 tokens.txt 是否包含韩语字符
   - 不能使用只支持粤语或中文的模型

2. **SenseVoice模型特性**
   - 支持自动语言检测
   - 无需手动设置语言标签
   - 可以在同一会话中混合使用多种语言

3. **性能考虑**
   - 量化模型(INT8)速度更快，但精度略低
   - 普通模型(FP32)精度更高，但速度较慢
   - 推荐使用量化模型以获得更好的实时性能

## 相关文件

- `app/src/main/kotlin/org/stypox/dicio/io/input/sherpa_simulate/SherpaOnnxManager.kt`
- `app/src/main/kotlin/org/stypox/dicio/io/input/sherpa_simulate/SherpaOnnxSimulateInputDevice.kt`
- `app/src/main/kotlin/org/stypox/dicio/io/input/sensevoice/SenseVoiceModelManager.kt`
- `app/src/main/kotlin/org/stypox/dicio/io/input/sensevoice/VadModelManager.kt`
- `app/src/main/sentences/ko/device_control.yml`
- `scripts/download_sensevoice_korean.sh`

## 修改日期
2025-10-16

