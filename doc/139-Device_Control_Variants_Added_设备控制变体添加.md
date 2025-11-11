# Device Control 命令相近词添加总结

## 概述
基于真人录音识别数据 (`command_recognized_texts_20251102_141902.json`)，为 `device_control.yml` 添加了大量相近词和常见识别错误变体。

## 更新时间
2025-11-03

## 韩语命令更新 (ko/device_control.yml)

### 1. 电源控制命令

#### power_off (关机)
- 添加识别错误: `전원 꺼져`, `저원 꺼줘`, `전원 꺼 줘`, `꺼져`

#### power_on (开机)
- 添加识别错误: `전화 켜줘`, `전화 켜 줘`, `전 원 켜 줘`, `저 원`

### 2. 音量控制命令

#### volume_down (音量降低)
- 添加大量变体: `벌륨 멀려줘`, `얼륨 내려줘`, `볼륨 돼`, `볼륨 내`, `볼륨 네`, `지금 내려줘`, `내려 줘`, `내 줘` 等

#### mute_on (静音)
- 添加: `음소가 해줘`, `음소가 해 줘`, `음속거 해 줘`, `음속어 해줘`, `음소 어 해줘`, `만꺼줘`, `안꺼줘` 等

### 3. 输入源控制

#### input_source
- 添加: `입력소`, `입 약속 수`, `인력 소스 장 띄워줘`, `입력소스장 띄워져` 等

#### hdmi_one/hdmi_two
- 添加大量HDMI发音变体:
  - `에치 디엠아이 일`, `에치 디에 마이 원`, `에치 데마1`
  - `에이치 디엠마이2`, `에치 디에마이2`, `H DM2` 等

#### dp_port (DisplayPort)
- 添加: `디스플레이포트`, `디피포트`, `리피 포`, `디피포트트`, `리피포트트` 等

#### front_usb_c
- 添加: `앞쪽 USBC`, `유에스비 씨`, `유에쓰비 씨`, `US비`, `스비 씨`, `쓰비 씨` 等

#### ops
- 添加: `오피피에스`, `오피스`, `오피`, `오피피`, `우피` 等

### 4. 导航命令

#### home_screen (主屏幕)
- 添加众多变体: `화면 으로 이동해 줘`, `면으로 이동해줘`, `홈무 화면 으로 이동해`, `홈화으로 이동해줘` 等

#### google
- 添加: `국을 연결해줘`, `구을 연결해줘`, `구글 연결 되 죠`, `구글해 줘`, `곡을 연결해줘` 等

### 5. 应用启动命令

#### browser (浏览器)
- 添加: `인터넷`, `인터넷 열기`, `인터넷 연결`, `브라우저 실행해줘`

#### play_store
- 添加: `플레이토`, `플레이 투`, `플레이2`, `플레이 투어`, `포레이토`, `플레이스 투어` 等

#### youtube
- 添加: `유튜브 실행해죠`, `유튜브 실 행해 줘`, `유튜브에 실행해줘`, `유투브`, `유튜브 실행 행해줘` 等

#### whiteboard (白板)
- 添加大量变体: `화이트 보드`, `트보드`, `이트보드`, `보드 실행해줘`, `화이트 모드` 等

#### camera (相机)
- 添加: `카라 십`, `카메 시`, `카라 시`, `카메라 실행 행해 줘` 等

### 6. 白板工具命令

#### red_pen (红笔)
- 添加: `빨강색 펜 대체`, `빨간색펜`, `빨간색 패`, `빨강색펜`, `팬 빨간색 팬`, `빨간재팬` 等

#### blue_pen (蓝笔)
- 添加: `파랑색 펜 대체`, `파란색팩`, `파랑색팩`, `바랑`, `바란색`, `파랑색펜`, `파랑색 배` 等

#### black_pen (黑笔)
- 添加: `검정색 팬`, `검정색 패`

#### white_pen (白笔)
- 添加: `흰색 팬`

#### eraser (橡皮擦)
- 保持现有命令

#### delete_all (全部删除)
- 添加: `모두 삭제해줘`, `우모두 지워줘`, `우개 모두 지워줘`, `게`

#### highlight_pen (荧光笔)
- 添加: `형광 팬`, `H know`

#### brush_pen (毛笔)
- 添加: `붓 펜`, `부end`, `부텐`, `P펜`, `부펜`

### 7. 其他命令

#### save_whiteboard (保存白板)
- 添加: `저장 해줘`, `파일`, `파일과`, `파일 읽어 거`

#### settings (设置)
- 添加: `정 창 보여줘`, `붓펜 설정 창 보여줘`, `설정 창 보여줘`, `일정 창 보여줘`

#### recorder (录音机)
- 添加: `녹음기 실행해줘`, `화면`, `화면 넣화`, `화면캡`, `화면 녹화`

#### eshare
- 添加: `이쉐어 실행해줘`, `이쉐 실행해줘`, `이 쉐어 실행해줘`

#### screenshot (截图)
- 添加: `화면`, `화면 캡쳐야죠`, `화면 캡쳐 해줘`

#### finder (文件管理器)
- 添加: `파일 관리자`, `파일 관리`, `파일 관리자 실행해줘`

#### note_mode (批注模式)
- 添加: `판소 모 드로 바꿔 줘`, `탄서 모드로 바꿔줘`, `모 드로 봐`, `또 모 드로` 等

## 英语命令更新 (en/device_control.yml)

### 1. 基础命令简化

#### power_off
- 添加: `shutdown`, `power off now`, `turn it off`, `switch off`

#### power_on
- 添加: `power on now`, `turn it on`, `switch on`, `boot up`

### 2. 音量控制简化

#### volume_up
- 添加: `vol up`, `volume higher`, `pump up the volume`

#### volume_down
- 添加: `vol down`, `volume lower`, `quiet down`

#### mute_on
- 添加: `mute it`, `quiet`, `silent mode`, `no sound`

### 3. 应用和导航简化

#### google
- 添加: `search google`, `google search`, `google dot com`, `go google`

#### browser
- 添加: `internet`, `web`, `go online`, `surf the web`

#### youtube
- 添加: `watch youtube`, `youtube videos`, `play youtube`

## 总体统计

- **韩语命令**: 添加了 **500+** 个识别变体和相近词
- **英语命令**: 添加了 **50+** 个简化命令和常用变体
- **覆盖命令类型**: 30+ 种设备控制命令

## 识别改进预期

基于真人录音数据分析，这些更新应该能显著提高以下场景的识别率:

1. **韩语语音识别错误**: 特别是HDMI、USB-C等技术术语的韩语发音
2. **空格和连读问题**: 如 "화이트보드" vs "화이트 보드"
3. **发音相近词**: 如 "음소거" vs "음속거" / "음소가"
4. **简化表达**: 用户倾向使用更短的命令

## 测试建议

1. 使用更新后的配置重新测试原始录音数据
2. 关注HDMI、USB-C、白板工具等命令的识别率提升
3. 测试英语简化命令的识别效果

## 相关文件

- `/app/src/main/sentences/ko/device_control.yml` - 韩语命令配置
- `/app/src/main/sentences/en/device_control.yml` - 英语命令配置
- `/command_recognized_texts_20251102_141902.json` - 原始录音识别数据
- `/app/src/main/kotlin/com/ai/voice/skills/device_control/DeviceControlSkill.kt` - 命令处理逻辑

## 注意事项

1. 部分变体可能包含识别错误的词(如 "부end", "P펜"),但基于真实数据保留以提高识别率
2. 某些变体看起来不常规,但确实是用户在语音交互时可能出现的发音
3. 建议定期基于新的录音数据继续优化命令集

## ⚠️ 技术限制

### 不能使用阿拉伯数字

**原因**: `dicio-sentences-compiler` 的解析器设计限制

- ❌ 不能使用: `에치 데마1`, `플레이2`, `구글0` 等包含阿拉伯数字的命令
- ✅ 必须使用: `에치 데마일`, `플레이 투`, 韩语数字表达

**技术细节**:
- **Tokenizer** 会将包含数字的词标记为 `lettersPlusOther` 类型
- **Parser** 只接受纯 `letters` 类型的token来构建单词
- 结果：包含数字的词会导致编译错误 "Expected sentence construct or end of file"

**源码位置**:
```java
// Tokenizer.java:55-57
private boolean isOtherValid(String ch) {
    return !ch.isEmpty() && (Character.isDigit(ch.codePointAt(0)) || ch.equals("_"));
}

// Parser.java:308-311  
if (ts.get(0).isType(Token.Type.letters) // 只接受纯letters！
    && (parts.isEmpty() || parts.get(parts.size() - 1).size() > 1)) {
    parts.add(Collections.singletonList(ts.get(0).getValue().toLowerCase()));
```

**解决方案**: 所有数字都已转换为韩语文字表达

---
**更新人**: AI Assistant  
**数据来源**: command_recognized_texts_20251102_141902.json (真人录音识别数据)

