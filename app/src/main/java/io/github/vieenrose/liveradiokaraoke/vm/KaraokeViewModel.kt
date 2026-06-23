package io.github.vieenrose.liveradiokaraoke.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import io.github.vieenrose.liveradiokaraoke.BuildConfig
import io.github.vieenrose.liveradiokaraoke.asr.AsrEngine
import io.github.vieenrose.liveradiokaraoke.audio.RadioStreamController
import io.github.vieenrose.liveradiokaraoke.data.*
import io.github.vieenrose.liveradiokaraoke.llm.LlamaBridge
import io.github.vieenrose.liveradiokaraoke.llm.LlmEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val label: String, val fraction: Float) : DownloadState
    data object NeedGemmaConsent : DownloadState
    data object Done : DownloadState
    data class Error(val message: String) : DownloadState
}

@UnstableApi
class KaraokeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ModelRepository(app)
    private val tier = DeviceTier.forDevice(app)
    private val browser = RadioBrowserApi()
    val controller = RadioStreamController(app)

    val nativeAvailable = BuildConfig.WITH_NATIVE && AsrEngine.nativeAvailable() && LlamaBridge.nativeAvailable()

    // ---- UI state ----
    val playbackState get() = controller.playbackState
    val nowPlaying get() = controller.nowPlaying
    val currentStation = MutableStateFlow<Station?>(null)
    val utterances = MutableStateFlow<List<Utterance>>(emptyList())
    val summaries = MutableStateFlow<List<SummaryItem>>(emptyList())
    val llmActivity = MutableStateFlow(LlmActivity.IDLE)
    val targetLanguage = MutableStateFlow<String?>(null)
    val volume = MutableStateFlow(1f)
    val userSyncOffset = MutableStateFlow(0f)         // seconds, debug slider
    val deviceTier = tier

    val bundledStations: List<Station> = Config.STATIONS
    val discovered = MutableStateFlow<List<Station>>(emptyList())
    val download = MutableStateFlow<DownloadState>(DownloadState.Idle)

    private var llmEngine: LlmEngine? = null
    private var asrJob: Job? = null
    private var llmKey = Config.DEFAULT_LLM
    private var gemmaConsented = false

    private val sessionTranscript = mutableListOf<Utterance>()   // full, for export

    // ---- Playback ----
    fun selectAndPlay(station: Station) {
        val asrSpec = Config.asrModelForLanguage(station.language)
        val llmSpec = Config.LLM_MODELS.getValue(llmKey)
        val needLlm = tier.enableSummarizer
        val ready = (!nativeAvailable) ||
            (repo.isAsrReady(asrSpec) && (!needLlm || repo.isLlmReady(llmSpec)))
        if (!ready) {
            currentStation.value = station
            if (needLlm && !llmSpec.freeLicense && !gemmaConsented) {
                download.value = DownloadState.NeedGemmaConsent
            } else {
                downloadThenPlay(station, asrSpec, llmSpec, needLlm)
            }
            return
        }
        startPipeline(station, asrSpec, llmSpec, needLlm)
    }

    fun acceptGemmaTermsAndContinue() {
        gemmaConsented = true
        val station = currentStation.value ?: return
        val asrSpec = Config.asrModelForLanguage(station.language)
        downloadThenPlay(station, asrSpec, Config.LLM_MODELS.getValue(llmKey), tier.enableSummarizer)
    }

    /** Switch to a fully-open LLM (skips the Gemma terms). */
    fun useOpenModelInstead() {
        llmKey = "lfm2.5-1.2b"
        val station = currentStation.value ?: return
        selectAndPlay(station)
    }

    private fun downloadThenPlay(station: Station, asr: AsrModelSpec, llm: LlmModelSpec, needLlm: Boolean) {
        viewModelScope.launch {
            try {
                if (nativeAvailable) {
                    repo.downloadAsr(asr) { p -> download.value = DownloadState.Running("ASR ${asr.key}", frac(p)) }
                    if (needLlm) repo.downloadLlm(llm) { p -> download.value = DownloadState.Running("LLM ${llm.key}", frac(p)) }
                }
                download.value = DownloadState.Done
                startPipeline(station, asr, llm, needLlm)
            } catch (t: Throwable) {
                download.value = DownloadState.Error(t.message ?: "download failed")
            }
        }
    }

    private fun frac(p: ModelRepository.Progress): Float =
        if (p.totalBytes > 0) (p.downloadedBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f

    @Suppress("unused")
    private fun startPipeline(station: Station, asr: AsrModelSpec, llm: LlmModelSpec, needLlm: Boolean) {
        stopPipeline()
        currentStation.value = station
        utterances.value = emptyList()
        sessionTranscript.clear()
        controller.setVolume(volume.value)
        controller.play(station.url)

        if (!nativeAvailable) return   // audio-only build (no on-device models)

        // LLM
        if (needLlm && repo.isLlmReady(llm)) {
            val engine = LlmEngine(repo.llmFile(llm).absolutePath, tier, viewModelScope).also { llmEngine = it }
            engine.targetLanguage = targetLanguage.value
            engine.onSummary = { item -> onSummary(item) }
            engine.onTranslation = { id, text, streaming -> onTranslation(id, text, streaming) }
            viewModelScope.launch {
                if (engine.load()) {
                    engine.start()
                    // mirror activity state
                    launch { engine.activity.collect { llmActivity.value = it } }
                }
            }
        }

        // ASR
        val files = repo.resolveAsrFiles(asr)
        val engine = AsrEngine(asr, files, tier.asrThreads)
        asrJob = viewModelScope.launch {
            engine.run(
                pcm = controller.tap.pcmChannel,
                mediaTimeSeconds = { controller.positionSeconds() },
                onUtterance = { u -> onUtterance(u) },
                onFinalText = { id, text -> llmEngine?.onFinalUtterance(id, text) },
            )
        }
    }

    fun stop() {
        stopPipeline()
        controller.stop()
        currentStation.value = null
    }

    private fun stopPipeline() {
        asrJob?.cancel(); asrJob = null
        llmEngine?.free(); llmEngine = null
        llmActivity.value = LlmActivity.IDLE
    }

    // ---- Stream callbacks ----
    private fun onUtterance(u: Utterance) {
        val list = utterances.value.toMutableList()
        val idx = list.indexOfFirst { it.id == u.id }
        // Preserve any translation already attached to this id.
        val merged = if (idx >= 0) u.copy(translation = list[idx].translation) else u
        if (idx >= 0) list[idx] = merged else list.add(merged)
        utterances.value = list.takeLast(20)
        if (u.isFinal) {
            val si = sessionTranscript.indexOfFirst { it.id == u.id }
            if (si >= 0) sessionTranscript[si] = merged else sessionTranscript.add(merged)
        }
    }

    private fun onTranslation(id: Int, text: String, streaming: Boolean) {
        utterances.value = utterances.value.map {
            if (it.id == id) it.copy(translation = text, translationStreaming = streaming) else it
        }
    }

    private fun onSummary(item: SummaryItem) {
        val list = summaries.value.toMutableList()
        if (list.isNotEmpty() && list.last().streaming) list[list.size - 1] = item else list.add(item)
        summaries.value = list.takeLast(20)
        if (!item.streaming) {
            val ids = item.sourceUtteranceIds.toSet()
            utterances.value = utterances.value.map { if (it.id in ids) it.copy(summarized = true) else it }
        }
    }

    // ---- UI actions ----
    fun setTargetLanguage(lang: String?) {
        targetLanguage.value = lang
        llmEngine?.targetLanguage = lang
    }

    fun setVolume(v: Float) { volume.value = v; controller.setVolume(v) }
    fun setSyncOffset(v: Float) { userSyncOffset.value = v }

    fun discover(search: String, country: String, language: String, category: RadioBrowserApi.Category) {
        viewModelScope.launch { discovered.value = browser.discover(search, country, language, category) }
    }

    fun sessionForExport(): List<Utterance> = sessionTranscript.toList()

    override fun onCleared() {
        stopPipeline()
        controller.release()
    }
}
