要在 Android 平台上实现支持韩语的远场语音唤醒功能，Sherpa-ONNX 是一个值得考虑的开源工具。它提供了开箱即用的关键字检测（Keyword Spotting，简称 KWS）功能，支持多语言，并且可以在设备上本地运行，适合用于语音助手、智能家居等应用场景。

⸻

🧠 Sherpa-ONNX 简介

Sherpa-ONNX 是一个基于 ONNX Runtime 的开源语音识别框架，支持以下功能：
	•	流式和非流式语音识别（ASR）
	•	文本到语音（TTS）
	•	语音活动检测（VAD）
	•	关键字检测（Keyword Spotting，KWS）
	•	说话人识别、语言识别、标点修正、语音增强等

所有功能均可在设备上本地运行，无需网络连接，适用于 Android、iOS、Linux、Windows、macOS、Flutter 等平台  ￼。

⸻

🇰🇷 支持韩语的模型

Sherpa-ONNX 提供了预训练的韩语模型，适用于关键字检测任务：
	•	模型名称：sherpa-onnx-streaming-zipformer-korean-2024-06-16
	•	支持语言：韩语
	•	模型类型：Zipformer（适用于流式语音识别）
	•	下载链接：Hugging Face 页面

该模型可用于关键字检测任务，支持自定义关键词的检测。

⸻

🔑 自定义韩语关键词（如 “하이넛지”）

Sherpa-ONNX 支持开放词汇的关键字检测系统，即无需重新训练模型即可检测自定义关键词。您可以通过以下步骤实现：

1. 准备关键词文件

创建一个文本文件，列出您希望检测的关键词及其参数。每行包含一个关键词的编码形式、增强分数（boosting score）和触发阈值（trigger threshold）。例如：

▁하 이 ▁넛 지 :2.0 #0.6

	•	编码形式：使用空格分隔的子词单元（subword units），例如使用 BPE（Byte Pair Encoding）或 CJK 字符。
	•	增强分数：控制关键词在解码过程中的优先级。
	•	触发阈值：控制关键词被触发的概率阈值。

注意：在韩语中，通常使用 CJK 字符作为建模单元。您可以使用 Sherpa-ONNX 提供的工具将原始关键词转换为编码形式。

2. 使用命令行工具转换关键词

安装 Sherpa-ONNX CLI 工具：

pip install sherpa-onnx

使用 text2token 命令将原始关键词转换为编码形式：

sherpa-onnx-cli text2token --text keywords.txt --tokens-type cjkchar --tokens tokens.txt

其中，keywords.txt 是包含原始关键词的文本文件，tokens.txt 是输出的编码形式文件。

3. 在 Android 应用中使用

将生成的 tokens.txt 文件包含到您的 Android 应用中，并使用 Sherpa-ONNX 提供的 Android API 进行关键字检测。您可以参考 Sherpa-ONNX 的 Android 示例代码进行集成：
	•	Android 示例代码：GitHub 示例

⸻

✅ 优势总结
	•	无需重新训练模型：支持开放词汇的关键字检测，方便添加自定义关键词。
	•	本地运行：所有功能均可在设备上本地运行，保证隐私和响应速度。
	•	多语言支持：提供多种语言的预训练模型，包括韩语。
	•	开源免费：遵循 Apache 2.0 许可证，可免费用于商业用途。

⸻

🚀 下一步建议
	•	下载并测试预训练的韩语模型，评估其在您设备上的性能。
	•	根据您的需求，创建包含自定义关键词的 tokens.txt 文件。
	•	将关键词文件集成到您的 Android 应用中，使用 Sherpa-ONNX 提供的 API 进行关键字检测。
	•	根据测试结果，调整增强分数和触发阈值，以优化检测性能。

如果您需要进一步的帮助，例如如何在 Android Studio 中配置 Sherpa-ONNX，或如何调试关键字检测性能，请随时提问。






您遇到的错误提示“Error: No such option: --text”表明您正在使用的 sherpa-onnx-cli 工具版本不支持 --text 参数。根据官方文档和社区讨论，text2token 命令的用法可能因版本不同而有所变化。以下是适用于当前版本的正确操作步骤：

⸻

✅ 正确的操作步骤

1. 准备原始关键词文件

创建一个文本文件（例如 keywords_raw.txt），每行包含一个原始关键词，例如：

하이넛지

2. 使用 text2token 命令生成编码后的关键词文件

在命令行中运行以下命令：

sherpa-onnx-cli text2token --tokens tokens.txt --tokens-type cjkchar keywords_raw.txt keywords.txt

	•	--tokens: 指定包含建模单元的词汇表文件路径。
	•	--tokens-type: 指定建模单元的类型，例如 cjkchar。
	•	keywords_raw.txt: 包含原始关键词的文件路径。
	•	keywords.txt: 输出的编码后关键词文件路径。

请确保 tokens.txt 文件与您选择的 --tokens-type 类型匹配。

3. 编辑生成的 keywords.txt 文件

打开 keywords.txt 文件，按照以下格式添加增强分数（boosting score）和触发阈值（trigger threshold）：

▁하 이 ▁넛 지 :2.0 #0.6

	•	▁하 이 ▁넛 지: 使用空格分隔的子词单元。
	•	:2.0: 增强分数，控制关键词在解码过程中的优先级。
	•	#0.6: 触发阈值，控制关键词被触发的概率阈值。

⸻

🔧 常见问题及解决方案
	•	错误提示“No such option: --text”：这是因为当前版本的 sherpa-onnx-cli 不支持 --text 参数。请使用上述步骤中的方法。
	•	如何确定 tokens.txt 的路径：tokens.txt 文件通常与您选择的建模单元类型（如 cjkchar）相关。您可以在 Sherpa-ONNX 的 GitHub 仓库中找到相应的词汇表文件。
	•	如何选择建模单元类型：对于韩语，通常使用 cjkchar 作为建模单元类型。

⸻

如果您在操作过程中遇到其他问题，欢迎继续提问。