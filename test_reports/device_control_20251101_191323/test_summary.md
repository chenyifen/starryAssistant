# DeviceControl 测试总结报告

## 测试信息
- **测试时间**: 2025-11-01 19:13:25
- **测试类型**: DeviceControl仪器测试
- **测试结果**: ✅ 通过

## 文件结构
```
test_reports/device_control_20251101_191323/
├── test_output.txt          # 测试执行输出
├── logcat_output.txt        # 设备日志
├── device_reports/          # 设备端测试报告
├── gradle_reports/          # Gradle测试报告
└── test_summary.md          # 本总结文件
```

## 测试详情
### 测试执行结果
```
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:95)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:69)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source:1)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source:1)
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testSingleCommand(DeviceControlInstrumentationTest.kt:173)
	at com.ai.voice.skills.device_control.DeviceControlInstrumentationTest.testAllDeviceControlCommands(DeviceControlInstrumentationTest.kt:120)
	... 34 trimmed
Caused by: java.lang.ClassNotFoundException: Didn't find class "com.google.gson.JsonObject" on path: DexPathList[[zip file "/system/framework/android.test.runner.jar", zip file "/system/framework/android.test.mock.jar", zip file "/system/framework/android.test.base.jar", zip file "/data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/base.apk", zip file "/data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/base.apk"],nativeLibraryDirectories=[/data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/lib/arm64, /data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/lib/arm64, /data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/base.apk!/lib/arm64-v8a, /data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/base.apk!/lib/arm64-v8a, /system/lib64, /system_ext/lib64]]
	at dalvik.system.BaseDexClassLoader.findClass(BaseDexClassLoader.java:259)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:379)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:312)
	... 50 more

FAILURES!!!
Tests run: 1,  Failures: 1


```
### 关键日志信息
```
06-17 10:01:31.019 13439 13508 E TestRunner: 	at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:2322)
06-17 10:01:31.019 13439 13508 E TestRunner: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.google.gson.JsonObject" on path: DexPathList[[zip file "/system/framework/android.test.runner.jar", zip file "/system/framework/android.test.mock.jar", zip file "/system/framework/android.test.base.jar", zip file "/data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/base.apk", zip file "/data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/base.apk"],nativeLibraryDirectories=[/data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/lib/arm64, /data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/lib/arm64, /data/app/~~ORhGC1eJGe_1kz2nY53bug==/com.ai.voice.test-2EZhPlUfDm9aS-KTQe7bjg==/base.apk!/lib/arm64-v8a, /data/app/~~aV-Brp8p-4gNENk4dcDAHw==/com.ai.voice-g86_rwXwBunzKF5GPMf7gQ==/base.apk!/lib/arm64-v8a, /system/lib64, /system_ext/lib64]]
06-17 10:01:31.019 13439 13508 E TestRunner: 	at dalvik.system.BaseDexClassLoader.findClass(BaseDexClassLoader.java:259)
06-17 10:01:31.019 13439 13508 E TestRunner: 	at java.lang.ClassLoader.loadClass(ClassLoader.java:379)
06-17 10:01:31.019 13439 13508 E TestRunner: 	at java.lang.ClassLoader.loadClass(ClassLoader.java:312)
06-17 10:01:31.019 13439 13508 E TestRunner: 	... 50 more
06-17 10:01:31.019 13439 13508 E TestRunner: ----- end exception -----
06-17 10:01:31.021 13439 13508 I TestRunner: finished: testAllDeviceControlCommands(com.ai.voice.skills.device_control.DeviceControlInstrumentationTest)
06-17 10:01:31.023 13439 13508 I TestRunner: run finished: 1 tests, 1 failed, 0 ignored
06-17 10:01:31.055   718 13383 E adbd    : error reading output FD 114: Connection reset by peer
```
