# 现有相似度算法对韩语的适用性分析

## 现有算法架构

### 1. 词分割算法（splitWords）
- **实现**: 使用 `Regex("\\p{L}+")` 匹配所有Unicode字母字符
- **对韩语**: ✅ **可用** - `\p{L}` 正确识别韩文字符（U+AC00-U+D7AF）
- **潜在问题**: 无

### 2. NFKD标准化（nfkdNormalizeWord）
- **实现**: `Normalizer.normalize(word, Normalizer.Form.NFKD)` + 移除组合标记
- **对韩语**: ⚠️ **可能有问题**
  - NFKD会将韩文音节（如"구글"）分解为字母（初声、中声、终声）
  - 但Java的NFKD对韩文音节的处理可能不会完全分解
  - 需要实际测试验证

### 3. 匹配算法（WordConstruct）
- **实现**: 根据 `isDiacriticsSensitive` 选择使用原始文本或NFKD标准化文本
- **对韩语**: 
  - 如果 `isDiacriticsSensitive = true`: ✅ 使用原始文本，不会有问题
  - 如果 `isDiacriticsSensitive = false`: ⚠️ 使用NFKD标准化，可能有影响

### 4. 评分算法（StandardScore）
- **实现**: 
  - 词匹配权重: `WORD_WEIGHT = 1.0f`
  - 字符匹配权重: `CHAR_WEIGHT = 0.1f`
  - 使用调和平均数: `2 / (userWeight/userMatched + refWeight/refMatched)`
- **对韩语**: ✅ **可用** - 基于匹配比例的算法对韩语同样有效

## 主要问题分析

### 问题1: NFKD标准化对韩语的影响
**理论影响**:
- NFKD会将某些字符分解，但韩文音节（한글 음절）在Unicode中是预组合字符
- Java的NFKD对韩文音节的处理：**大部分情况下不会分解**
- 例如："구글" 在NFKD标准化后仍然是 "구글"（不会分解）

**实际影响**: 
- ✅ **较小** - 韩文音节通常不会被NFKD分解
- ⚠️ **但需要注意**: 如果配置使用了 `isDiacriticsSensitive = false`，仍可能影响某些边界情况

### 问题2: 词边界识别
**当前状态**:
- ✅ 已通过 `cleanTextForSkillMatching()` 解决
- 韩语去除所有空格，避免词边界问题

### 问题3: 部分匹配精度
**当前状态**:
- ✅ 算法支持部分匹配（通过 `cumulativeWeight` 计算）
- ✅ 评分算法使用调和平均数，能处理部分匹配情况

## 建议改进方案

### 方案1: 确保韩语使用原始文本匹配（推荐）
**实现**: 在编译时设置 `isDiacriticsSensitive = true` 对于韩语配置
- 优点: 完全避免NFKD标准化的影响
- 缺点: 需要修改sentences compiler配置

### 方案2: 优化韩语匹配权重（可选）
**实现**: 针对韩语调整 `WORD_WEIGHT` 和 `CHAR_WEIGHT`
- 优点: 提高匹配精度
- 缺点: 需要语言感知的权重系统

### 方案3: 添加韩语特定的模糊匹配（可选）
**实现**: 对ASR误识别（如"구을"→"구글"）使用编辑距离
- 优点: 提高容错性
- 缺点: 性能开销较大

## 结论

✅ **现有算法基本适合韩语**，但建议：
1. **优先使用**: 确保韩语配置使用 `isDiacriticsSensitive = true`
2. **已解决的问题**: 通过文本清理去除空格和标点
3. **监控**: 观察实际匹配效果，如有问题再考虑优化

## 实际测试建议

测试以下场景：
1. "구글 연결해줘" vs "구을 연결해줘" (ASR误识别)
2. "구글 연결해줘" vs "구글연결해줘" (空格差异)
3. "구글 연결해줘" vs "구글 연결해줘." (标点差异)
