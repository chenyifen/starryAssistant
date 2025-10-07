LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# NDK版本配置
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_CPPFLAGS += -std=c++11
APP_STL := c++_shared
APP_PLATFORM := android-21

# 包含源文件列表
include $(LOCAL_PATH)/opus-1.3.1/celt_sources.mk
include $(LOCAL_PATH)/opus-1.3.1/silk_sources.mk  
include $(LOCAL_PATH)/opus-1.3.1/opus_sources.mk

LOCAL_MODULE := opus

# Fixed point sources for better performance on mobile
SILK_SOURCES += $(SILK_SOURCES_FIXED)

# ARM optimizations
CELT_SOURCES += $(CELT_SOURCES_ARM)
SILK_SOURCES += $(SILK_SOURCES_ARM)

LOCAL_SRC_FILES := \
    $(CELT_SOURCES) $(SILK_SOURCES) $(OPUS_SOURCES) $(OPUS_SOURCES_FLOAT)

LOCAL_LDLIBS := -lm -llog

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/opus-1.3.1/include \
    $(LOCAL_PATH)/opus-1.3.1/silk \
    $(LOCAL_PATH)/opus-1.3.1/silk/fixed \
    $(LOCAL_PATH)/opus-1.3.1/celt

LOCAL_CFLAGS := -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1 -D_FILE_OFFSET_BITS=64
LOCAL_CFLAGS += -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -O3 -fno-math-errno

LOCAL_CPPFLAGS := -DBSD=1 
LOCAL_CPPFLAGS += -ffast-math -O3 -funroll-loops

include $(BUILD_SHARED_LIBRARY)

# JNI wrapper library
include $(CLEAR_VARS)

LOCAL_MODULE := opus_jni
LOCAL_SRC_FILES := opus_jni.cpp
LOCAL_SHARED_LIBRARIES := opus
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/opus-1.3.1/include

include $(BUILD_SHARED_LIBRARY)
