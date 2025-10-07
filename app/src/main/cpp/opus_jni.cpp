#include <jni.h>
#include <android/log.h>
#include <opus.h>
#include <string>

#define LOG_TAG "OpusJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_createEncoder(JNIEnv *env, jobject thiz,
                                                         jint sampleRateInHz, jint channelConfig,
                                                         jint complexity, jint bitrate) {
    int error;
    OpusEncoder *pOpusEnc = opus_encoder_create(sampleRateInHz, channelConfig,
                                                OPUS_APPLICATION_VOIP, &error);
    if (pOpusEnc && error == OPUS_OK) {
        // 配置编码器参数
        opus_encoder_ctl(pOpusEnc, OPUS_SET_VBR(0)); // 0:CBR, 1:VBR
        opus_encoder_ctl(pOpusEnc, OPUS_SET_VBR_CONSTRAINT(1));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_BITRATE(bitrate));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_COMPLEXITY(complexity)); // 0~10
        opus_encoder_ctl(pOpusEnc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_LSB_DEPTH(16));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_DTX(0));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_INBAND_FEC(0));
        opus_encoder_ctl(pOpusEnc, OPUS_SET_PACKET_LOSS_PERC(0));
        
        LOGI("✅ Opus编码器创建成功: %dHz, %dch, 复杂度%d, 比特率%d", 
             sampleRateInHz, channelConfig, complexity, bitrate);
    } else {
        LOGE("❌ Opus编码器创建失败: error=%d", error);
    }
    return (jlong) pOpusEnc;
}

JNIEXPORT jlong JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_createDecoder(JNIEnv *env, jobject thiz,
                                                         jint sampleRateInHz, jint channelConfig) {
    int error;
    OpusDecoder *pOpusDec = opus_decoder_create(sampleRateInHz, channelConfig, &error);
    if (pOpusDec && error == OPUS_OK) {
        LOGI("✅ Opus解码器创建成功: %dHz, %dch", sampleRateInHz, channelConfig);
    } else {
        LOGE("❌ Opus解码器创建失败: error=%d", error);
    }
    return (jlong) pOpusDec;
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_encode(JNIEnv *env, jobject thiz, jlong pOpusEnc,
                                                  jshortArray samples, jint frameSize,
                                                  jbyteArray bytes) {
    OpusEncoder *pEnc = (OpusEncoder *) pOpusEnc;
    if (!pEnc || !samples || !bytes) {
        LOGE("❌ encode: 无效参数");
        return -1;
    }

    jshort *pSamples = env->GetShortArrayElements(samples, 0);
    jsize nSampleSize = env->GetArrayLength(samples);
    jbyte *pBytes = env->GetByteArrayElements(bytes, 0);
    jsize nByteSize = env->GetArrayLength(bytes);

    if (nSampleSize < frameSize || nByteSize <= 0) {
        LOGE("❌ encode: 数据大小不匹配 samples=%d, frameSize=%d, bytes=%d", 
             nSampleSize, frameSize, nByteSize);
        env->ReleaseShortArrayElements(samples, pSamples, 0);
        env->ReleaseByteArrayElements(bytes, pBytes, 0);
        return -1;
    }

    int nRet = opus_encode(pEnc, pSamples, frameSize, (unsigned char *) pBytes, nByteSize);
    
    if (nRet < 0) {
        LOGE("❌ opus_encode失败: %d", nRet);
    }

    env->ReleaseShortArrayElements(samples, pSamples, 0);
    env->ReleaseByteArrayElements(bytes, pBytes, 0);
    return nRet;
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_decode(JNIEnv *env, jobject thiz, jlong pOpusDec,
                                                  jbyteArray bytes, jint bytesLength,
                                                  jshortArray samples, jint frameSize) {
    OpusDecoder *pDec = (OpusDecoder *) pOpusDec;
    if (!pDec || !samples || !bytes) {
        LOGE("❌ decode: 无效参数");
        return -1;
    }

    jshort *pSamples = env->GetShortArrayElements(samples, 0);
    jbyte *pBytes = env->GetByteArrayElements(bytes, 0);
    jsize nShortSize = env->GetArrayLength(samples);

    if (bytesLength <= 0 || nShortSize < frameSize) {
        LOGE("❌ decode: 数据大小不匹配 bytesLength=%d, samples=%d, frameSize=%d", 
             bytesLength, nShortSize, frameSize);
        env->ReleaseShortArrayElements(samples, pSamples, 0);
        env->ReleaseByteArrayElements(bytes, pBytes, 0);
        return -1;
    }

    int nRet = opus_decode(pDec, (unsigned char *) pBytes, bytesLength, pSamples, frameSize, 0);
    
    if (nRet < 0) {
        LOGE("❌ opus_decode失败: %d", nRet);
    }

    env->ReleaseShortArrayElements(samples, pSamples, 0);
    env->ReleaseByteArrayElements(bytes, pBytes, 0);
    return nRet;
}

JNIEXPORT void JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_destroyEncoder(JNIEnv *env, jobject thiz,
                                                          jlong pOpusEnc) {
    OpusEncoder *pEnc = (OpusEncoder *) pOpusEnc;
    if (pEnc) {
        opus_encoder_destroy(pEnc);
        LOGI("🧹 Opus编码器已销毁");
    }
}

JNIEXPORT void JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_destroyDecoder(JNIEnv *env, jobject thiz,
                                                          jlong pOpusDec) {
    OpusDecoder *pDec = (OpusDecoder *) pOpusDec;
    if (pDec) {
        opus_decoder_destroy(pDec);
        LOGI("🧹 Opus解码器已销毁");
    }
}

JNIEXPORT jstring JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(opus_get_version_string());
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getEncoderSize(JNIEnv *env, jobject thiz,
                                                          jint channels) {
    return opus_encoder_get_size(channels);
}

JNIEXPORT jint JNICALL
Java_org_stypox_dicio_io_audio_OpusNative_getDecoderSize(JNIEnv *env, jobject thiz,
                                                          jint channels) {
    return opus_decoder_get_size(channels);
}

} // extern "C"
