# AI模型清单

本文档记录项目中使用的所有AI模型及其管理方式。

## 📁 模型目录结构

```
models/
├── wake/                        # 唤醒词模型
│   ├── hiNudgeOpenWakeWord/     # V8韩语唤醒 (默认) - 1.3MB
│   ├── openwakeword_korean_minimal/  # 最小韩语唤醒 - 1.3MB
│   └── openwakeword_korean_v1/      # V1韩语唤醒 - 3.8MB
├── tts/                         # 文本转语音模型
│   ├── vits-piper-en_US-amy-low/    # 英语TTS - 60MB
│   ├── vits-mimic3-ko_KO-kss_low/   # 韩语TTS - 60MB
│   └── vits-zh-hf-fanchen-C/        # 中文TTS - 150MB
├── asr/                         # 语音识别模型
│   └── sensevoice/              # SenseVoice多语言 - 900MB+
└── vad/                         # 语音活动检测
```

## 🚀 快速使用

### 推送所有模型到设备
```bash
./scripts/model_manager.sh
```

### 仅推送特定类型模型
```bash
./scripts/model_manager.sh push-wake    # 唤醒词
./scripts/model_manager.sh push-tts     # TTS
./scripts/model_manager.sh push-asr     # ASR
```

### 查看设备上的模型
```bash
./scripts/model_manager.sh show
```

### 从设备备份模型
```bash
./scripts/model_manager.sh backup
```

## 📦 模型详细信息

### 唤醒词模型 (Wake Word Detection)

#### 1. HiNudge OpenWakeWord V8 ⭐ (默认)
- **路径**: `models/hiNudgeOpenWakeWord/`
- **大小**: 1.3MB
- **语言**: 韩语
- **唤醒词**: "하이넛지" (Hi Nudge)
- **特点**: 100% recall, 最佳性能
- **推荐**: ✅ 生产环境使用

#### 2. OpenWakeWord Korean Minimal
- **路径**: `models/openwakeword_korean_minimal/`
- **大小**: 1.3MB
- **语言**: 韩语
- **特点**: 轻量级，资源占用少

#### 3. OpenWakeWord Korean V1
- **路径**: `models/openwakeword_korean_v1/`
- **大小**: 3.8MB
- **语言**: 韩语
- **特点**: 更高准确率

### TTS模型 (Text-to-Speech)

#### 1. VITS Piper - English
- **路径**: `models/tts/vits-piper-en_US-amy-low/`
- **大小**: 60MB
- **语言**: 英语 (美式)
- **声音**: Amy (女声)
- **质量**: Low (适合移动设备)
- **格式**: ONNX + espeak-ng-data

#### 2. VITS Mimic3 - Korean
- **路径**: `models/tts/vits-mimic3-ko_KO-kss_low/`
- **大小**: 60MB
- **语言**: 韩语
- **声音**: KSS
- **质量**: Low (适合移动设备)
- **格式**: ONNX

#### 3. VITS - Chinese
- **路径**: `models/tts/vits-zh-hf-fanchen-C/`
- **大小**: 150MB
- **语言**: 中文 (简体)
- **格式**: ONNX

### ASR模型 (Automatic Speech Recognition)

#### SenseVoice ⭐ (默认)
- **路径**: `models/asr/sensevoice/`
- **大小**: 900MB+
- **语言**: 多语言支持
  - 中文 (简体/繁体)
  - 英语
  - 韩语
  - 日语
  - 粤语
- **特点**: 
  - 高精度多语言识别
  - 情感识别
  - 标点符号预测
  - 支持长音频
- **推荐**: ✅ 生产环境使用
- **注意**: 模型较大，首次下载需要时间

## 📥 模型下载

### Git LFS 模型下载

项目使用Git LFS管理大文件。如果模型文件显示很小（几百字节），需要下载真实文件：

```bash
# 在模型目录下执行
cd /path/to/dicio-android_2/models
git lfs install
git lfs pull
```

### 手动下载地址

如果Git LFS不可用，可从以下位置下载：

#### TTS模型
- **英语**: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/amy/low
- **韩语**: https://huggingface.co/rhasspy/mimic3-voices/tree/main/ko_KO/kss_low
- **中文**: https://huggingface.co/fanchen/vits-zh-hf-fanchen-C

#### ASR模型
- **SenseVoice**: https://huggingface.co/FunAudioLLM/SenseVoiceSmall
  - 下载: `model.onnx` (非int8版本)
  - 配置文件和tokenizer

#### 唤醒词模型
- 已包含在项目中，无需额外下载

## 🔧 设备路径

模型在Android设备上的存储路径：
```
/storage/emulated/0/Android/data/com.ai.voice/files/models/
├── wake/
├── tts/
├── asr/
└── vad/
```

## ⚙️ 默认配置

应用默认使用以下配置：
- **语言**: 韩语
- **唤醒词**: HiNudge V8
- **ASR**: SenseVoice
- **TTS**: 根据语言自动选择

配置文件位置：
`app/src/main/kotlin/com/ai/voice/settings/datastore/UserSettingsSerializer.kt`

## 🛠️ 故障排除

### 模型文件太小（几百字节）
这是Git LFS指针文件，需要下载真实文件：
```bash
cd models/tts/vits-piper-en_US-amy-low/
git lfs pull
```

### 推送失败：权限错误
使用临时目录中转：
```bash
./scripts/model_manager.sh push-all
```
脚本会自动处理权限问题。

### 模型加载失败
1. 检查设备上的模型完整性
2. 查看应用日志：`adb logcat | grep -i "model\|onnx\|tts\|wake"`
3. 确认模型文件完整未损坏

## 📊 模型大小统计

| 类型 | 数量 | 总大小 | 必需 |
|------|------|--------|------|
| 唤醒词 | 3 | ~6MB | ✅ |
| TTS | 3 | ~270MB | ✅ |
| ASR | 1 | ~900MB | ✅ |
| VAD | - | <1MB | ⚠️ |
| **总计** | **7** | **~1.2GB** | - |

## 🔄 更新日志

### 2025-10-18
- ✅ 创建统一模型管理脚本
- ✅ 设置V8为默认唤醒词
- ✅ 设置SenseVoice为默认ASR
- ✅ 设置韩语为默认语言
- ✅ 完成英语TTS模型推送

### 待添加
- [ ] 中文SenseVoice模型优化
- [ ] 更多语言TTS支持
- [ ] 模型自动更新机制

## 📝 注意事项

1. **存储空间**: 确保设备有至少2GB可用空间
2. **网络**: 首次下载SenseVoice需要稳定网络
3. **性能**: SenseVoice对设备性能有一定要求
4. **备份**: 使用 `model_manager.sh backup` 定期备份

## 🔗 相关资源

- [OpenWakeWord](https://github.com/dscripka/openWakeWord)
- [Piper TTS](https://github.com/rhasspy/piper)
- [SenseVoice](https://github.com/FunAudioLLM/SenseVoice)
- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)

