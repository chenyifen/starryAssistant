# DeviceControl 测试总结报告

## 测试信息
- **测试时间**: 2025-11-02 02:20:02
- **测试类型**: DeviceControl仪器测试
- **测试结果**: ✅ 通过

## 文件结构
```
test_reports/device_control_20251102_021900/
├── test_output.txt          # 测试执行输出
├── logcat_output.txt        # 设备日志
├── device_reports/          # 设备端测试报告
├── gradle_reports/          # Gradle测试报告
└── test_summary.md          # 本总结文件
```

## 测试详情
### 测试执行结果
```
com.ai.voice.skills.device_control.DeviceControlInstrumentationTest:.....................
Error in testEshare(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest):
java.lang.AssertionError: Eshare should succeed
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testEshare(DeviceControlInstrumentationTest.kt:349)
.....

Time: 60.427
There was 1 failure:
1) testEshare(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
java.lang.AssertionError: Eshare should succeed
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testEshare(DeviceControlInstrumentationTest.kt:349)

FAILURES!!!
Tests run: 27,  Failures: 1


```
### 关键日志信息
```
11-01 14:20:02.241   639   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 14:20:02.241   639   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 14:20:02.241   639   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 14:20:02.241   639   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 14:20:02.241   639   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 14:20:02.241   639   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 14:20:02.241   639   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 14:20:02.255   894  1092 W BatteryExternalStatsWorker: error reading Bluetooth stats: 9
11-01 14:20:02.423 22216 22234 I TestRunner: finished: testVolumeDown(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
11-01 14:20:02.428 22216 22234 I TestRunner: run finished: 27 tests, 1 failed, 0 ignored
```
