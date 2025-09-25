# 悬浮窗UI设计指南

## 概述

本文档描述了Dicio语音助手的全新悬浮窗UI设计，专为大平板设备优化，采用未来感科技霓虹风格。

## 设计理念

### 视觉风格
- **未来感 + 极简 + 科技霓虹风**
- **主色调**：
  - 电光蓝 (#4ADFFF)
  - 紫罗兰光泽 (#9A4DFF)
- **辅色**：
  - 极光绿 (#4DFFB8)
  - 银河灰 (#121212 背景)

### 质感元素
- **玻璃拟物（Glassmorphism）**：半透明面板，毛玻璃磨砂效果
- **光效（Neon Glow）**：UI元素发光描边
- **动态能量场（Energy Field）**：核心图标均有光能环绕

## 核心组件

### 1. 中央能量球（EnergyOrb）

#### 待机状态
- 微弱发光的能量球，表面有流动的极光纹理
- 缓慢呼吸式脉动（2秒周期）
- 下方显示文本：
  - 韩文：「Hi Nudget: 하이넛지」
  - 中文：「叫我"小艺小艺"」

#### 唤醒状态
- 能量球骤然亮起
- 光环快速扩散动画（500ms）
- 表面出现动态声波光环
- 下方显示：「듣고 있어요…」（"我在听…"）

#### 思考状态
- 三个旋转光点环绕能量球
- 光点带霓虹拖尾效果
- 背景极光加速流动
- 下方显示：「생각하고 있어요…」（"正在思考…"）

### 2. 语音文本显示

录音听取中，显示两行文本：
- **第一行**：ASR实时识别文本（电光蓝）
- **第二行**：TTS语音助手回复（极光绿）

### 3. 命令建议面板

点击能量球展开，包含常用命令：
- 打开计算器
- 打开相机
- 今天天气
- 设置闹钟
- 播放音乐
- 发送消息

### 4. 设置入口

右上角多面体图标，点击进入设置页面。

## 技术实现

### 文件结构
```
app/src/main/kotlin/org/stypox/dicio/ui/floating/
├── FloatingWindowService.kt          # 悬浮窗服务
├── FloatingWindowViewModel.kt        # 状态管理
└── components/
    ├── EnergyOrb.kt                  # 能量球组件
    └── FloatingAssistantUI.kt        # 主UI组件
```

### 权限要求
- `android.permission.SYSTEM_ALERT_WINDOW` - 悬浮窗权限

### 动画效果

#### 呼吸动画
```kotlin
val breathingAnimation = animateFloat(
    initialValue = 0.9f,
    targetValue = 1.1f,
    animationSpec = infiniteRepeatable(
        animation = tween(2000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )
)
```

#### 极光纹理旋转
```kotlin
val rotationAnimation = animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
        animation = tween(20000, easing = LinearEasing)
    )
)
```

#### 声波扩散
```kotlin
val waveAnimation = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = LinearEasing)
    )
)
```

## 使用方法

### 启动悬浮窗
1. 应用启动时自动请求悬浮窗权限
2. 授予权限后自动启动悬浮窗服务
3. 悬浮窗显示在屏幕中央

### 交互方式
- **点击能量球**：展开/收起命令建议
- **点击设置图标**：打开设置页面
- **点击命令按钮**：执行对应命令
- **语音唤醒**：说"小艺小艺"触发监听

### 状态指示
- **待机**：蓝色微光，呼吸动画
- **监听**：绿色光环，声波扩散
- **思考**：紫色光点，旋转动画

## 开发指南

### 添加新命令
在 `CommandSuggestionsPanel` 中的 `commands` 列表添加新项：
```kotlin
val commands = listOf(
    "新命令显示文本" to "command_id",
    // ...
)
```

### 自定义动画
继承或修改 `EnergyOrb` 组件中的动画参数：
```kotlin
// 修改呼吸频率
animation = tween(1500, easing = FastOutSlowInEasing)

// 修改颜色渐变
colors = listOf(
    CustomColor.copy(alpha = 0.8f),
    // ...
)
```

### 主题定制
在 `Color.kt` 中修改颜色定义：
```kotlin
val EnergyBlue = Color(0xFF4ADFFF)      // 电光蓝
val VioletGlow = Color(0xFF9A4DFF)      // 紫罗兰光泽
val AuroraGreen = Color(0xFF4DFFB8)     // 极光绿
```

## 测试

运行测试脚本：
```bash
./test_floating_ui.sh
```

测试要点：
1. 悬浮窗权限请求
2. 能量球动画效果
3. 状态切换响应
4. 命令建议交互
5. 设置页面跳转

## 注意事项

1. **性能优化**：动画使用硬件加速，避免过度绘制
2. **内存管理**：及时释放悬浮窗资源
3. **权限处理**：优雅处理权限拒绝情况
4. **兼容性**：支持Android 6.0+系统
5. **用户体验**：提供清晰的视觉反馈

## 未来扩展

- [ ] 手势控制（滑动、缩放）
- [ ] 更多动画效果（粒子系统）
- [ ] 自定义主题支持
- [ ] 多语言本地化
- [ ] 无障碍功能支持


