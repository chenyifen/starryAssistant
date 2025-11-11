# Partial阶段Fallback修复测试指南

## 快速测试步骤

### 1. 编译并安装应用
```bash
cd /Users/user/AndroidStudioProjects/dicio-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 收集测试日志
```bash
# 清除旧日志
adb logcat -c

# 开始收集日志
adb logcat -v time | tee partial_fix_test_$(date +%Y%m%d_%H%M%S).log
```

### 3. 执行测试用例

#### 测试用例1：短语不应触发fallback ⭐
**目的：** 验证低分Partial不会触发fallback

**操作：**
1. 唤醒词："하이 넛지"
2. 说一半的话："가..." 或 "어..." 或 "윈..."（只说1-2个字）
3. 等待3秒，让系统超时

**预期结果：**
```log
✅ 日志应包含：
D SkillEvaluator: 🔍 [Partial] 尝试匹配技能: '가.'
D SkillEvaluator: ⏸️ [Partial] 分数较低(0.x < 0.7)，等待Final结果
D SenseVoiceInputDevice: 🔇 检测到静音超时

❌ 日志不应包含：
- "💬 New skill output generated" (在Partial阶段)
- "다시 말씀해 주시겠어요?" (TTS播放)
- "LISTENING → SPEAKING → IDLE" (快速状态切换)
```

**修复前的问题：**
- 会播放TTS："다시 말씀해 주시겠어요?"
- 状态快速切换

---

#### 测试用例2：高分匹配立即执行 ⭐⭐
**目的：** 验证高分Partial会立即执行

**操作：**
1. 唤醒词："하이 넛지"
2. 说："윈도 모드로" 或 "화면 켜줘"

**预期结果：**
```log
✅ 日志应包含：
D SkillEvaluator: 🎯 [Partial] 找到匹配: device_control, 分数: 0.7x
I SkillEvaluator: ✅ [Partial] 高分匹配(0.7x)，立即执行技能
D SkillEvaluator: 🎯 使用预匹配技能: device_control, 评分: 0.7x
I AutoTest: 技能执行: device_control, 结果: xxx
D SkillEvaluator: ⏭️ [Final] Partial已执行技能，跳过重复执行

❌ 日志不应包含：
- 重复的技能执行（Final不应再次执行）
```

**关键指标：**
- Partial执行次数 = Final跳过次数
- 无重复技能执行

---

#### 测试用例3：连续识别不被打断 ⭐⭐⭐
**目的：** 验证说话过程中不会被TTS打断

**操作：**
1. 唤醒词："하이 넛지"
2. 慢慢说一个完整指令："총... 나무... 원으로... 이동해줘"
   （故意说得慢，让系统产生多次Partial更新）

**预期结果：**
```log
✅ 日志应包含：
D 🎤[SenseVoiceRecognizer]: SenseVoice识别结果: "총."
D SkillEvaluator: ⏸️ [Partial] 分数较低(0.x < 0.7)，等待Final结果
D 🎤[SenseVoiceRecognizer]: SenseVoice识别结果: "총나무."
D SkillEvaluator: ⏸️ [Partial] 分数较低(0.x < 0.7)，等待Final结果
...（多次Partial，但不触发技能）
D SkillEvaluator: 📥 收到Final事件: [총나무원으로 이동해줘.]

❌ 日志不应包含（在Final之前）：
- "💬 New skill output generated"
- "I AudioResourceManager: 🔊 TTS播放开始"
- "防止状态覆盖" (多次出现)
```

**体验：**
- 用户说话时不应该听到任何TTS声音
- 悬浮球状态应保持LISTENING，不应闪烁

---

#### 测试用例4：Final阶段fallback正常 ⭐
**目的：** 验证Final阶段仍然有fallback机制

**操作：**
1. 唤醒词："하이 넛지"
2. 说一个完全无法识别的指令："阿巴阿巴阿巴"

**预期结果：**
```log
✅ 日志应包含：
D SkillEvaluator: 📥 收到Final事件: [xxx]
D SkillRanker: ❌ 所有轮次都未通过阈值检查
D SkillEvaluator: ⚠️ 无匹配技能，使用fallback
I AutoTest: 技能执行: text, 结果: 다시 말씀해 주시겠어요?
```

**体验：**
- 应该播放TTS："다시 말씀해 주시겠어요?"
- 用户得到明确的反馈

---

## 验证日志

### 自动验证脚本
```bash
# 停止logcat（Ctrl+C）后运行
./scripts/verify_partial_fix.sh partial_fix_test_*.log
```

### 手动检查关键日志

#### ✅ 应该看到的日志（修复生效）
```bash
# 1. 阈值已更新为0.7
grep "分数较低.*< 0.7" partial_fix_test_*.log

# 2. 使用预匹配技能
grep "🎯 使用预匹配技能" partial_fix_test_*.log

# 3. Partial禁止fallback
grep "\[Partial\] 无匹配技能且禁止fallback" partial_fix_test_*.log

# 4. Partial和Final执行匹配
grep "✅ \[Partial\] 高分匹配" partial_fix_test_*.log | wc -l
grep "⏭️ \[Final\] Partial已执行技能" partial_fix_test_*.log | wc -l
# 两个数量应该相等或接近
```

#### ❌ 不应该看到的日志（修复失败）
```bash
# 1. Partial阶段不应该有这些日志组合
grep -A3 "🔍 \[Partial\] 尝试匹配技能" partial_fix_test_*.log | grep "💬 New skill output"

# 2. 不应该有大量状态覆盖警告
grep -c "防止状态覆盖" partial_fix_test_*.log
# 应该 < 5 次

# 3. 不应该有旧的0.5阈值
grep "分数较低.*< 0.5" partial_fix_test_*.log
# 应该为空
```

---

## 性能指标

### 修复前 vs 修复后

| 指标 | 修复前 | 修复后（目标） |
|------|--------|----------------|
| Partial阶段fallback次数 | 5-10次/对话 | 0次 |
| 状态覆盖警告 | 10-20次/对话 | <5次/对话 |
| TTS打断用户 | 经常发生 | 不应发生 |
| 状态切换次数 | 15-20次/对话 | 8-12次/对话 |
| Partial立即执行准确率 | 60-70% | 85-95% |

---

## 回归测试

确保修复不影响现有功能：

### 1. 基础唤醒和ASR
```bash
# 测试
唤醒 → "현재 시각 알려줘"
唤醒 → "화면 켜줘"
唤醒 → "잠금 화면으로 이동"

# 预期：所有指令正常执行
```

### 2. 连续对话
```bash
# 测试
唤醒 → "날씨 알려줘" → "내일은?" → "고마워"

# 预期：多轮对话正常
```

### 3. 长指令
```bash
# 测试
唤醒 → "서울역에서 강남역까지 가는 길 알려줘"

# 预期：导航功能正常
```

---

## 问题排查

### 如果测试失败

#### 问题1：仍然看到Partial阶段fallback
**检查：**
```bash
grep -B5 "💬 New skill output" test.log | grep "Partial"
```

**可能原因：**
- 代码未正确编译
- APK未正确安装

**解决：**
```bash
./gradlew clean
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

#### 问题2：没有看到"使用预匹配技能"日志
**检查：**
```bash
grep "🎯 使用预匹配技能" test.log
```

**可能原因：**
- 测试指令分数都 < 0.7
- 需要测试更明确的指令

**解决：**
使用确定性高的指令：
- "화면 켜줘" (开屏)
- "화면 꺼줘" (关屏)
- "잠금 화면" (锁屏)

---

#### 问题3：高分匹配但未执行
**检查：**
```bash
grep -A5 "✅ \[Partial\] 高分匹配" test.log | grep "使用预匹配技能"
```

**可能原因：**
- `partialSkillExecuted` 标记问题
- 并发控制异常

**解决：**
查看是否有 "⏭️ [Partial] 技能已被执行，跳过" 日志

---

## 成功标准

修复被认为成功，如果：
- ✅ 测试用例1通过（短语不触发fallback）
- ✅ 测试用例2通过（高分匹配立即执行）
- ✅ 测试用例3通过（连续识别不被打断）
- ✅ 测试用例4通过（Final fallback正常）
- ✅ 自动验证脚本通过 ≥4/6 检查
- ✅ 所有回归测试通过

---

## 后续步骤

### 如果测试通过
1. 提交代码并创建PR
2. 更新用户文档
3. 在生产环境观察1-2天
4. 收集用户反馈

### 如果测试失败
1. 记录失败日志和场景
2. 分析根本原因
3. 调整修复方案
4. 重新测试

---

## 联系信息

如有问题，请查看：
- `GO_HOME_FAILURE_ROOT_CAUSE_ANALYSIS.md` - 根本原因分析
- `PARTIAL_FALLBACK_FIX_SUMMARY.md` - 修复详细说明
- `scripts/verify_partial_fix.sh` - 自动验证脚本

