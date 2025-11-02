# ✅ Partial阶段Fallback修复 - 准备测试

## 📋 修复摘要

**问题：** Partial ASR阶段意外触发fallback技能，导致TTS打断用户输入和状态混乱

**修复方案：** 方案A - 统一阈值并禁止Partial阶段fallback

**修复状态：** ✅ 代码已完成，等待真机测试验证

---

## 🔧 已完成的修改

### 1. 修改文件
- `app/src/main/kotlin/com/ai/voice/eval/SkillEvaluator.kt`

### 2. 关键修改
1. **阈值统一**：0.5 → 0.7（第170行）
2. **方法签名扩展**：添加`preMatchedSkill`和`allowFallback`参数（第214-218行）
3. **Partial调用**：使用预匹配结果，禁止fallback（第184-189行）
4. **Fallback控制**：只有Final阶段允许fallback（第240-247行）

### 3. 编译状态
- ✅ 无语法错误
- ✅ 无Lint错误
- ✅ 代码逻辑已验证

---

## 🧪 测试准备

### 快速开始测试

```bash
# 1. 编译安装
cd /Users/user/AndroidStudioProjects/dicio-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. 收集日志
adb logcat -c
adb logcat -v time '*:D' | tee test_partial_fix_$(date +%Y%m%d_%H%M%S).log

# 3. 执行测试（另一个终端窗口）
# 见 PARTIAL_FIX_TEST_GUIDE.md

# 4. 验证结果（测试完成后）
./scripts/verify_partial_fix.sh test_partial_fix_*.log
```

---

## ✅ 核心测试用例

### 最关键的测试（必测）
1. **短语不触发fallback** ⭐⭐⭐
   - 操作：唤醒 → 只说1-2个字 → 停顿
   - 预期：不播放TTS，不触发fallback

2. **高分立即执行** ⭐⭐
   - 操作：唤醒 → "윈도 모드로"
   - 预期：Partial立即执行，Final跳过

3. **连续识别不打断** ⭐⭐⭐
   - 操作：唤醒 → 慢慢说完整指令
   - 预期：说话过程中无TTS播放

---

## 📊 预期效果

### 修复前的问题
```log
❌ 01:20:54.741 D 🎤: SenseVoice识别结果: "어."
❌ 01:20:54.746 D 🎨: 💬 New skill output generated
❌ 01:20:54.749 D 🎨: UI state changed: LISTENING → SPEAKING
❌ 01:20:54.750 D 🎨: TTS: '다시 말씀해 주시겠어요?'
```

### 修复后的预期
```log
✅ 01:20:54.741 D 🎤: SenseVoice识别结果: "어."
✅ 01:20:54.746 D SkillEvaluator: ⏸️ [Partial] 分数较低(0.3 < 0.7)，等待Final结果
✅ (继续等待更多输入，无状态切换，无TTS播放)
```

---

## 📝 验证标准

### 自动验证通过条件
- ✅ Partial阶段fallback次数 = 0
- ✅ 阈值检查显示 `< 0.7`
- ✅ 预匹配技能使用次数 > 0（如果有高分Partial）
- ✅ 状态覆盖警告 < 5次
- ✅ Partial执行 ≈ Final跳过

### 用户体验验证
- ✅ 说话时不被TTS打断
- ✅ 悬浮球状态稳定，不闪烁
- ✅ 高分指令响应快速
- ✅ 低分指令等待Final结果

---

## 🔍 如果测试失败

### 常见问题排查
1. **APK未更新**
   ```bash
   adb uninstall org.stypox.dicio.debug
   ./gradlew clean assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **日志过滤不正确**
   ```bash
   # 确保日志级别为DEBUG
   adb logcat -v time '*:D'
   ```

3. **测试指令分数过低**
   - 使用确定性高的指令：
     - "화면 켜줘" (开屏)
     - "화면 꺼줘" (关屏)
     - "잠금 화면" (锁屏)

---

## 📂 相关文档

1. **问题分析**
   - `GO_HOME_FAILURE_ROOT_CAUSE_ANALYSIS.md` - 根本原因深度分析

2. **修复说明**
   - `PARTIAL_FALLBACK_FIX_SUMMARY.md` - 详细修复方案和代码说明

3. **测试指南**
   - `PARTIAL_FIX_TEST_GUIDE.md` - 完整测试用例和验证方法

4. **验证工具**
   - `scripts/verify_partial_fix.sh` - 自动验证脚本

---

## 🎯 下一步行动

### 立即执行
```bash
# 编译并安装
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 开始测试（建议使用真人测试）
adb logcat -c
adb logcat -v time '*:D' | tee test_$(date +%Y%m%d_%H%M%S).log
```

### 测试完成后
1. 停止logcat（Ctrl+C）
2. 运行验证脚本：`./scripts/verify_partial_fix.sh test_*.log`
3. 查看验证结果
4. 如果通过，提交代码；如果失败，分析日志

---

## ⚠️ 注意事项

1. **测试环境**
   - 建议使用真机测试，模拟器可能无法准确模拟语音识别
   - 确保网络连接正常（如果需要）
   - 确保麦克风权限已授予

2. **测试时长**
   - 建议每个测试用例重复3-5次
   - 总测试时长约15-20分钟

3. **日志大小**
   - 测试日志可能较大，确保磁盘空间充足
   - 可以使用 `grep` 过滤关键日志

4. **回归测试**
   - 除了新功能测试，还需要执行回归测试
   - 确保现有功能未受影响

---

## 📞 反馈

测试完成后，请提供：
1. ✅ 验证脚本输出结果
2. 📝 关键日志片段（前100行包含问题的部分）
3. 🎯 测试用例通过情况（4个核心用例）
4. 💬 用户体验反馈（是否还会被打断？响应是否及时？）

---

**准备就绪，可以开始测试！** 🚀

