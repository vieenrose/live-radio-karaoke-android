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
    val downloadDialogVisible = MutableStateFlow(true)   // user can hide it and keep listening

    // Engine-info row (which on-device models are in use) — shown in the summary panel.
    val sttModelLabel = MutableStateFlow(if (nativeAvailable) "—" else "off (audio-only build)")
    val llmModelLabel = MutableStateFlow(
        if (nativeAvailable && tier.enableSummarizer) Config.llmLabel(Config.DEFAULT_LLM) else "off"
    )

    private var llmEngine: LlmEngine? = null
    private var asrJob: Job? = null
    private var llmKey = Config.DEFAULT_LLM
    private var gemmaConsented = false

    private val sessionTranscript = mutableListOf<Utterance>()   // full, for export

    // ---- Playback ----
    fun selectAndPlay(station: Station) {
        currentStation.value = station
        // 1) Radio plays IMMEDIATELY — never blocked on a model download.
        startPlayback(station)
        // 2) Transcription comes up in the background (download models if needed).
        ensureTranscription(station)
    }

    private fun startPlayback(station: Station) {
        stopPipeline()
        utterances.value = emptyList()
        sessionTranscript.clear()
        controller.setVolume(volume.value)
        controller.play(station.url)
    }

    /** Download the right models (if missing) then start ASR/LLM. Playback is already running. */
    private fun ensureTranscription(station: Station) {
        if (!nativeAvailable) return
        val asrSpec = Config.asrModelForLanguage(station.language)
        val llmSpec = Config.LLM_MODELS.getValue(llmKey)
        val needLlm = tier.enableSummarizer
        sttModelLabel.value = Config.asrLabel(asrSpec.key)
        llmModelLabel.value = if (needLlm) Config.llmLabel(llmSpec.key) else "off"
        // Gemma terms gate only the LLM download — playback keeps going.
        if (needLlm && !llmSpec.freeLicense && !gemmaConsented && !repo.isLlmReady(llmSpec)) {
            download.value = DownloadState.NeedGemmaConsent
            return
        }
        downloadDialogVisible.value = true
        // ASR: download (if needed) then start — independent of the (much larger) LLM download.
        viewModelScope.launch {
            try {
                if (!repo.isAsrReady(asrSpec))
                    repo.downloadAsr(asrSpec) { p -> download.value = DownloadState.Running("Speech-recognition model", frac(p)) }
                if (download.value is DownloadState.Running) download.value = DownloadState.Idle
                startAsr(asrSpec)
            } catch (t: Throwable) {
                download.value = DownloadState.Error(t.message ?: "speech-model download failed")
            }
        }
        // LLM: download + start in parallel — never blocks the live transcript.
        if (needLlm) viewModelScope.launch {
            try {
                android.util.Log.i("LRK", "LLM setup: ${llmSpec.key} ready=${repo.isLlmReady(llmSpec)}")
                if (!repo.isLlmReady(llmSpec))
                    repo.downloadLlm(llmSpec) { p -> download.value = DownloadState.Running("Summary / translation model", frac(p)) }
                if (download.value is DownloadState.Running) download.value = DownloadState.Idle
                startLlm(llmSpec)
            } catch (t: Throwable) { android.util.Log.e("LRK", "LLM setup failed", t) }
        }
    }

    fun acceptGemmaTermsAndContinue() {
        gemmaConsented = true
        download.value = DownloadState.Idle
        currentStation.value?.let { ensureTranscription(it) }
    }

    /** Switch to a fully-open LLM (skips the Gemma terms). */
    fun useOpenModelInstead() {
        llmKey = "lfm2.5-1.2b"
        download.value = DownloadState.Idle
        currentStation.value?.let { ensureTranscription(it) }
    }

    private fun frac(p: ModelRepository.Progress): Float =
        if (p.totalBytes > 0) (p.downloadedBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f

    /** Start streaming ASR against the already-playing stream (ASR model is present here). */
    private fun startAsr(asr: AsrModelSpec) {
        asrJob?.cancel()
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

    /** Load + start the LLM (summaries + translation). LLM model is present here. */
    private fun startLlm(llm: LlmModelSpec) {
        if (!repo.isLlmReady(llm)) { android.util.Log.w("LRK", "startLlm: ${llm.key} NOT ready"); return }
        val engine = LlmEngine(repo.llmFile(llm).absolutePath, tier, viewModelScope).also { llmEngine = it }
        engine.targetLanguage = targetLanguage.value
        engine.onSummary = { item -> onSummary(item) }
        engine.onTranslation = { id, text, streaming -> onTranslation(id, text, streaming) }
        viewModelScope.launch {
            val ok = engine.load()
            android.util.Log.i("LRK", "LLM ${llm.key} load=$ok size=${repo.llmFile(llm).length()}")
            if (ok) {
                engine.start()
                launch { engine.activity.collect { llmActivity.value = it } }
            }
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
