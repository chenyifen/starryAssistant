package org.stypox.dicio.io.wake.oww

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.stypox.dicio.io.wake.WakeDevice
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.AssetModelManager
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.AudioDebugSaver
import org.stypox.dicio.util.FileToDownload
import org.stypox.dicio.util.downloadBinaryFilesWithPartial
import org.stypox.dicio.util.measureTimeAndLog
import java.io.File
import java.io.IOException

class OpenWakeWordDevice(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : WakeDevice {
    private val _state: MutableStateFlow<WakeState>
    override val state: StateFlow<WakeState>

    private val cacheDir: File = appContext.cacheDir
    private val owwFolder = File(appContext.filesDir, "openWakeWord")
    private val melFile = FileToDownload(MEL_URL, File(owwFolder, "melspectrogram.tflite"))
    private val embFile = FileToDownload(EMB_URL, File(owwFolder, "embedding.tflite"))
    private val wakeFile = FileToDownload(WAKE_URL, File(owwFolder, "wake.tflite"))
    private val userWakeFile = userWakeFile(appContext)
    private val userWakeFileExists = userWakeFile.exists()
    private val allModelFiles =
        // wakeFile is not needed if we want to use the userWakeFile instead
        if (userWakeFileExists) listOf(melFile, embFile)
        else listOf(melFile, embFile, wakeFile)

    private val audio = FloatArray(OwwModel.MEL_INPUT_COUNT)
    private var model: OwwModel? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        DebugLogger.logWakeWord(TAG, "🚀 Initializing OpenWakeWordDevice")
        DebugLogger.logWakeWord(TAG, "📁 OWW folder: ${owwFolder.absolutePath}")
        DebugLogger.logWakeWord(TAG, "📄 Model files: ${allModelFiles.map { "${it.file.name} (${if (it.file.exists()) "EXISTS" else "MISSING"})" }}")
        DebugLogger.logWakeWord(TAG, "👤 User wake file exists: $userWakeFileExists")
        
        val modelsAvailable = hasModelsAvailable()
        DebugLogger.logWakeWord(TAG, "✅ Models available: $modelsAvailable")
        
        _state = if (modelsAvailable) {
            MutableStateFlow(WakeState.NotLoaded)
        } else {
            MutableStateFlow(WakeState.NotDownloaded)
        }
        state = _state
        
        DebugLogger.logStateMachine(TAG, "Initial state: ${_state.value}")
        
        // 如果assets中有模型但本地没有，自动复制
        scope.launch {
            val hasLocal = hasLocalModels()
            val hasAssets = AssetModelManager.hasOpenWakeWordModelsInAssets(appContext)
            
            DebugLogger.logModelManagement(TAG, "Local models: $hasLocal, Assets models: $hasAssets")
            
            if (!hasLocal && hasAssets) {
                DebugLogger.logModelManagement(TAG, "🔄 Auto-copying OpenWakeWord models from assets on init")
                val copySuccess = measureTimeAndLog(TAG, "Copy OWW models from assets") {
                    AssetModelManager.copyOpenWakeWordModels(appContext)
                }
                
                if (copySuccess) {
                    DebugLogger.logModelManagement(TAG, "✅ Successfully copied models from assets")
                    _state.value = WakeState.NotLoaded
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from assets")
                }
            }
        }
    }
    
    private fun hasModelsAvailable(): Boolean {
        return hasLocalModels() || AssetModelManager.hasOpenWakeWordModelsInAssets(appContext)
    }
    
    private fun hasLocalModels(): Boolean {
        return !allModelFiles.any(FileToDownload::needsToBeDownloaded)
    }

    override fun download() {
        _state.value = WakeState.Downloading(Progress.UNKNOWN)

        scope.launch {
            try {
                // 首先尝试从assets复制预打包的模型
                if (AssetModelManager.hasOpenWakeWordModelsInAssets(appContext)) {
                    Log.d(TAG, "Copying OpenWakeWord models from assets")
                    val copySuccess = AssetModelManager.copyOpenWakeWordModels(appContext)
                    if (copySuccess) {
                        // 模型文件已就绪，立即加载到内存
                        loadModel()
                        return@launch
                    }
                    Log.w(TAG, "Failed to copy from assets, falling back to download")
                }
                
                // 如果assets中没有模型或复制失败，则从网络下载
                owwFolder.mkdirs()
                downloadBinaryFilesWithPartial(
                    urlsFiles = allModelFiles,
                    httpClient = okHttpClient,
                    cacheDir = cacheDir,
                ) { progress ->
                    _state.value = WakeState.Downloading(progress)
                }
                
                // 下载完成后，加载模型
                loadModel()
            } catch (e: Throwable) {
                Log.e(TAG, "Can't download OpenWakeWord model", e)
                _state.value = WakeState.ErrorDownloading(e)
                return@launch
            }
        }
    }
    
    /**
     * 加载模型到内存
     */
    private fun loadModel() {
        try {
            DebugLogger.logWakeWord(TAG, "🔄 Loading OWW model...")
            _state.value = WakeState.Loading
            
            val modelFiles = if (userWakeFileExists) {
                DebugLogger.logWakeWord(TAG, "👤 Using user wake file: ${userWakeFile.absolutePath}")
                listOf(melFile.file, embFile.file, userWakeFile)
            } else {
                DebugLogger.logWakeWord(TAG, "🔊 Using default wake file: ${wakeFile.file.absolutePath}")
                listOf(melFile.file, embFile.file, wakeFile.file)
            }
            
            DebugLogger.logWakeWord(TAG, "📄 Model files: ${modelFiles.map { "${it.name} (${if (it.exists()) "✅" else "❌"})" }}")
            
            // 检查所有文件是否存在
            val missingFiles = modelFiles.filter { !it.exists() }
            if (missingFiles.isNotEmpty()) {
                val error = IOException("Missing model files: ${missingFiles.joinToString { it.name }}")
                DebugLogger.logWakeWordError(TAG, "❌ Cannot load model, missing files", error)
                _state.value = WakeState.ErrorLoading(error)
                return
            }
            
            model = measureTimeAndLog(TAG, "Load OWW model") {
                OwwModel(
                    melFile.file,
                    embFile.file,
                    if (userWakeFileExists) userWakeFile else wakeFile.file,
                )
            }
            
            _state.value = WakeState.Loaded
            DebugLogger.logWakeWord(TAG, "✅ OWW model loaded successfully")
        } catch (t: Throwable) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to load OWW model", t)
            _state.value = WakeState.ErrorLoading(t)
        }
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        if (audio16bitPcm.size != OwwModel.MEL_INPUT_COUNT) {
            DebugLogger.logWakeWordError(TAG, "❌ Invalid frame size: ${audio16bitPcm.size}, expected: ${OwwModel.MEL_INPUT_COUNT}")
            throw IllegalArgumentException(
                "OwwModel can only process audio frames of ${OwwModel.MEL_INPUT_COUNT} samples"
            )
        }

        // 模型应该已经在download()时加载
        if (model == null) {
            DebugLogger.logWakeWordError(TAG, "❌ Model not loaded, current state: ${_state.value}")
            throw IOException("Model has not been loaded yet")
        }

        // 转换音频数据
        for (i in 0..<OwwModel.MEL_INPUT_COUNT) {
            audio[i] = audio16bitPcm[i].toFloat() / 32768.0f
        }

        // 处理音频帧并获取置信度
        val confidence = measureTimeAndLog(TAG, "Process audio frame") {
            model!!.processFrame(audio)
        }
        
        val threshold = 0.01f
        val detected = confidence > threshold
        
        return detected
    }

    override fun frameSize(): Int {
        return OwwModel.MEL_INPUT_COUNT
    }

    override fun destroy() {
        model?.close()
        model = null
        scope.cancel()
    }

    override fun isHeyDicio(): Boolean = !userWakeFileExists
    

    companion object {
        val TAG = OpenWakeWordDevice::class.simpleName ?: "OpenWakeWordDevice"
        const val MEL_URL = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.tflite"
        const val EMB_URL = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.tflite"
        const val WAKE_URL = "https://github.com/Stypox/dicio-android/releases/download/v2.0/hey_dicio_v6.0.tflite"

        private fun userWakeFile(context: Context) =
            File(context.filesDir, "openWakeWord/userwake.tflite")

        suspend fun addUserWakeFile(context: Context, source: Uri) {
            // Use a partial file to ensure atomicity
            val userWakeFile = userWakeFile(context)
            withContext(Dispatchers.IO) {
                val partialFile = File.createTempFile(userWakeFile.name, ".part", context.cacheDir)
                val inputStream = context.contentResolver.openInputStream(source)
                if (inputStream != null) {
                    inputStream.use { source ->
                        partialFile.outputStream().use {
                            source.copyTo(it)
                        }
                    }

                    // Remove the previous file if it already exists
                    userWakeFile.delete()
                    userWakeFile.parentFile?.mkdirs()
                    val renameOk = partialFile.renameTo(userWakeFile)
                    if (!renameOk) {
                        throw IOException("Cannot rename partial file $partialFile to actual file $userWakeFile")
                    }
                }
            }
        }

        suspend fun removeUserWakeFile(context: Context) {
            withContext(Dispatchers.IO) {
                userWakeFile(context).delete()
            }
        }
    }
}

