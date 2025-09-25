# TTS模型调试指南

## 问题描述
在main渠道（noModels变体）中出现"❌ 未找到TTS模型: zh"错误。

## 调试步骤

### 1. 查看日志输出
运行应用后，在logcat中搜索以下标签：
- `TtsModelManager`
- `SherpaOnnxTtsSpeechDevice`

### 2. 关键日志信息
应用启动时会输出详细的路径检查信息：

```
🔍 getExternalFilesDir结果: /storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts
✅ 使用应用专用目录: /storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts
📁 外部存储基础路径: /storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts
📂 外部存储基础目录状态:
  - 存在: true/false
  - 可读: true/false  
  - 是目录: true/false
  - 子目录列表 (X 个):
    * vits-zh-hf-fanchen-C (目录)
    * ...
```

### 3. 预期的TTS模型路径结构

对于main渠道，TTS模型应该放在：
```
/storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts/
├── vits-zh-hf-fanchen-C/
│   ├── vits-zh-hf-fanchen-C.onnx
│   ├── lexicon.txt
│   └── dict/
├── vits-mimic3-ko_KO-kss_low/
│   ├── ko_KO-kss_low.onnx
│   ├── tokens.txt
│   └── espeak-ng-data/
└── vits-piper-en_US-amy-low/
    ├── en_US-amy-low.onnx
    ├── tokens.txt
    └── espeak-ng-data/
```

### 4. 中文TTS模型配置
- **语言代码**: zh
- **模型目录**: vits-zh-hf-fanchen-C
- **模型文件**: vits-zh-hf-fanchen-C.onnx
- **词典文件**: lexicon.txt
- **字典目录**: dict

### 5. 常见问题排查

#### 问题1: 目录不存在
如果日志显示"存在: false"，需要：
1. 手动创建目录结构
2. 或者从其他设备/项目复制模型文件

#### 问题2: 权限问题
如果日志显示"可读: false"，需要：
1. 检查应用是否有存储权限
2. 重新安装应用

#### 问题3: 模型文件缺失
如果目录存在但模型文件不存在，需要：
1. 下载对应的TTS模型文件
2. 确保文件名与配置匹配

### 6. 手动验证
可以使用adb命令检查文件：
```bash
adb shell ls -la /storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts/
adb shell ls -la /storage/emulated/0/Android/data/org.stypox.dicio.master/files/models/tts/vits-zh-hf-fanchen-C/
```

### 7. 临时解决方案
如果中文TTS模型不可用，系统会自动回退到英语模型。检查日志中是否有：
```
⚠️ 未找到 zh 语言的TTS模型配置，回退到英语
```

## 注意事项
- main渠道使用应用专用目录，不需要额外的存储权限
- 路径会根据应用包名动态生成
- 模型文件较大，需要确保有足够的存储空间
