package io.github.vieenrose.liveradiokaraoke.data

/** OpenCC handling for an ASR model's output (see asr/OpenCcConverter). */
enum class OpenCcMode { NONE, CHARS, TOKENS }

sealed interface AsrSource {
    /** Hugging Face repo snapshot. */
    data class Hf(val repoId: String) : AsrSource
    /** GitHub release .tar.bz2 (the X-ASR models). */
    data class GithubTar(val url: String) : AsrSource
}

data class AsrModelSpec(
    val key: String,
    val dirName: String,
    val source: AsrSource,
    val openCc: OpenCcMode,
)

data class LlmModelSpec(
    val key: String,
    val fileName: String,
    val repoId: String,
    val reasoning: Boolean = false,
    /** True for OSI/Apache-style licences; false for Gemma's non-free terms. */
    val freeLicense: Boolean = true,
)

/**
 * Faithful port of the Python config.py registries, stations and language routing.
 * On-device defaults: en & zh → bilingual X-ASR (1920 ms, cheapest on CPU); fr → fr-kroko.
 * Default LLM → Gemma 3 1B (QAT Q4_0).
 */
object Config {

    const val SAMPLE_RATE = 16000
    const val MAX_SEGMENT_CHARS = 160   // force-split long utterances (asr_service.py)

    val ASR_MODELS: Map<String, AsrModelSpec> = listOf(
        AsrModelSpec(
            "en-20M", "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
            AsrSource.Hf("csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17"), OpenCcMode.NONE,
        ),
        AsrModelSpec(
            "fr-kroko", "sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06",
            AsrSource.Hf("csukuangfj/sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06"), OpenCcMode.NONE,
        ),
        AsrModelSpec(
            "zh-14M", "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
            AsrSource.Hf("csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"), OpenCcMode.CHARS,
        ),
        xAsr("160ms"), xAsr("480ms"), xAsr("960ms"), xAsr("1920ms"),
    ).associateBy { it.key }

    private fun xAsr(latency: String): AsrModelSpec {
        val name = "sherpa-onnx-x-asr-$latency-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05"
        return AsrModelSpec(
            "x-asr-zh-en-$latency", name,
            AsrSource.GithubTar("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$name.tar.bz2"),
            OpenCcMode.TOKENS,
        )
    }

    /**
     * Language → ASR model key. en/zh use the 480 ms-chunk X-ASR variant: its ~0.5 s look-ahead
     * keeps the transcript close to the audio (the 1920 ms variant lagged ~2 s). Compute is still
     * tiny (RTF well under 0.1); the variants differ in latency/accuracy, not in download size.
     */
    val ASR_MODEL_FOR = mapOf(
        "en" to "x-asr-zh-en-480ms",
        "fr" to "fr-kroko",
        "zh" to "x-asr-zh-en-480ms",
    )

    val LLM_MODELS: Map<String, LlmModelSpec> = listOf(
        // Default per request. Gemma Terms of Use = NON-FREE → runtime download + consent gate.
        LlmModelSpec("gemma", "google_gemma-3-1b-it-qat-Q4_0.gguf",
            "bartowski/google_gemma-3-1b-it-qat-GGUF", reasoning = false, freeLicense = false),
        // Fully-open alternatives for a zero-non-free path.
        LlmModelSpec("qwen2.5-1.5b", "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            "bartowski/Qwen2.5-1.5B-Instruct-GGUF"),
        LlmModelSpec("lfm2.5-1.2b", "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "LiquidAI/LFM2.5-1.2B-Instruct-GGUF"),
        LlmModelSpec("lfm2.5-350m", "LFM2.5-350M-Q4_K_M.gguf",
            "LiquidAI/LFM2.5-350M-GGUF"),
    ).associateBy { it.key }

    const val DEFAULT_LLM = "gemma"

    fun hfFileUrl(repoId: String, fileName: String) =
        "https://huggingface.co/$repoId/resolve/main/$fileName?download=true"

    // --- Bundled stations (config.py RADIO_URLS) ---
    val STATIONS: List<Station> = buildList {
        fun en(n: String, u: String) = add(Station(n, u, "en"))
        fun fr(n: String, u: String) = add(Station(n, u, "fr"))
        fun zh(n: String, u: String) = add(Station(n, u, "zh"))

        en("KEXP (Seattle, 64 kbps)", "https://kexp.streamguys1.com/kexp64.aac")
        en("KEXP (Seattle, 160 kbps)", "https://kexp.streamguys1.com/kexp160.aac")
        en("NPR", "https://npr-ice.streamguys1.com/live.mp3")
        en("WYPR 88.1 FM (Baltimore)", "https://wtmd-ice.streamguys1.com/wypr-1-mp3")
        en("WAMU 88.5 FM (Washington DC)", "https://wamu.cdnstream1.com/wamu.mp3")
        en("BBC World Service", "http://stream.live.vc.bbcmedia.co.uk/bbc_world_service")
        en("BBC Radio 4 (UK)", "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_fourfm")
        en("BBC Radio 5 Live (UK)", "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_five_live_online_nonuk")
        en("BBC Radio 2 (UK)", "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_two")
        en("KQED NPR (San Francisco)", "https://streams.kqed.org/kqedradio")
        en("WNYC 93.9 FM (New York)", "http://stream.wnyc.org/wnycfm")
        en("WBUR 90.9 FM (Boston)", "http://icecast.wbur.org/wbur")
        en("KPCC 89.3 FM (Los Angeles)", "http://kpcclive.streamguys1.com/kpcc64.aac")
        en("WHYY 90.9 FM (Philadelphia)", "http://whyy.streamguys1.com/whyy-mp3")
        en("ABC News Radio (Australia)", "http://live-radio01.mediahubaustralia.com/PBW/mp3/")
        en("CBC Radio One (Toronto)", "http://cbc_r1_tor.akacast.akamaistream.net/7/15/451661/v1/rc.akacast.akamaistream.net/cbc_r1_tor")
        en("Voice of America (VOA News Now)", "https://voa-18.akacast.akamaistream.net/7/983/437752/v1/ibb.akacast.akamaistream.net/voa-18")
        en("Al Jazeera English (Audio)", "https://live-hls-web-aje.getaj.net/AJE/01.m3u8")
        en("PRI The World", "http://stream.pri.org:8000/pri.mp3")
        en("Radio Paradise (USA, Mix)", "http://stream.radioparadise.com/mp3-128")
        en("KCRW 89.9 FM (Santa Monica)", "http://kcrw.streamguys1.com/kcrw_192")

        fr("France Inter", "https://direct.franceinter.fr/live/franceinter-midfi.mp3")
        fr("France Info", "https://direct.franceinfo.fr/live/franceinfo-midfi.mp3")
        fr("France Culture", "https://direct.franceculture.fr/live/franceculture-midfi.mp3")
        fr("FIP", "https://direct.fip.fr/live/fip-midfi.mp3")
        fr("Radio Classique", "https://radioclassique.ice.infomaniak.ch/radioclassique-high.mp3")

        zh("中廣新聞網", "https://stream.rcs.revma.com/78fm9wyy2tzuv")
        zh("News98新聞網", "https://stream.rcs.revma.com/pntx1639ntzuv.m4a")
        zh("飛碟聯播網", "https://stream.rcs.revma.com/em90w4aeewzuv")
    }

    val SUPPORTED_ASR_LANGUAGES = setOf("en", "fr", "zh")

    /** config.py LANGUAGE_FALLBACK (unsupported detected language → nearest supported ASR). */
    private val LANGUAGE_FALLBACK = mapOf(
        "es" to "en", "de" to "en", "it" to "en", "pt" to "en", "nl" to "en", "fi" to "en",
        "ja" to "zh", "ko" to "zh", "th" to "en", "vi" to "en",
        "ar" to "en", "tr" to "en", "ru" to "en",
    )

    /** Returns (asrLanguage, isFallback). */
    fun resolveAsrLanguage(detected: String): Pair<String, Boolean> =
        if (detected in SUPPORTED_ASR_LANGUAGES) detected to false
        else (LANGUAGE_FALLBACK[detected] ?: "en") to true

    fun asrModelForLanguage(language: String): AsrModelSpec {
        val key = ASR_MODEL_FOR[language] ?: "x-asr-zh-en-480ms"
        return ASR_MODELS.getValue(key)
    }

    /** Short human label for the engine-info row (e.g. "X-ASR zh·en 480ms", "Gemma 3 1B"). */
    fun asrLabel(key: String): String = when {
        key.startsWith("x-asr-zh-en-") -> "X-ASR zh·en ${key.removePrefix("x-asr-zh-en-")}"
        key == "fr-kroko" -> "Zipformer fr"
        key == "en-20M" -> "Zipformer en 20M"
        key == "zh-14M" -> "Zipformer zh 14M"
        else -> key
    }

    fun llmLabel(key: String): String = when (key) {
        "gemma" -> "Gemma 3 1B"
        "qwen2.5-1.5b" -> "Qwen2.5 1.5B"
        "lfm2.5-1.2b" -> "LFM2.5 1.2B"
        "lfm2.5-350m" -> "LFM2.5 350M"
        else -> key
    }
}
