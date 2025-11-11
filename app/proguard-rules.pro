# ===== 基础混淆配置 =====
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# ===== 保留必要的属性 =====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ===== JNA =====
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

# ===== SherpaOnnx - 保持native方法 =====
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# ===== Hilt Dependency Injection =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * {
    <init>(...);
}
-keep @javax.inject.Inject class * { *; }
-keepclassmembers @javax.inject.Inject class * {
    <init>(...);
}

# ===== Kotlin Serialization =====
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer { *; }

# ===== Gson =====
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== Protobuf =====
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessage { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessage {
    <fields>;
}
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ===== Protobuf生成的类（DataStore相关）=====
# 保护所有Protobuf生成的类，防止字段被混淆
-keep class com.ai.voice.settings.datastore.** { *; }
-keepclassmembers class com.ai.voice.settings.datastore.** {
    <fields>;
    <methods>;
}

# 保护Protobuf生成的Builder类
-keep class com.ai.voice.settings.datastore.**$Builder { *; }
-keepclassmembers class com.ai.voice.settings.datastore.**$Builder {
    <fields>;
    <methods>;
}

# 保护Protobuf生成的枚举类
-keep enum com.ai.voice.settings.datastore.** { *; }

# Protobuf字段名保护（字段名以_结尾）
-keepclassmembers class com.ai.voice.settings.datastore.** {
    *** theme_;
    *** language_;
    *** input_device_;
    *** speech_output_device_;
    *** wake_device_;
    *** stt_play_sound_;
    *** two_pass_settings_;
    *** audio_quality_;
    *** tts_fallback_chain_;
    *** asr_fallback_chain_;
    *** dynamic_colors_;
    *** auto_finish_stt_popup_;
    *** performance_monitor_enabled_;
    *** pause_wake_during_asr_;
    *** enabled_skills_;
}

# ===== Hyundai IT API =====
-keep class com.ifpdos.sdklib.hyundaiit.** { *; }

# ===== ONNX Runtime =====
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { *; }

# ===== LiteRT =====
-keep class ai.litert.** { *; }

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ===== DataBinding =====
-keep class androidx.databinding.** { *; }

# ===== Compose =====
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ===== Lottie =====
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== Vosk =====
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# ===== 保留native方法 =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== 保留Parcelable实现 =====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===== 保留Serializable类 =====
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===== 保留R类 =====
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ===== 保留View构造函数 =====
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== 保留Application类 =====
-keep class * extends android.app.Application {
    <init>();
    <methods>;
}

# ===== 保留Activity、Service、BroadcastReceiver =====
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ===== 保留自定义View =====
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    *** get*();
}

# ===== 保留枚举 =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== 保留Kotlin协程 =====
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# ===== 保留Kotlin反射 =====
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# ===== 保留DataStore =====
-keep class androidx.datastore.** { *; }

# ===== 保留Exp4j =====
-keep class net.objecthunter.exp4j.** { *; }

# ===== 保留Jsoup =====
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ===== 保留Unbescape =====
-keep class org.unbescape.** { *; }

# ===== 保留Coil =====
-keep class coil.** { *; }
-dontwarn coil.**

# ===== 保留Permission Flow =====
-keep class dev.shreyaspatil.permissionflow.** { *; }

# ===== 核心业务逻辑保护（选择性混淆，确保功能正常）=====
# 注意：这里只保护关键类，不全部keep，允许混淆以提高安全性
# 如果发现运行时问题，可以添加更多keep规则

# 保留WakeService和关键服务
-keep class com.ai.voice.io.wake.WakeService { *; }
-keep class com.ai.voice.ui.floating.EnhancedFloatingWindowService { *; }
-keep class com.ai.voice.io.wake.BootBroadcastReceiver { *; }

# 保留MainActivity
-keep class com.ai.voice.MainActivity { *; }

# 保留关键接口和抽象类
-keep interface com.ai.voice.io.wake.WakeDevice { *; }
-keep interface com.ai.voice.di.WakeDeviceWrapper { *; }

# ===== 移除调试日志（Release版本）=====
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 移除自定义调试日志（Release版本）
-assumenosideeffects class com.ai.voice.util.DebugLogger {
    public static void logWakeWord(...);
    public static void logWakeWordError(...);
    public static void logVoiceRecognition(...);
    public static void logVoiceRecognitionError(...);
    public static void logAudioProcessing(...);
    public static void logModelManagement(...);
    public static void logStateMachine(...);
    public static void logUI(...);
    public static void logIfDebug(...);
    public static void logWarnIfDebug(...);
    # 注意：以下关键日志方法不在这里移除，Release版本也需要输出
    # - logAsrResult (ASR识别结果)
    # - logWakeWordSuccess (唤醒词检测成功)
    # - logCommandExecuted (命令执行成功)
    # - logError (错误日志)
}

# ===== 警告抑制 =====
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.annotations.**

# ===== R8缺失类警告抑制 =====
# Hyundai IT API相关（可选依赖）
-dontwarn com.ifpdos.mcusdk.McuSDK
-dontwarn com.ifpdos.mcusdk.interfaces.OnSdkConnected

# JNA AWT相关（Android不支持AWT，但JNA库中有引用）
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
