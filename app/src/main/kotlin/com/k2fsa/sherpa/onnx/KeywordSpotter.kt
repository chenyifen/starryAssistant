package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

class KeywordSpotter(
    assetManager: AssetManager? = null,
    config: KeywordSpotterConfig
) {
    companion object {
        init {
            try {
                System.loadLibrary("sherpa-onnx-jni")
            } catch (e: UnsatisfiedLinkError) {
                // Handle library loading error
                e.printStackTrace()
            }
        }
    }

    private var ptr: Long = 0

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
    }

    fun createStream(keywords: String = ""): OnlineStream? {
        return if (ptr != 0L) {
            OnlineStream(createStream(ptr, keywords))
        } else {
            null
        }
    }

    fun isReady(stream: OnlineStream): Boolean {
        return if (ptr != 0L && stream.ptr != 0L) {
            isReady(ptr, stream.ptr)
        } else {
            false
        }
    }

    fun decode(stream: OnlineStream) {
        if (ptr != 0L && stream.ptr != 0L) {
            decode(ptr, stream.ptr)
        }
    }

    fun getResult(stream: OnlineStream): KeywordSpotterResult? {
        return if (ptr != 0L && stream.ptr != 0L) {
            val result = getResult(ptr, stream.ptr)
            // JNI实际返回Object[]，需要正确解析
            if (result is Array<*> && result.isNotEmpty()) {
                val keyword = result[0] as? String ?: ""
                val tokens = if (result.size > 1) result[1] as? Array<String> ?: emptyArray() else emptyArray()
                val timestamps = if (result.size > 2) result[2] as? FloatArray ?: floatArrayOf() else floatArrayOf()
                KeywordSpotterResult(keyword, tokens, timestamps)
            } else {
                // 如果返回的是String（旧版本兼容）
                val keyword = result as? String ?: ""
                KeywordSpotterResult(keyword)
            }
        } else {
            null
        }
    }

    fun reset(stream: OnlineStream) {
        if (ptr != 0L && stream.ptr != 0L) {
            reset(ptr, stream.ptr)
        }
    }

    fun release() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        release()
    }

    // Native methods
    private external fun newFromAsset(
        assetManager: AssetManager,
        config: KeywordSpotterConfig
    ): Long

    private external fun newFromFile(config: KeywordSpotterConfig): Long
    private external fun delete(ptr: Long)
    private external fun createStream(ptr: Long, keywords: String): Long
    private external fun isReady(ptr: Long, streamPtr: Long): Boolean
    private external fun decode(ptr: Long, streamPtr: Long)
    private external fun getResult(ptr: Long, streamPtr: Long): Any
    private external fun reset(ptr: Long, streamPtr: Long)
}

data class KeywordSpotterConfig(
    var featConfig: FeatureConfig = FeatureConfig(),
    var modelConfig: OnlineModelConfig = OnlineModelConfig(),
    var maxActivePaths: Int = 4,
    var keywordsFile: String = "",
    var keywordsScore: Float = 1.5f,
    var keywordsThreshold: Float = 0.25f,
    var numTrailingBlanks: Int = 2,
)

data class KeywordSpotterResult(
    val keyword: String,
    val tokens: Array<String> = emptyArray(),
    val timestamps: FloatArray = floatArrayOf(),
)
