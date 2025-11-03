# DeviceControl 测试总结报告

## 测试信息
- **测试时间**: 2025-11-03 23:03:55
- **测试类型**: DeviceControl仪器测试（包含所有拆分后的技能）
- **测试范围**: PowerControl, InputSourceControl, AppLauncher, WhiteboardTools, SystemNavigation
- **测试结果**: ✅ 通过

## 文件结构
```
test_reports/device_control_20251103_230228/
├── test_output.txt          # 测试执行输出
├── logcat_output.txt        # 设备日志
├── device_reports/          # 设备端测试报告
├── gradle_reports/          # Gradle测试报告
└── test_summary.md          # 本总结文件
```

## 测试详情
### 测试执行结果
```

com.ai.voice.skills.device_control.DeviceControlInstrumentationTest:.....................................

Time: 84.917

OK (37 tests)


```
### 关键日志信息
```
11-04 00:03:54.432   341  2664 E [CLOG][NFC][ERROR]: _checkNfcIsNormal failed
11-04 00:03:54.432   341  2664 E [CLOG][NFC][ERROR]: readlink Failed: retval -1
11-04 00:03:54.432   341  2664 E [CLOG][NFC][ERROR]: _checkNfcIsNormal failed
11-04 00:03:54.432   341  2664 E [CLOG][NFC][ERROR]: readlink Failed: retval -1
11-04 00:03:54.432   341  2664 E [CLOG][NFC][ERROR]: _checkNfcIsNormal failed
11-04 00:03:54.457   341  2664 E [CLOG][NFC][ERROR]: nfc device not found
11-04 00:03:54.457   341  2664 E [CLOG][NFC][ERROR]: _checkNfcIsNormal failed
11-04 00:03:54.471   341  2664 E [CLOG][NFC][ERROR]: nfc device not found
11-04 00:03:54.471   341  2664 E [CLOG][NFC][ERROR]: _checkNfcIsNormal failed
11-04 00:03:54.504   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.504   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.504   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.518   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.518   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.518   880   880 E CameraConfig: [getFileModifyTime] file /system/etc/systemConfig.xml open error ( No such file or directory )!
11-04 00:03:54.523   725   803 W libc    : Unable to set property "vendor.vehicle.camera.count" to "1": PROP_ERROR_PERMISSION_DENIED (0x18)
11-04 00:03:55.255   759  1114 E [ERROR][CLOG][VMAN]: [31m[pid:1497, dp_in_logic.cpp:184]: control.value = 0[0m
11-04 00:03:55.287 12619 12728 I TestRunner: finished: testVolumeDown(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
11-04 00:03:55.290 12619 12728 I TestRunner: run finished: 37 tests, 0 failed, 1 ignored
11-04 00:03:55.395 22497 12608 E adbd    : error reading output FD 56: Connection reset by peer
```
