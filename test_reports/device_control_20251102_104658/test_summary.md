# DeviceControl 测试总结报告

## 测试信息
- **测试时间**: 2025-11-02 10:47:58
- **测试类型**: DeviceControl仪器测试
- **测试结果**: ✅ 通过

## 文件结构
```
test_reports/device_control_20251102_104658/
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
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testEshare(DeviceControlInstrumentationTest.kt:361)
....

Time: 57.553
There was 1 failure:
1) testEshare(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
java.lang.AssertionError: Eshare should succeed
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testEshare(DeviceControlInstrumentationTest.kt:361)

FAILURES!!!
Tests run: 26,  Failures: 1


```
### 关键日志信息
```
11-01 22:47:58.302   637   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 22:47:58.302   637   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 22:47:58.302   637   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 22:47:58.302   637   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 22:47:58.302   637   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 22:47:58.302   637   696 E [avmanager]hal_usart: FILE:vendor/cvte/avmanager/common/hal_usart.c, FUNC:writeUsartDevice, LINE:43, ERRNO:I/O error, wirte usart failed
11-01 22:47:58.302   637   696 E [avmanager]DSP: dspHalWriteUsart,576 error: retlen= -1
11-01 22:47:58.545  7610  7632 I TestRunner: finished: testVolumeDown(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
11-01 22:47:58.550  7610  7632 I TestRunner: run finished: 26 tests, 1 failed, 1 ignored
11-01 22:47:58.595   720  7593 E adbd    : error reading output FD 96: Connection reset by peer
```
