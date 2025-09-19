/*
 * Taken from /e/OS Assistant
 *
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.stypox.dicio.io.input.vosk

import android.content.Context
import android.util.Log
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.input.vosk.VoskState.Downloaded
import org.stypox.dicio.io.input.vosk.VoskState.Downloading
import org.stypox.dicio.io.input.vosk.VoskState.ErrorDownloading
import org.stypox.dicio.io.input.vosk.VoskState.ErrorLoading
import org.stypox.dicio.io.input.vosk.VoskState.ErrorUnzipping
import org.stypox.dicio.io.input.vosk.VoskState.Listening
import org.stypox.dicio.io.input.vosk.VoskState.Loaded
import org.stypox.dicio.io.input.vosk.VoskState.Loading
import org.stypox.dicio.io.input.vosk.VoskState.NotAvailable
import org.stypox.dicio.io.input.vosk.VoskState.NotDownloaded
import org.stypox.dicio.io.input.vosk.VoskState.NotInitialized
import org.stypox.dicio.io.input.vosk.VoskState.NotLoaded
import org.stypox.dicio.io.input.vosk.VoskState.Unzipping
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.AssetModelManager
import org.stypox.dicio.util.FileToDownload
import org.stypox.dicio.util.LocaleUtils
import org.stypox.dicio.util.distinctUntilChangedBlockingFirst
import org.stypox.dicio.util.downloadBinaryFilesWithPartial
import org.stypox.dicio.util.extractZip
import org.stypox.dicio.BuildConfig as AppBuildConfig
import org.vosk.BuildConfig
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.Locale

class VoskInputDevice(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val localeManager: LocaleManager,
) : SttInputDevice {

    private val _state: MutableStateFlow<VoskState>
    private val _uiState: MutableStateFlow<SttState>
    override val uiState: StateFlow<SttState>

    private var operationsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val filesDir: File = appContext.filesDir
    private val cacheDir: File = appContext.cacheDir
    private val modelZipFile: File get() = File(filesDir, "vosk-model.zip")
    private val sameModelUrlCheck: File get() = File(filesDir, "vosk-model-url")
    private val modelDirectory: File get() = File(filesDir, "vosk-model")
    private val modelExistFileCheck: File get() = File(modelDirectory, "ivector")


    init {
        // Run blocking, because the locale is always available right away since LocaleManager also
        // initializes in a blocking way. Moreover, if VoskInputDevice were not initialized straight
        // away, the tryLoad() call when MainActivity starts may do nothing.
        val (firstLocale, nextLocaleFlow) = localeManager.locale
            .distinctUntilChangedBlockingFirst()

        val initialState = init(firstLocale)
        _state = MutableStateFlow(initialState)
        _uiState = MutableStateFlow(initialState.toUiState())
        uiState = _uiState

        scope.launch {
            _state.collect { _uiState.value = it.toUiState() }
        }

        scope.launch {
            // perform initialization again every time the locale changes
            nextLocaleFlow.collect { reinit(it) }
        }
        
        // 自动检查和复制模型（优先级：外部存储 > assets）
        scope.launch {
            val localeString = resolveVoskLanguageCode(firstLocale)
            
            if (localeString != null) {
                // 检查当前模型语言是否匹配
                val needsModelUpdate = !modelExistFileCheck.exists() || run {
                    val languageMarkerFile = File(filesDir, "vosk-model-language")
                    if (languageMarkerFile.exists()) {
                        try {
                            val currentLanguage = languageMarkerFile.readText().trim()
                            currentLanguage != localeString
                        } catch (e: Exception) {
                            true // 如果读取失败，认为需要更新
                        }
                    } else {
                        true // 如果标记文件不存在，认为需要更新
                    }
                }
                
                if (needsModelUpdate) {
                    Log.d(TAG, "Model update needed for language: $localeString")
                    
                    var modelCopied = false
                    
                    // noModels变体优先检查外部存储
                    if (!AppBuildConfig.HAS_MODELS_IN_ASSETS && AssetModelManager.hasVoskModelInExternalStorage(appContext, localeString)) {
                        Log.d(TAG, "Auto-copying Vosk model from external storage for language: $localeString")
                        val copySuccess = AssetModelManager.copyVoskModelFromExternalStorage(appContext, localeString)
                        if (copySuccess) {
                            Log.d(TAG, "✅ Successfully copied Vosk model from external storage")
                            modelCopied = true
                        } else {
                            Log.w(TAG, "⚠️ Failed to copy Vosk model from external storage")
                        }
                    }
                    
                    // 如果外部存储没有或复制失败，尝试从assets复制（withModels变体或fallback）
                    if (!modelCopied && AssetModelManager.hasVoskModelInAssets(appContext, localeString)) {
                        Log.d(TAG, "Auto-copying Vosk model from assets for language: $localeString")
                        val copySuccess = AssetModelManager.copyVoskModel(appContext, localeString)
                        if (copySuccess) {
                            Log.d(TAG, "✅ Successfully copied Vosk model from assets")
                            modelCopied = true
                        } else {
                            Log.w(TAG, "⚠️ Failed to copy Vosk model from assets")
                        }
                    }
                    
                    // 如果模型复制成功，重置状态以触发重新加载
                    if (modelCopied) {
                        _state.value = NotLoaded
                        return@launch
                    } else {
                        // 没有可用的模型，确保状态是NotLoaded，这样后续会进入下载流程
                        Log.d(TAG, "❌ No Vosk model available for $localeString, will proceed with download flow")
                        _state.value = NotLoaded
                        // 不要return，让后续正常的初始化流程处理下载
                    }
                } else {
                    Log.d(TAG, "Model for language $localeString already exists and is up to date")
                }
            }
        }
    }

    private fun init(locale: Locale): VoskState {
        Log.d(TAG, "🎤 Vosk初始化 - 语言切换调试:")
        Log.d(TAG, "  📥 输入Locale: $locale (language=${locale.language}, country=${locale.country})")
        
        // choose the model url based on the locale
        val localeString = resolveVoskLanguageCode(locale)
        Log.d(TAG, "  🔄 解析后的Vosk语言代码: $localeString")
        
        val modelUrl = if (localeString != null) MODEL_URLS[localeString] else null
        Log.d(TAG, "  🌐 对应的模型URL: $modelUrl")
        Log.d(TAG, "  📚 所有支持的Vosk语言: ${MODEL_URLS.keys}")

        // the model url may change if the user changes app language, or in case of model updates
        val modelUrlChanged = try {
            val savedUrl = sameModelUrlCheck.readText()
            Log.d(TAG, "  💾 已保存的模型URL: $savedUrl")
            savedUrl != modelUrl
        } catch (e: IOException) {
            // modelUrlCheck file does not exist
            Log.d(TAG, "  ⚠️ 模型URL检查文件不存在，视为URL已更改")
            true
        }
        
        Log.d(TAG, "  🔄 模型URL是否更改: $modelUrlChanged")

        return when {
            // if the modelUrl is null, then the current locale is not supported by any Vosk model
            modelUrl == null -> NotAvailable
            // if the model url changed, the model needs to be re-downloaded
            modelUrlChanged -> {
                val localeString = resolveVoskLanguageCode(locale)
                
                when {
                    // noModels变体优先检查外部存储
                    localeString != null && !AppBuildConfig.HAS_MODELS_IN_ASSETS && 
                    AssetModelManager.hasVoskModelInExternalStorage(appContext, localeString) -> {
                        if (modelExistFileCheck.exists()) {
                            NotLoaded // 模型已复制，可以直接加载
                        } else {
                            Downloaded // 需要从外部存储复制（通过unzip流程触发）
                        }
                    }
                    // 检查assets中是否有对应语言的模型
                    localeString != null && AssetModelManager.hasVoskModelInAssets(appContext, localeString) -> {
                        if (modelExistFileCheck.exists()) {
                            NotLoaded // 模型已复制，可以直接加载
                        } else {
                            Downloaded // 需要从assets复制（通过unzip流程触发）
                        }
                    }
                    else -> NotDownloaded(modelUrl)
                }
            }
            // if the model zip file exists, it means that the app was interrupted after the
            // download finished (because the file is downloaded in the cache, and is moved to its
            // actual position only after it finishes downloading), but before the unzip process
            // completed (because after that the zip is deleted), so the next step is to unzip
            modelZipFile.exists() -> Downloaded
            // if the model zip file does not exist, but the model directory exists, then the model
            // has been completely downloaded and unzipped, and should be ready to be loaded
            modelExistFileCheck.isDirectory -> NotLoaded
            // if the both the model zip file and the model directory do not exist, then the model
            // has not been downloaded yet
            else -> {
                val localeString = resolveVoskLanguageCode(locale)
                
                when {
                    // noModels变体优先检查外部存储
                    localeString != null && !AppBuildConfig.HAS_MODELS_IN_ASSETS && 
                    AssetModelManager.hasVoskModelInExternalStorage(appContext, localeString) -> {
                        Downloaded // 外部存储有模型，标记为已下载状态，等待复制
                    }
                    // 检查assets中是否有对应语言的模型
                    localeString != null && AssetModelManager.hasVoskModelInAssets(appContext, localeString) -> {
                        Downloaded // assets中有模型，标记为已下载状态，等待复制
                    }
                    else -> NotDownloaded(modelUrl)
                }
            }
        }
    }

    private suspend fun reinit(locale: Locale) {
        Log.d(TAG, "🔄 Vosk重新初始化:")
        Log.d(TAG, "  📥 新语言: $locale")
        
        // interrupt whatever was happening before
        deinit()

        // reinitialize and emit the new state
        val initialState = init(locale)
        Log.d(TAG, "  📤 新状态: $initialState")
        _state.emit(initialState)
    }

    private suspend fun deinit() {
        val prevState = _state.getAndUpdate { NotInitialized }
        when (prevState) {
            // either interrupt the current
            is Downloading -> {
                operationsJob?.cancel()
                operationsJob?.join()
            }
            is Unzipping -> {
                operationsJob?.cancel()
                operationsJob?.join()
            }
            is Loading -> {
                operationsJob?.join()
                when (val s = _state.getAndUpdate { NotInitialized }) {
                    NotInitialized -> {} // everything is ok
                    is Loaded -> {
                        s.speechService.stop()
                        s.speechService.shutdown()
                    }
                    is Listening -> {
                        stopListening(s.speechService, s.eventListener, true)
                        s.speechService.shutdown()
                    }
                    else -> {
                        Log.w(TAG, "Unexpected state after loading: $s")
                    }
                }
            }
            is Loaded -> {
                prevState.speechService.stop()
                prevState.speechService.shutdown()
            }
            is Listening -> {
                stopListening(prevState.speechService, prevState.eventListener, true)
                prevState.speechService.shutdown()
            }

            // these states are all resting states, so there is nothing to interrupt
            is NotInitialized,
            is NotAvailable,
            is NotDownloaded,
            is ErrorDownloading,
            is Downloaded,
            is ErrorUnzipping,
            is NotLoaded,
            is ErrorLoading -> {}
        }
    }

    /**
     * Loads the model with [thenStartListeningEventListener] if the model is already downloaded
     * but not loaded in RAM (which will then start listening if [thenStartListeningEventListener]
     * is not `null` and pass events there), or starts listening if the model is already ready
     * and [thenStartListeningEventListener] is not `null` and passes events there.
     *
     * @param thenStartListeningEventListener if not `null`, causes the [VoskInputDevice] to start
     * listening after it has finished loading, and the received input events are sent there
     * @return `true` if the input device will start listening (or be ready to do so in case
     * `thenStartListeningEventListener == null`) at some point,
     * `false` if manual user intervention is required to start listening
     */
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        val s = _state.value
        if (s == NotLoaded || s is ErrorLoading) {
            load(thenStartListeningEventListener)
            return true
        } else if (thenStartListeningEventListener != null && s is Loaded) {
            startListening(s.speechService, thenStartListeningEventListener)
            return true
        } else {
            return false
        }
    }

    /**
     * If the model is not being downloaded/unzipped/loaded, or if there was an error in any of
     * those steps, downloads/unzips/loads the model. If the model is already loaded (or is being
     * loaded) toggles listening state.
     *
     * @param eventListener only used if this click causes Vosk to start listening, will receive all
     * updates for this run
     */
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        // the state can only be changed in the background by the jobs corresponding to Downloading,
        // Unzipping and Loading, but as can be seen below we don't do anything in case of
        // Downloading and Unzipping. For Loading however, special measures are taken in
        // toggleThenStartListening() and in load() to ensure the button click is not lost nor has
        // any unwanted behavior if the state changes right after checking its value in this switch.
        when (val s = _state.value) {
            is NotInitialized -> {} // wait for initialization to happen
            is NotAvailable -> {} // nothing to do
            is NotDownloaded -> download(s.modelUrl)
            is Downloading -> {} // wait for download to finish
            is ErrorDownloading -> download(s.modelUrl) // retry
            is Downloaded -> unzip()
            is Unzipping -> {} // wait for unzipping to finish
            is ErrorUnzipping -> unzip() // retry
            is NotLoaded -> load(eventListener)
            is Loading -> toggleThenStartListening(eventListener) // wait for loading to finish
            is ErrorLoading -> load(eventListener) // retry
            is Loaded -> startListening(s.speechService, eventListener)
            is Listening -> stopListening(s.speechService, s.eventListener, true)
        }
    }

    /**
     * If the recognizer is currently listening, stops listening. Otherwise does nothing.
     */
    override fun stopListening() {
        when (val s = _state.value) {
            is Listening -> stopListening(s.speechService, s.eventListener, true)
            else -> {}
        }
    }

    /**
     * Downloads the model zip file. Sets the state to [Downloading], and periodically updates it
     * with downloading progress, until either [ErrorDownloading] or [Downloaded] are set as state.
     */
    private fun download(modelUrl: String) {
        _state.value = Downloading(Progress.UNKNOWN)

        operationsJob = scope.launch(Dispatchers.IO) {
            try {
                downloadBinaryFilesWithPartial(
                    urlsFiles = listOf(FileToDownload(modelUrl, modelZipFile, sameModelUrlCheck)),
                    httpClient = okHttpClient,
                    cacheDir = cacheDir,
                ) { progress ->
                    _state.value = Downloading(progress)
                }

                // downloadBinaryFilesWithPartial will update the sameModelUrlCheck file contents
                // with the correct model url. We can do this safely now that the zip file with the
                // correct model url is in place, since even if the app were closed after this
                // step, the correct .zip will be extracted afterwards, even if modelExistFileCheck
                // already exists.

            } catch (e: IOException) {
                Log.e(TAG, "Can't download Vosk model", e)
                _state.value = ErrorDownloading(modelUrl, e)
                return@launch
            }

            _state.value = Unzipping(Progress.UNKNOWN)
            unzipImpl() // reuse same job
        }
    }

    /**
     * Sets the state to [Unzipping] and calls [unzipImpl] in the background.
     */
    private fun unzip() {
        _state.value = Unzipping(Progress.UNKNOWN)

        operationsJob = scope.launch {
            val currentAppLocale = localeManager.locale.value
            val currentLocale = resolveVoskLanguageCode(currentAppLocale)
            
            if (currentLocale != null) {
                // noModels变体优先尝试从外部存储复制模型
                if (!AppBuildConfig.HAS_MODELS_IN_ASSETS && AssetModelManager.hasVoskModelInExternalStorage(appContext, currentLocale)) {
                    Log.d(TAG, "Copying Vosk model from external storage for language: $currentLocale")
                    val copySuccess = AssetModelManager.copyVoskModelFromExternalStorage(appContext, currentLocale)
                    if (copySuccess) {
                        _state.value = NotLoaded
                        return@launch
                    }
                    Log.w(TAG, "Failed to copy Vosk model from external storage")
                }
                
                // 尝试从assets复制模型（withModels变体或fallback）
                if (AssetModelManager.hasVoskModelInAssets(appContext, currentLocale)) {
                    Log.d(TAG, "Copying Vosk model from assets for language: $currentLocale")
                    val copySuccess = AssetModelManager.copyVoskModel(appContext, currentLocale)
                    if (copySuccess) {
                        _state.value = NotLoaded
                        return@launch
                    }
                    Log.w(TAG, "Failed to copy Vosk model from assets, falling back to unzip")
                }
            }
            
            // 如果没有可用的预置模型或复制失败，则解压zip文件
            unzipImpl()
        }
    }

    /**
     * Unzips the downloaded model zip file. Assumes the state has already ben set to an
     * indeterminate [Unzipping]`(0, 0)`, but periodically publishes states with unzipping progress.
     * Will set the state to [ErrorUnzipping] or [NotLoaded] in the end. Also deletes the zip
     * file once downloading is successfully complete, to save disk space.
     */
    private suspend fun unzipImpl() {
        try {
            // delete the model directory in case there are leftover files from other models
            modelDirectory.deleteRecursively()
            extractZip(
                sourceZip = modelZipFile,
                destinationDirectory = modelDirectory,
            ) { progress ->
                _state.value = Unzipping(progress)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Can't unzip Vosk model", e)
            _state.value = ErrorUnzipping(e)
            return
        }

        // delete zip file after extraction to save memory
        if (!modelZipFile.delete()) {
            Log.w(TAG, "Can't delete Vosk model zip: $modelZipFile")
        }

        _state.value = NotLoaded
    }

    /**
     * Loads the model, and initially sets the state to [Loading] with [Loading.thenStartListening]
     * = ([thenStartListeningEventListener] != `null`), and later either sets the state to [Loaded]
     * or calls [startListening] by checking the current state's [Loading.thenStartListening]
     * (which might have changed from ([thenStartListeningEventListener] != `null`) in the meantime,
     * if the user clicked on the button while loading).
     */
    private fun load(thenStartListeningEventListener: ((InputEvent) -> Unit)?) {
        _state.value = Loading(thenStartListeningEventListener)

        operationsJob = scope.launch {
            val speechService: SpeechService
            try {
                // 检查模型目录是否存在和有效
                if (!modelDirectory.exists()) {
                    Log.e(TAG, "Model directory does not exist: ${modelDirectory.absolutePath}")
                    _state.value = ErrorLoading(IOException("Model directory not found: ${modelDirectory.absolutePath}"))
                    return@launch
                }
                
                if (!modelDirectory.isDirectory) {
                    Log.e(TAG, "Model path is not a directory: ${modelDirectory.absolutePath}")
                    _state.value = ErrorLoading(IOException("Model path is not a directory: ${modelDirectory.absolutePath}"))
                    return@launch
                }
                
                // 检查必要的模型文件是否存在
                val requiredFiles = listOf("ivector", "conf", "am")
                for (fileName in requiredFiles) {
                    val file = File(modelDirectory, fileName)
                    if (!file.exists()) {
                        Log.e(TAG, "Required model file missing: $fileName in ${modelDirectory.absolutePath}")
                        _state.value = ErrorLoading(IOException("Required model file missing: $fileName"))
                        return@launch
                    }
                }
                
                Log.d(TAG, "Loading Vosk model from: ${modelDirectory.absolutePath}")
                LibVosk.setLogLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARNINGS)
                val model = Model(modelDirectory.absolutePath)
                val recognizer = Recognizer(model, SAMPLE_RATE)
                recognizer.setMaxAlternatives(ALTERNATIVE_COUNT)
                speechService = SpeechService(recognizer, SAMPLE_RATE)
                Log.d(TAG, "✅ Vosk model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Vosk model from ${modelDirectory.absolutePath}", e)
                _state.value = ErrorLoading(e)
                return@launch
            }

            if (!_state.compareAndSet(Loading(null), Loaded(speechService))) {
                val state = _state.value
                if (state is Loading && state.thenStartListening != null) {
                    // "state is Loading" will always be true except when the load() is begin
                    // joined by init().
                    // "state.thenStartListening" might be "null" if, in the brief moment between
                    // the compareAndSet() and reading _state.value, the state was changed by
                    // toggleThenStartListening().
                    startListening(speechService, state.thenStartListening)

                } else if (!_state.compareAndSet(Loading(null, true), Loaded(speechService))) {
                    // The current state is not the Loading state, which is unexpected. This means
                    // that load() is begin joined by init(), which is reinitializing everything,
                    // so we should drop the speechService.
                    speechService.stop()
                    speechService.shutdown()
                }

            } // else, the state was set to Loaded, so no need to do anything
        }
    }

    /**
     * Atomically handles toggling the [Loading.thenStartListening] state, making sure that if in
     * the meantime the value is changed by [load], the user click is not wasted, and the state
     * machine does not end up in an inconsistent state.
     *
     * @param eventListener used only if the model has finished loading in the brief moment between
     * when the state is first checked, but if the state was switched to [Loaded] (and not
     * [Listening]), which means that this click should start listening.
     */
    private fun toggleThenStartListening(eventListener: (InputEvent) -> Unit) {
        if (
            !_state.compareAndSet(Loading(null), Loading(eventListener)) &&
            !_state.compareAndSet(Loading(eventListener), Loading(null))
        ) {
            // may happen if load() changes the state in the brief moment between when the state is
            // first checked before calling this function, and when the checks above are performed
            Log.w(TAG, "Cannot toggle thenStartListening")
            when (val newValue = _state.value) {
                is Loaded -> startListening(newValue.speechService, eventListener)
                is Listening -> stopListening(newValue.speechService, newValue.eventListener, true)
                is ErrorLoading -> {} // ignore the user's click
                // the else should never happen, since load() only transitions from Loading(...) to
                // one of Loaded, Listening or ErrorLoading
                else -> Log.e(TAG, "State was none of Loading, Loaded or Listening")
            }
        }
    }

    /**
     * Starts the speech service listening, and changes the state to [Listening].
     */
    private fun startListening(
        speechService: SpeechService,
        eventListener: (InputEvent) -> Unit,
    ) {
        _state.value = Listening(speechService, eventListener)
        speechService.startListening(VoskListener(this, eventListener, speechService))
    }

    /**
     * Stops the speech service from listening, and changes the state to [Loaded]. This is
     * `internal` because it is used by [VoskListener].
     */
    internal fun stopListening(
        speechService: SpeechService,
        eventListener: (InputEvent) -> Unit,
        sendNoneEvent: Boolean,
    ) {
        _state.value = Loaded(speechService)
        speechService.stop()
        if (sendNoneEvent) {
            eventListener(InputEvent.None)
        }
    }

    override suspend fun destroy() {
        deinit()
        // cancel everything
        scope.cancel()
    }

    /**
     * 解析Vosk支持的语言代码，处理特殊映射情况（如中文）
     */
    private fun resolveVoskLanguageCode(locale: Locale): String? {
        Log.d(TAG, "    🔍 resolveVoskLanguageCode调试:")
        Log.d(TAG, "      📥 输入Locale: $locale")
        
        return try {
            val localeResolutionResult = LocaleUtils.resolveSupportedLocale(
                LocaleListCompat.create(locale),
                MODEL_URLS.keys
            )
            Log.d(TAG, "      ✅ 标准解析成功: ${localeResolutionResult.supportedLocaleString}")
            localeResolutionResult.supportedLocaleString
        } catch (e: LocaleUtils.UnsupportedLocaleException) {
            Log.d(TAG, "      ⚠️ 标准解析失败: ${e.message}")
            // 特殊处理语言映射
            val result = when {
                locale.language == "zh" -> {
                    Log.d(TAG, "      🔄 特殊映射: zh -> cn")
                    "cn"  // 中文映射到cn
                }
                locale.language == "cn" -> {
                    Log.d(TAG, "      🔄 特殊映射: cn -> cn")
                    "cn"  // cn保持cn
                }
                else -> {
                    Log.d(TAG, "      ❌ 无匹配的语言映射: ${locale.language}")
                    null
                }
            }
            Log.d(TAG, "      📤 最终结果: $result")
            result
        }
    }

    companion object {
        private const val SAMPLE_RATE = 44100.0f
        private const val ALTERNATIVE_COUNT = 5
        private val TAG = VoskInputDevice::class.simpleName

        /**
         * All small models from [Vosk](https://alphacephei.com/vosk/models)
         */
        val MODEL_URLS = mapOf(
            "en" to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "en-in" to "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            "cn" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            "ru" to "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            "fr" to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            "de" to "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
            "es" to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            "pt" to "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
            "tr" to "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
            "vn" to "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
            "it" to "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
            "nl" to "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip",
            "ca" to "https://alphacephei.com/vosk/models/vosk-model-small-ca-0.4.zip",
            "ar" to "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
            "ar-tn" to "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip",
            "fa" to "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip",
            "ph" to "https://alphacephei.com/vosk/models/vosk-model-tl-ph-generic-0.6.zip",
            "uk" to "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-nano.zip",
            "kz" to "https://alphacephei.com/vosk/models/vosk-model-small-kz-0.15.zip",
            "sv" to "https://alphacephei.com/vosk/models/vosk-model-small-sv-rhasspy-0.15.zip",
            "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            "eo" to "https://alphacephei.com/vosk/models/vosk-model-small-eo-0.42.zip",
            "hi" to "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            "cs" to "https://alphacephei.com/vosk/models/vosk-model-small-cs-0.4-rhasspy.zip",
            "pl" to "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip",
            "uz" to "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip",
            "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            "br" to "https://alphacephei.com/vosk/models/vosk-model-br-0.8.zip",
            "gu" to "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip",
            "tg" to "https://alphacephei.com/vosk/models/vosk-model-small-tg-0.22.zip",
            "te" to "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
        )
    }
}
