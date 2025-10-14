# 设备控制集成说明

## 概述
本文档说明了如何集成Hyundai IT API AAR以实现设备控制功能。该功能已集成到Dicio语音助手的`device_control`技能中。

## 集成的AAR文件
- **文件名**: `hyundaiit-api-v0.1-2025-10-14-16-release.aar`
- **位置**: `/Users/user/AndroidStudioProjects/dicio-android/app/libs/`
- **大小**: 279KB

## API接口映射

### AudioHelper (音频控制)
| 命令 | API调用 | 说明 |
|------|---------|------|
| Turn the volume up | `AudioHelper.getInstance().volumeUp()` | 增大音量 |
| Turn the volume down | `AudioHelper.getInstance().volumeDown()` | 降低音量 |
| Mute on | `AudioHelper.getInstance().changeMuteStatus()` | 切换静音状态 |

### ScreenHelper (电源控制)
| 命令 | API调用 | 说明 |
|------|---------|------|
| Turn off the power | `ScreenHelper.getInstance().turnOffPower()` | 关闭电源 |
| Turn on the power | `ScreenHelper.getInstance().turnOnPower()` | 打开电源 |

### SourceHelper (信号源控制)
| 命令 | API调用 | 说明 |
|------|---------|------|
| Switch to HDMI one | `SourceHelper.getInstance().switchToHDMI1()` | 切换到HDMI 1 |
| Switch to HDMI two | `SourceHelper.getInstance().switchToHDMI2()` | 切换到HDMI 2 |
| Switch to DP port | `SourceHelper.getInstance().switchToDP()` | 切换到DP端口 |
| Switch to Front HDMI | `SourceHelper.getInstance().switchToFrontHDMI()` | 切换到前置HDMI |
| Switch to Front USB C | `SourceHelper.getInstance().switchToFrontUSBC()` | 切换到前置USB-C |
| Switch to OPS | `SourceHelper.getInstance().switchToOPS()` | 切换到OPS |

### SystemHelper (系统控制)
| 命令 | API调用 | 说明 |
|------|---------|------|
| Open the input source window | `SystemHelper.getInstance().openInputSourceWindow(context)` | 打开输入源窗口 |
| Go to the home screen | `SystemHelper.getInstance().gotoHomeScreen(context)` | 回到主屏幕 |
| Go to Google | `SystemHelper.getInstance().gotoGoogle(context)` | 打开Google |
| Open the browser | `SystemHelper.getInstance().openBrowser(context)` | 打开浏览器 |
| Open the Play Store | `SystemHelper.getInstance().openPlayStore(context)` | 打开Play Store |
| Open the Youtube | `SystemHelper.getInstance().openYoutube(context)` | 打开Youtube |
| Open the settings | `SystemHelper.getInstance().openSettings(context)` | 打开设置 |
| Open the recorder | `SystemHelper.getInstance().openRecorder(context)` | 打开录音机 |
| Open the E share | `SystemHelper.getInstance().openEShare(context)` | 打开E-Share |
| Open the Camera | `SystemHelper.getInstance().openCamera(context)` | 打开相机 |
| Take a screenshot | `SystemHelper.getInstance().takeScreenShot(imagePath)` | 截图 |
| Open the Finder | `SystemHelper.getInstance().openFinder(context)` | 打开文件管理器 |

## 代码修改说明

### 1. build.gradle.kts修改
在`app/build.gradle.kts`中添加了AAR依赖:

```kotlin
// Hyundai IT API AAR (设备控制接口)
implementation(files("libs/hyundaiit-api-v0.1-2025-10-14-16-release.aar"))
```

### 2. AndroidManifest.xml修改
位置: `/app/src/main/AndroidManifest.xml`

添加了SDK版本覆盖声明,允许使用最低SDK版本为26的Hyundai IT API库:

```xml
<!-- Override minSdkVersion check for Hyundai IT API library -->
<uses-sdk tools:overrideLibrary="com.ifpdos.sdklib.hyundaiit" />
```

**注意**: 虽然项目的minSdk为21,但Hyundai IT API需要至少SDK 26。使用`tools:overrideLibrary`允许在较低版本设备上编译,但在运行时需要确保设备版本≥26才能正常使用这些功能。

### 3. DeviceControlSkill.kt修改
位置: `/app/src/main/kotlin/org/stypox/dicio/skills/device_control/DeviceControlSkill.kt`

#### 导入的类
```kotlin
import com.ifpdos.sdklib.hyundaiit.api.audio.AudioHelper
import com.ifpdos.sdklib.hyundaiit.api.screen.ScreenHelper
import com.ifpdos.sdklib.hyundaiit.api.source.SourceHelper
import com.ifpdos.sdklib.hyundaiit.api.system.SystemHelper
```

#### 实现的方法
所有设备控制命令都已更新为使用Hyundai IT API,包括:
- 电源控制 (`executePowerOff`, `executePowerOn`)
- 音量控制 (`executeVolumeUp`, `executeVolumeDown`, `executeMute`)
- 信号源控制 (`executeHdmiOne`, `executeHdmiTwo`, `executeDpPort`, `executeFrontHdmi`, `executeFrontUsbC`, `executeOps`)
- 系统功能 (`executeInputSource`, `executeHomeScreen`, `executeGoogle`, `executeBrowser`, `executePlayStore`, `executeYoutube`, `executeSettings`, `executeCamera`, `executeScreenshot`, `executeRecorder`, `executeEshare`, `executeFinder`)

### 4. 多语言支持

该技能已支持**中文、英文、韩文**三种语言的命令识别。

#### 英文句子定义
文件: `/app/src/main/sentences/en/device_control.yml`

包含所有命令的英文表达方式,例如:
- `turn off the power`, `power off`, `shut down`
- `turn the volume up`, `increase the volume`, `louder`
- `switch to hdmi one`, `go to hdmi one`

#### 中文句子定义
文件: `/app/src/main/sentences/zh-CN/device_control.yml`

包含所有命令的中文表达方式,例如:
- `关机`, `关闭电源`, `断电`
- `音量增加`, `音量加大`, `声音大一点`
- `切换到hdmi一`, `进入hdmi一`

#### 韩文句子定义
文件: `/app/src/main/sentences/ko/device_control.yml`

包含所有命令的韩文表达方式,例如:
- `전원 끄기`, `전원 꺼줘`, `기기 끄기`
- `볼륨 높이기`, `볼륨 올리기`, `소리 크게`
- `hdmi 일로 전환`, `hdmi 하나로 이동`

### 5. 技能定义
文件: `/app/src/main/sentences/skill_definitions.yml`

已包含所有设备控制命令的定义:
- power_off, power_on
- volume_up, volume_down, mute_on
- input_source, hdmi_one, hdmi_two, dp_port, front_hdmi, front_usb_c, ops
- home_screen, google, browser, play_store, youtube
- settings, recorder, eshare, camera, screenshot, finder
- 以及白板相关的控制命令

## 错误处理
所有方法都包含完善的try-catch错误处理机制,在调用API失败时会:
1. 记录错误日志(使用Log.e)
2. 返回包含错误信息的DeviceControlOutput
3. 将success标志设为false

## 使用示例

### 英文命令
- "Turn off the power" - 关机
- "Turn the volume up" - 增大音量
- "Switch to HDMI one" - 切换到HDMI 1
- "Open the Play Store" - 打开Play Store
- "Take a screenshot" - 截图

### 中文命令
- "关机" - 关闭电源
- "音量增加" - 增大音量
- "切换到hdmi一" - 切换到HDMI 1
- "打开应用商店" - 打开Play Store
- "截图" - 截图

### 韩文命令
- "전원 끄기" - 关机
- "볼륨 높이기" - 增大音量
- "hdmi 일로 전환" - 切换到HDMI 1
- "플레이 스토어 열기" - 打开Play Store
- "스크린샷 찍기" - 截图

## 技术特性

### 1. 多语言识别
DeviceControlSkill实现了多语言识别功能,可以同时识别英文、中文、韩文命令,无需切换语言设置。

### 2. 智能评分
使用三种语言的句子数据进行评分,选择匹配度最高的识别结果。

### 3. 异常处理
所有API调用都包含完善的异常处理机制,确保系统稳定性。

## 构建和测试

### 编译项目
```bash
./gradlew assembleDebug
```

### 测试设备控制功能
1. 启动Dicio语音助手
2. 说出任意设备控制命令(支持中/英/韩文)
3. 系统会调用相应的Hyundai IT API执行命令
4. 返回执行结果的语音和图形反馈

## 注意事项

1. **权限要求**: 某些功能(如截图)可能需要特定的系统权限
2. **设备兼容性**: Hyundai IT API仅在支持的Hyundai设备上可用
3. **初始化**: 确保在使用API之前,相关服务已正确初始化
4. **错误处理**: 如果API调用失败,会在日志中记录详细错误信息
5. **SDK版本要求**: Hyundai IT API需要Android SDK 26 (Android 8.0) 或更高版本。虽然项目设置了`tools:overrideLibrary`允许编译,但在低于SDK 26的设备上可能会出现运行时错误

## 维护说明

### 添加新命令
1. 在`skill_definitions.yml`中添加命令定义
2. 在各语言的`device_control.yml`中添加句子
3. 在`DeviceControlSkill.kt`的`generateOutput`方法中添加case处理
4. 实现对应的execute方法

### 更新API
如果需要更新Hyundai IT API AAR:
1. 替换`app/libs/`目录下的AAR文件
2. 更新`build.gradle.kts`中的文件名
3. 检查API变更,更新`DeviceControlSkill.kt`中的调用代码

## 相关文件清单

```
app/
├── build.gradle.kts                                          # 添加AAR依赖
├── libs/
│   └── hyundaiit-api-v0.1-2025-10-14-16-release.aar        # API库文件
└── src/main/
    ├── AndroidManifest.xml                                  # 添加SDK版本覆盖(已修改)
    ├── kotlin/org/stypox/dicio/
    │   ├── eval/
    │   │   └── SkillHandler.kt                              # 技能注册
    │   └── skills/device_control/
    │       ├── DeviceControlInfo.kt                         # 技能信息
    │       ├── DeviceControlSkill.kt                        # 技能实现(已修改)
    │       └── DeviceControlOutput.kt                       # 输出定义
    └── sentences/
        ├── skill_definitions.yml                            # 技能定义
        ├── en/
        │   └── device_control.yml                           # 英文句子
        ├── zh-CN/
        │   └── device_control.yml                           # 中文句子
        └── ko/
            └── device_control.yml                           # 韩文句子
```

## 更新日期
2025-10-14

## 作者
Claude (AI Assistant)

