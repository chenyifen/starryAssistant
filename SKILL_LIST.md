# 技能列表文档

本文档列出了系统中所有可用的技能及其子技能，用于自动化测试和验证。

## 技能格式说明

技能执行日志格式：`技能执行: <技能ID>:<子技能ID>, 结果: <执行结果>`

- 如果技能有子技能，格式为：`技能ID:子技能ID`（例如：`app_launcher:google`）
- 如果技能没有子技能，格式为：`技能ID`（例如：`text`）

---

## 1. 电源控制 (power_control)

**技能ID**: `power_control`

### 子技能列表

| 子技能ID | 说明 | 示例日志 |
|---------|------|---------|
| `power_off` | 关机 | `技能执行: power_control:power_off, 结果: ...` |
| `power_on` | 开机 | `技能执行: power_control:power_on, 结果: ...` |
| `volume_up` | 音量增加 | `技能执行: power_control:volume_up, 结果: ...` |
| `volume_down` | 音量减少 | `技能执行: power_control:volume_down, 结果: ...` |
| `mute_on` | 静音 | `技能执行: power_control:mute_on, 结果: ...` |

---

## 2. 输入源控制 (input_source_control)

**技能ID**: `input_source_control`

### 子技能列表

| 子技能ID | 说明 | 示例日志 |
|---------|------|---------|
| `input_source` | 切换输入源 | `技能执行: input_source_control:input_source, 结果: ...` |
| `hdmi_one` | 切换到HDMI 1 | `技能执行: input_source_control:hdmi_one, 结果: ...` |
| `hdmi_two` | 切换到HDMI 2 | `技能执行: input_source_control:hdmi_two, 结果: ...` |
| `dp_port` | 切换到DP端口 | `技能执行: input_source_control:dp_port, 结果: ...` |
| `front_hdmi` | 切换到前置HDMI | `技能执行: input_source_control:front_hdmi, 结果: ...` |
| `front_usb_c` | 切换到前置USB-C | `技能执行: input_source_control:front_usb_c, 结果: ...` |
| `ops` | 切换到OPS | `技能执行: input_source_control:ops, 结果: ...` |

---

## 3. 应用启动器 (app_launcher)

**技能ID**: `app_launcher`

### 子技能列表

| 子技能ID | 说明 | 示例日志 |
|---------|------|---------|
| `google` | 打开Google | `技能执行: app_launcher:google, 结果: ...` |
| `browser` | 打开浏览器 | `技能执行: app_launcher:browser, 结果: ...` |
| `play_store` | 打开Play商店 | `技能执行: app_launcher:play_store, 结果: ...` |
| `youtube` | 打开YouTube | `技能执行: app_launcher:youtube, 结果: ...` |
| `settings` | 打开设置 | `技能执行: app_launcher:settings, 结果: ...` |
| `recorder` | 打开录音机 | `技能执行: app_launcher:recorder, 结果: ...` |
| `eshare` | 打开EShare | `技能执行: app_launcher:eshare, 结果: ...` |
| `camera` | 打开相机 | `技能执行: app_launcher:camera, 结果: ...` |
| `finder` | 打开文件管理器 | `技能执行: app_launcher:finder, 结果: ...` |

---

## 4. 白板工具 (whiteboard_tools)

**技能ID**: `whiteboard_tools`

### 子技能列表

| 子技能ID | 说明 | 示例日志 |
|---------|------|---------|
| `whiteboard` | 打开白板 | `技能执行: whiteboard_tools:whiteboard, 结果: ...` |
| `save_whiteboard` | 保存白板 | `技能执行: whiteboard_tools:save_whiteboard, 结果: ...` |
| `red_pen` | 选择红色笔 | `技能执行: whiteboard_tools:red_pen, 结果: ...` |
| `blue_pen` | 选择蓝色笔 | `技能执行: whiteboard_tools:blue_pen, 结果: ...` |
| `white_pen` | 选择白色笔 | `技能执行: whiteboard_tools:white_pen, 结果: ...` |
| `black_pen` | 选择黑色笔 | `技能执行: whiteboard_tools:black_pen, 结果: ...` |
| `eraser` | 选择橡皮擦 | `技能执行: whiteboard_tools:eraser, 结果: ...` |
| `delete_all` | 删除全部 | `技能执行: whiteboard_tools:delete_all, 结果: ...` |
| `highlight_pen` | 选择高亮笔 | `技能执行: whiteboard_tools:highlight_pen, 结果: ...` |
| `fountain_pen` | 选择钢笔 | `技能执行: whiteboard_tools:fountain_pen, 结果: ...` |
| `brush_pen` | 选择画笔 | `技能执行: whiteboard_tools:brush_pen, 结果: ...` |
| `add_page` | 添加页面 | `技能执行: whiteboard_tools:add_page, 结果: ...` |
| `delete_page` | 删除页面 | `技能执行: whiteboard_tools:delete_page, 结果: ...` |
| `next_page` | 下一页 | `技能执行: whiteboard_tools:next_page, 结果: ...` |
| `previous_page` | 上一页 | `技能执行: whiteboard_tools:previous_page, 结果: ...` |

---

## 5. 系统导航 (system_navigation)

**技能ID**: `system_navigation`

### 子技能列表

| 子技能ID | 说明 | 示例日志 |
|---------|------|---------|
| `home_screen` | 返回主屏幕 | `技能执行: system_navigation:home_screen, 结果: ...` |
| `go_back` | 返回上一页 | `技能执行: system_navigation:go_back, 结果: ...` |
| `screenshot` | 截图 | `技能执行: system_navigation:screenshot, 结果: ...` |
| `note_mode` | 切换到批注模式 | `技能执行: system_navigation:note_mode, 结果: ...` |
| `window_mode` | 切换到窗口模式 | `技能执行: system_navigation:window_mode, 结果: ...` |
| `wifi_connect` | 打开WiFi设置 | `技能执行: system_navigation:wifi_connect, 结果: ...` |

---

## 6. 回退技能 (text)

**技能ID**: `text`

**说明**: 当无法识别具体命令时使用的回退技能，没有子技能。

**示例日志**: `技能执行: text, 结果: 이해하지 못했습니다`

---

## 技能统计

| 技能类别 | 技能ID | 子技能数量 | 总计 |
|---------|--------|-----------|------|
| 电源控制 | `power_control` | 5 | 5 |
| 输入源控制 | `input_source_control` | 7 | 7 |
| 应用启动器 | `app_launcher` | 9 | 9 |
| 白板工具 | `whiteboard_tools` | 15 | 15 |
| 系统导航 | `system_navigation` | 6 | 6 |
| 回退技能 | `text` | 0 | 0 |
| **总计** | **6个技能** | **42个子技能** | **42** |

---

## 测试验证

### 日志过滤命令

```bash
# 查看所有技能执行日志
adb logcat -s AutoTest:* | grep "技能执行"

# 查看特定技能的执行日志（例如：app_launcher）
adb logcat -s AutoTest:* | grep "技能执行.*app_launcher"

# 查看特定子技能的执行日志（例如：app_launcher:google）
adb logcat -s AutoTest:* | grep "技能执行.*app_launcher:google"
```

### 验证要点

1. **技能ID格式**: 确认日志中显示的技能ID格式正确（`技能ID:子技能ID`）
2. **子技能识别**: 确认子技能ID正确提取（例如：`app_launcher:google` 而不是 `app_launcher`）
3. **回退技能**: 确认无法识别的命令正确使用 `text` 技能（没有子技能）
4. **执行结果**: 确认每个技能执行后都有相应的结果描述

---

## 更新日期

- **创建日期**: 2025-11-05
- **最后更新**: 2025-11-05

---

## 相关文件

- `/app/src/main/sentences/skill_definitions.yml` - 技能定义文件
- `/app/src/main/kotlin/com/ai/voice/util/AutoTestLogger.kt` - 自动化测试日志工具
- `/app/src/main/kotlin/com/ai/voice/eval/SkillEvaluator.kt` - 技能评估器（包含子技能提取逻辑）

