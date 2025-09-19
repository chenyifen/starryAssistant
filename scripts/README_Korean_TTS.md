# 韩语唤醒词 TTS 音频生成工具集

本目录包含完整的韩语唤醒词 "하이넛지" 训练数据生成工具集，支持多种TTS引擎、远场语音模拟和大规模数据集生成。

## 脚本说明

### 1. `simple_korean_tts_generator.py` (入门推荐)
简化版生成器，依赖最少，易于使用。

**特点:**
- 只需要 `gtts` 和 `pydub` 两个库
- 自动生成正样本和负样本
- 自动音频格式转换 (MP3 → WAV)
- 生成训练用的文件列表

### 2. `advanced_korean_tts_generator.py` (功能完整)
高级多引擎生成器，支持丰富的语音变化。

**特点:**
- 支持多种TTS引擎 (gTTS, edge-tts, pyttsx3)
- 不同音色、语速、情感变化
- 远场语音模拟 (混响、噪声、距离衰减)
- 异步并行生成，提高效率
- 音频数据增强

### 3. `batch_generate_korean_dataset.py` (大规模生成)
批量数据集生成器，一次性生成完整的训练数据集。

**特点:**
- 预设小型/中型/大型/超大型数据集配置
- 自动训练/验证/测试集划分 (70%/15%/15%)
- 数据质量检查和统计
- 完整的数据集元数据
- 支持自定义配置

### 4. `install_tts_dependencies.py` (依赖管理)
自动安装和检查TTS相关依赖库。

**特点:**
- 自动检测已安装的库
- 批量安装缺失依赖
- 系统依赖检查 (如ffmpeg)
- 生成requirements.txt

### 5. `test_korean_tts.py` (功能测试)
TTS功能测试脚本，验证环境配置。

## 安装依赖

### 自动安装 (推荐)
```bash
# 检查依赖状态
python scripts/install_tts_dependencies.py --check-only

# 安装基础依赖
python scripts/install_tts_dependencies.py

# 安装所有依赖 (包括可选)
python scripts/install_tts_dependencies.py --install-all
```

### 手动安装

#### 基础依赖 (必需)
```bash
pip install gtts pydub numpy librosa soundfile scipy
```

#### TTS引擎库
```bash
pip install edge-tts pyttsx3  # 可选的额外TTS引擎
```

#### 系统依赖
```bash
# macOS
brew install ffmpeg

# Ubuntu/Debian
sudo apt install ffmpeg

# Windows
# 下载ffmpeg并添加到PATH
```

## 使用方法

### 1. 功能测试 (推荐第一步)
```bash
# 测试TTS功能
python scripts/test_korean_tts.py
```

### 2. 简化版使用 (快速开始)
```bash
# 基本使用 (30个正样本 + 60个负样本)
python scripts/simple_korean_tts_generator.py

# 指定数量
python scripts/simple_korean_tts_generator.py --positive_count 50 --negative_count 100

# 指定总数量 (自动分配正负样本比例 1:2)
python scripts/simple_korean_tts_generator.py --count 90
```

### 3. 高级版使用 (多引擎 + 远场)
```bash
# 检查可用引擎
python scripts/advanced_korean_tts_generator.py --show_status_only

# 基本生成
python scripts/advanced_korean_tts_generator.py

# 生成更多样本 + 远场效果
python scripts/advanced_korean_tts_generator.py --positive_count 200 --negative_count 400 --far_field

# 启用所有引擎
python scripts/advanced_korean_tts_generator.py --enable_all_engines
```

### 4. 批量数据集生成 (大规模训练)
```bash
# 生成中型数据集 (5K样本)
python scripts/batch_generate_korean_dataset.py --medium

# 生成大型数据集 (16K样本)
python scripts/batch_generate_korean_dataset.py --large

# 自定义配置
python scripts/batch_generate_korean_dataset.py --custom --positive 1000 --negative 4000 --far_field_samples 500

# 不生成远场样本
python scripts/batch_generate_korean_dataset.py --large --no_far_field
```

## 输出结构

生成的数据结构如下:
```
training_data/korean_tts/
├── positive/           # 正样本 (唤醒词 "하이넛지")
│   ├── positive_001.wav
│   ├── positive_002.wav
│   └── ...
├── negative/           # 负样本 (其他韩语词汇)
│   ├── negative_001.wav
│   ├── negative_002.wav
│   └── ...
├── enhanced/           # 增强样本 (仅高级版)
│   ├── positive_noise_001.wav
│   ├── positive_pitch_up_001.wav
│   └── ...
├── temp/               # 临时文件目录
├── file_list.txt       # 训练文件列表
└── metadata.json       # 元数据 (仅高级版)
```

## 文件列表格式

`file_list.txt` 格式:
```
positive/positive_001.wav 1
positive/positive_002.wav 1
negative/negative_001.wav 0
negative/negative_002.wav 0
```

- 第一列: 相对文件路径
- 第二列: 标签 (1=正样本, 0=负样本)

## 音频规格

- **采样率**: 16kHz
- **声道**: 单声道
- **时长**: 2秒
- **格式**: WAV
- **音量**: 标准化处理

## 负样本词汇

包含以下类型的韩语词汇:
- 常用日常用语
- 相似但不同的词汇 (如 "하이", "넛지" 等)
- 数字和时间表达
- 总计约50个不同词汇

## 注意事项

1. **网络连接**: 需要稳定的网络连接访问Google TTS服务
2. **请求频率**: 脚本已内置延迟避免请求过快
3. **存储空间**: 确保有足够的磁盘空间存储音频文件
4. **依赖安装**: 确保所有依赖库正确安装

## 故障排除

### 常见问题

1. **ImportError**: 缺少依赖库
   ```bash
   pip install gtts pydub
   ```

2. **ffmpeg 错误** (macOS):
   ```bash
   brew install ffmpeg
   ```

3. **网络错误**: 检查网络连接，稍后重试

4. **权限错误**: 确保输出目录有写入权限

### 调试模式

添加 `--verbose` 参数查看详细日志:
```bash
python scripts/simple_korean_tts_generator.py --verbose
```

## 集成到训练流程

生成的数据可以直接用于:
1. SherpaOnnx 唤醒词模型训练
2. 其他深度学习框架
3. 传统机器学习方法

使用 `file_list.txt` 作为训练数据索引文件。

## 扩展建议

1. **增加词汇**: 在 `negative_words` 列表中添加更多韩语词汇
2. **音频增强**: 使用高级版的音频增强功能
3. **真人录音**: 结合TTS数据和真人录音数据
4. **数据平衡**: 根据训练效果调整正负样本比例
