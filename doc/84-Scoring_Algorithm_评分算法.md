# Dicio 语音技能评分算法详解

## 概述

Dicio 使用基于有限状态机的匹配算法来评估用户输入与预定义句子模板的相似度。该算法综合考虑了多个因素，包括精确匹配、部分匹配、权重计算等。

## 核心组件

### 1. 技能层级结构

```
SkillRanker (技能排名器)
├── defaultBatch (默认技能批次)
│   ├── High Priority Skills (高优先级)
│   ├── Medium Priority Skills (中优先级)
│   └── Low Priority Skills (低优先级)
└── batches (动态技能批次)
```

### 2. 多语言匹配 (MultiLanguageSkill)

每个技能支持多语言匹配：
- 遍历所有已加载的语言数据
- 为每种语言计算匹配分数
- 返回最高分作为最终匹配结果

### 3. 句子构造器 (Construct System)

句子通过构造器系统进行解析：
- `WordConstruct`: 精确匹配单词
- `CompositeConstruct`: 组合多个构造器
- `OptionalConstruct`: 可选匹配
- `OrConstruct`: 或匹配

## 评分算法详解

### 核心评分公式

```kotlin
score() = UM × userMatched + UW × userWeight + RM × refMatched + RW × refWeight
```

其中：
- **UM (User Matched)**: 2.0 - 用户匹配权重
- **UW (User Weight)**: -1.1 - 用户权重惩罚
- **RM (Reference Matched)**: 2.0 - 参考匹配权重
- **RW (Reference Weight)**: -1.1 - 参考权重惩罚

### 权重计算原理

#### 1. 用户输入权重 (User Weight)
- **单词权重**: 1.0 (每个单词)
- **字符权重**: 0.1 (单个字符)
- **标点权重**: 0.05 (标点符号)
- **空白权重**: 0.0 (空白字符)

#### 2. 参考权重 (Reference Weight)
- 每个句子构造器都有固定的权重 (通常为1.0)
- 通过构造器组合形成完整的句子权重

### 0-1范围标准化

```kotlin
scoreIn01Range() = 2.0 / (userWeight/userMatched + refWeight/refMatched)
```

使用调和平均数确保：
- 如果任一匹配项显著低于另一个，评分趋向于较低值
- 提供[0,1]范围内的标准化分数用于比较

## 匹配流程

### 1. 文本预处理

#### 韩语特殊处理
```kotlin
// 去掉空格和标点符号
cleaned = text.replace(Regex("\\s+"), "").replace(Regex("[.,。，!！?？;；:：]"), "")
// NFKD标准化
normalized = nfkdNormalizeWord(cleaned)
```

#### 其他语言
保留原始文本格式。

### 2. 单词分割

使用正则表达式 `\\p{L}+` 分割单词：
- 支持所有Unicode字母字符
- 自动处理大小写转换
- 生成NFKD标准化版本用于匹配

### 3. 有限状态机匹配

#### 匹配过程
1. 初始化匹配矩阵 `memToEnd`
2. 为每个可能的位置尝试匹配
3. 使用动态规划计算最佳匹配路径
4. 返回最佳匹配分数

#### 精确匹配逻辑
```kotlin
if (compiledRegex?.matches(wordText) ?: (text == wordText)) {
    // 匹配成功，累加权重
    memToEnd[start] = memToEnd[word.end].plus(
        userMatched = userWeight,
        userWeight = userWeight,
        refMatched = weight,
        refWeight = weight
    )
} else {
    // 匹配失败，添加惩罚
    memToEnd[start] = memToEnd[start].plus(refWeight = weight)
}
```

## 阈值系统

### 技能排名阈值

```kotlin
// 第一轮：高优先级技能
HIGH_THRESHOLD_1 = 0.6f

// 第二轮：中等优先级技能
MEDIUM_THRESHOLD_2 = 0.80f
HIGH_THRESHOLD_2 = 0.5f

// 第三轮：所有技能
LOW_THRESHOLD_3 = 0.80f
MEDIUM_THRESHOLD_3 = 0.60f
HIGH_THRESHOLD_3 = 0.5f
```

### 匹配逻辑
1. **第一轮**: 只考虑高优先级技能，阈值0.6
2. **第二轮**: 加入中等优先级技能，高优先级降低阈值到0.5
3. **第三轮**: 所有技能都考虑，各有不同阈值

## 特殊处理

### 1. NFKD标准化
- **目的**: 处理Unicode组合字符（如韩语）
- **过程**: 分解字符 + 移除组合标记
- **示例**: `"하면"` → `"하면"` (Unicode组合序列)

### 2. 音调不敏感匹配
- **默认启用**: `isDiacriticsSensitive = false`
- **效果**: 自动处理变音符号和重音

### 3. 多语言支持
- **策略**: 尝试所有已加载语言，返回最佳匹配
- **优势**: 用户可混合使用不同语言

## 性能优化

### 1. 早期剪枝
- 只有分数超过0.01的匹配才记录日志
- 避免不必要的计算和日志输出

### 2. 缓存机制
- `MatchHelper`缓存分词结果
- 避免重复计算相同的输入

### 3. 批次处理
- 技能按优先级分组处理
- 找到匹配后立即停止后续批次

## 调试信息

### 日志输出示例
```
🌐 [system_navigation] 多语言匹配开始: '하면캡쳐해죠'
  [system_navigation] 语言1 匹配分数: 1.0
✅ [system_navigation] 最佳匹配分数: 1.0
```

### 技能排名日志
```
🎯 SkillBatch.getBest() 开始评估输入: '하면캡쳐해죠'
📊 技能数量 - High: 5, Medium: 0, Low: 0
  🔍 评估 5 个技能:
    📝 power_control: 0.0
    📝 input_source_control: 0.0
    📝 app_launcher: 0.0
    📝 whiteboard_tools: 0.0
    📝 system_navigation: 1.0
  🏆 最佳技能: system_navigation (1.0)
```

## 常见问题

### 1. 为什么返回0.0分？
- **原因**: 句子未匹配，或文本预处理不一致
- **解决**: 检查NFKD标准化，确保YAML文件包含相应变体

### 2. 评分不准确？
- **原因**: 权重参数不合适
- **解决**: 调整UM/UW/RM/RW常量，或修改句子权重

### 3. 性能问题？
- **原因**: 太多句子或复杂构造器
- **解决**: 优化句子结构，减少可选分支

## 扩展建议

### 1. 动态权重
- 根据上下文调整权重参数
- 学习用户偏好进行个性化调整

### 2. 模糊匹配
- 集成编辑距离算法
- 支持拼写纠错功能

### 3. 语义理解
- 结合词向量或语言模型
- 理解句子语义而不仅是表面匹配

---

*最后更新: 2025年11月6日*
*版本: 1.0*
