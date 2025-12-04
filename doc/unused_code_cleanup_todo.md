# 未使用代码清理待办

## 1. 空目录 (建议删除)
- [ ] `app/src/main/kotlin/com/ai/voice/io/input/websocket/` - 空目录
- [ ] `app/src/main/kotlin/com/ai/voice/io/sherpa/` - 空目录  
- [ ] `app/src/main/kotlin/com/ai/voice/audio/enhanced/` - 空目录

## 2. 备份文件/目录 (建议删除或移出版本控制)
- [ ] `app/src/main/jniLibs.backup/` - 原生库备份，含libvosk.so等
- [ ] `app/src/main/assets_backup/` - TTS模型备份
- [ ] `models_backup/` - 模型备份目录

## 3. 未使用的工具类
- [ ] `app/src/main/kotlin/com/ai/voice/util/ConnectionUtils.kt` - 网络工具，未被import
- [ ] `app/src/main/kotlin/com/ai/voice/util/CoroutineExt.kt` - 协程扩展，未被import
- [ ] `app/src/main/kotlin/com/ai/voice/util/SkillContextExt.kt` - SkillContext扩展，未被import
- [ ] `app/src/main/kotlin/com/ai/voice/util/AsrTextNormalizer.kt` - ASR文本规范化，未被import
- [ ] `app/src/main/kotlin/com/ai/voice/io/audio/AdaptiveAudioProcessor.kt` - 自适应音频处理器，未被import

## 4. 未使用的协议代码
- [ ] `app/src/main/kotlin/com/ai/voice/io/net/Protocol.kt` - Protocol接口无任何实现

## 5. 未使用的路由定义
- [ ] `app/src/main/kotlin/com/ai/voice/ui/nav/Routes.kt` - Home/MainSettings/SkillSettings对象未被引用

## 6. 未注册的广播接收器
- [ ] `app/src/main/kotlin/com/ai/voice/io/wake/WakeWordTriggerBroadcastReceiver.kt` - 未在AndroidManifest中注册

## 7. 测试数据目录
- [ ] `test_data/` - 包含测试用json/txt/kt文件，可能不再需要

## 8. 其他可能废弃的资源
- [ ] `app/src/main/assets/korean_hinudge_onnx/*.backup_*` - 模型备份文件

---
注：删除前请确认代码确实未使用，部分代码可能通过反射或动态加载方式使用

