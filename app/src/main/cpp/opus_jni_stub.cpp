#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "OpusJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("Opus JNI Test Version 1.0 - Ready for real Opus integration");
}

JNIEXPORT jlong JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_createEncoder(JNIEnv *env, jobject thiz,
                                                         jint sampleRateInHz, jint channelConfig,
                                                         jint complexity, jint bitrate) {
    LOGI("🚧 createEncoder called - stub implementation");
    return 0L; // 返回0表示未实现
}

JNIEXPORT jlong JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_createDecoder(JNIEnv *env, jobject thiz,
                                                         jint sampleRateInHz, jint channelConfig) {
    LOGI("🚧 createDecoder called - stub implementation");
    return 0L; // 返回0表示未实现
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_encode(JNIEnv *env, jobject thiz, jlong pOpusEnc,
                                                  jshortArray samples, jint frameSize,
                                                  jbyteArray bytes) {
    LOGI("🚧 encode called - stub implementation");
    return -1; // 返回负数表示失败
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_decode(JNIEnv *env, jobject thiz, jlong pOpusDec,
                                                  jbyteArray bytes, jint bytesLength,
                                                  jshortArray samples, jint frameSize) {
    LOGI("🚧 decode called - stub implementation");
    return -1; // 返回负数表示失败
}

JNIEXPORT void JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_destroyEncoder(JNIEnv *env, jobject thiz,
                                                          jlong pOpusEnc) {
    LOGI("🚧 destroyEncoder called - stub implementation");
}

JNIEXPORT void JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_destroyDecoder(JNIEnv *env, jobject thiz,
                                                          jlong pOpusDec) {
    LOGI("🚧 destroyDecoder called - stub implementation");
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getEncoderSize(JNIEnv *env, jobject thiz,
                                                          jint channels) {
    LOGI("🚧 getEncoderSize called - stub implementation");
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getDecoderSize(JNIEnv *env, jobject thiz,
                                                          jint channels) {
    LOGI("🚧 getDecoderSize called - stub implementation");
    return 0;
}

} // extern "C"
