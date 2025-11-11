# 自动化测试 API

## 功能说明
支持外部应用通过广播触发语音识别，用于自动化测试。

## 使用方法

### 1. 启动语音识别

发送广播启动ASR识别：

```bash
adb shell am broadcast -a org.stypox.dicio.AUTO_TEST_START
```

### 2. 查看识别结果

监听AutoTest日志查看ASR识别结果：

```bash
adb logcat -s AutoTest
```

输出示例：
```
I/AutoTest: ASR结果: 你好世界
```

## 完整测试流程

```bash
# 1. 启动语音识别
adb shell am broadcast -a org.stypox.dicio.AUTO_TEST_START

# 2. 说话后等待几秒

# 3. 查看结果（另开终端）
adb logcat -s AutoTest

# 或者实时监听
adb logcat -s AutoTest | grep "ASR结果"
```

## Python 自动化示例

```python
import subprocess
import time
import re

def trigger_asr_test():
    """触发ASR测试并获取结果"""
    # 清空日志
    subprocess.run(['adb', 'logcat', '-c'])
    
    # 启动ASR
    subprocess.run(['adb', 'shell', 'am', 'broadcast', 
                    '-a', 'org.stypox.dicio.AUTO_TEST_START'])
    
    print("已启动ASR，请说话...")
    time.sleep(5)  # 等待用户说话
    
    # 获取结果
    result = subprocess.run(['adb', 'logcat', '-d', '-s', 'AutoTest'],
                          capture_output=True, text=True)
    
    # 解析结果
    for line in result.stdout.split('\n'):
        match = re.search(r'ASR结果: (.+)', line)
        if match:
            asr_text = match.group(1)
            print(f"识别结果: {asr_text}")
            return asr_text
    
    print("未获取到识别结果")
    return None

# 使用示例
if __name__ == '__main__':
    asr_result = trigger_asr_test()
```

## 技术实现

1. **触发方式**：
   - 广播：`org.stypox.dicio.AUTO_TEST_START`
   - 接收器：`EnhancedFloatingWindowService`
   - 效果：模拟点击悬浮球，进入听取状态

2. **结果输出**：
   - 标签：`AutoTest`
   - 时机：收到ASR Final事件时
   - 位置：`SkillEvaluator.kt` 第95行

## 清理方法

如果需要移除自动化测试功能：

1. 删除 `EnhancedFloatingWindowService.kt` 中：
   - 第109-115行：companion object常量定义
   - 第109行：`autoTestReceiver`变量
   - 第156行：`registerAutoTestReceiver()`调用
   - 第168行：`unregisterAutoTestReceiver()`调用
   - 第458-504行：三个测试相关函数

2. 删除 `SkillEvaluator.kt` 第95行：
   ```kotlin
   Log.i("AutoTest", "ASR结果: $firstUtterance")
   ```

3. 删除此文档

## 注意事项

- 需要悬浮窗服务已启动
- 需要麦克风权限
- 使用前确保悬浮球处于待机状态
- 每次只能处理一个识别任务

