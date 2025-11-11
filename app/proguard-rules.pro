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

# ===== Protobuf生成的类（DataStore相关）- 增强混淆保护 =====
# 🔒 允许混淆Protobuf生成的类名，但保留必要的序列化方法
# 只保留Protobuf运行时需要的方法，其他方法允许混淆

# 保护Protobuf核心运行时类（必须保留）
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessage { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# 🔒 允许混淆DataStore生成的类名，但保留必要的序列化方法
# 只保留Protobuf序列化/反序列化需要的方法
-keepclassmembers class com.ai.voice.settings.datastore.** {
    # 保留Protobuf序列化方法
    public <methods>;
    public static <methods>;
    # 保留Builder模式的方法
    public com.ai.voice.settings.datastore.**$Builder toBuilder();
    public static com.ai.voice.settings.datastore.**$Builder newBuilder();
    public static com.ai.voice.settings.datastore.** parseFrom(...);
    public static com.ai.voice.settings.datastore.** parseDelimitedFrom(...);
    # 保留字段访问器（Protobuf生成的getter/setter）
    public *** get*();
    public boolean has*();
    public com.ai.voice.settings.datastore.**$Builder set*(...);
    public com.ai.voice.settings.datastore.**$Builder clear*();
    # 保留序列化方法
    public void writeTo(com.google.protobuf.CodedOutputStream);
    public int getSerializedSize();
    public com.google.protobuf.ByteString toByteString();
    public byte[] toByteArray();
}

# 🔒 允许混淆Builder类名，但保留必要的构建方法
-keepclassmembers class com.ai.voice.settings.datastore.**$Builder {
    public com.ai.voice.settings.datastore.** build();
    public com.ai.voice.settings.datastore.** buildPartial();
    public com.ai.voice.settings.datastore.**$Builder clone();
    public com.ai.voice.settings.datastore.**$Builder mergeFrom(...);
    public com.ai.voice.settings.datastore.**$Builder clear();
    # 保留字段设置方法
    public com.ai.voice.settings.datastore.**$Builder set*(...);
    public com.ai.voice.settings.datastore.**$Builder clear*();
    public *** get*();
    public boolean has*();
}

# 🔒 允许混淆枚举类名，但保留枚举值
-keepclassmembers enum com.ai.voice.settings.datastore.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public int getNumber();
}

# 🔒 保护Protobuf字段名（以_结尾的字段，Protobuf内部使用）
# 这些字段名必须保留，否则序列化会失败
-keepclassmembers class com.ai.voice.settings.datastore.** {
    # 使用通配符匹配所有以_结尾的字段
    *** *_;
}

# 🔒 保护Protobuf内部使用的字段描述符
-keepclassmembers class com.ai.voice.settings.datastore.** {
    private static final com.google.protobuf.Descriptors$Descriptor *;
    private static final com.google.protobuf.Descriptors$FieldDescriptor *;
    private static final com.google.protobuf.Internal$FieldAccessorTable *;
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

# ===== Assets解密工具保护 =====
# 🔒 保护解密工具类，防止被反编译分析
# 允许混淆类名和方法名，但保留必要的功能
-keepclassmembers class com.ai.voice.util.AssetDecryptor {
    public static <methods>;
    private static <methods>;
}
-keepclassmembers class com.ai.voice.util.AssetHelper {
    public static <methods>;
}

# ===== 核心业务逻辑保护（选择性混淆，确保功能正常）=====
# 注意：这里只保护关键类，不全部keep，允许混淆以提高安全性
# 如果发现运行时问题，可以添加更多keep规则

# 🔒 允许混淆WakeService和关键服务的类名，但保留必要的方法
-keepclassmembers class com.ai.voice.io.wake.WakeService {
    public <methods>;
    protected <methods>;
}
-keepclassmembers class com.ai.voice.ui.floating.EnhancedFloatingWindowService {
    public <methods>;
    protected <methods>;
}
-keepclassmembers class com.ai.voice.io.wake.BootBroadcastReceiver {
    public <methods>;
}

# 🔒 允许混淆MainActivity类名，但保留必要的方法
-keepclassmembers class com.ai.voice.MainActivity {
    public <methods>;
    protected <methods>;
}

# 🔒 允许混淆接口名，但保留接口定义
-keep interface com.ai.voice.io.wake.WakeDevice {
    <methods>;
}
-keep interface com.ai.voice.di.WakeDeviceWrapper {
    <methods>;
}

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
