package io.github.vieenrose.liveradiokaraoke.data

/** A radio station — bundled or discovered via Radio Browser. */
data class Station(
    val name: String,
    val url: String,
    val language: String = "en",      // ASR language: en | fr | zh
    val detectedLanguage: String = language,
    val isFallbackLanguage: Boolean = false,
    val homepage: String = "",
    val favicon: String = "",
    val country: String = "",
    val bitrate: Int = 0,
    val tags: String = "",
)

/**
 * One transcription segment. Mirrors the web client's per-utterance DOM model:
 * tokens + absolute media-clock times drive the karaoke highlight; [translation]
 * fills the parallel column; [summarized] tints utterances folded into a summary.
 */
data class Utterance(
    val id: Int,
    val tokens: List<String>,
    val tokenTimes: List<Double>,   // absolute seconds on the player timeline
    val startTime: Double,
    val text: String,
    val isFinal: Boolean,
    val translation: String = "",
    val translationStreaming: Boolean = false,
    val summarized: Boolean = false,
)

enum class LlmActivity { IDLE, SUMMARIZING, TRANSLATING }

data class SummaryItem(
    val text: String,
    val timestampMillis: Long,
    val sourceUtteranceIds: List<Int>,
    val streaming: Boolean,
)

enum class PlaybackState { STOPPED, CONNECTING, BUFFERING, PLAYING, RECONNECTING, ERROR }

/** Whether the on-device models are present on disk. */
data class ModelReadiness(
    val asrReady: Boolean,
    val llmReady: Boolean,
)
