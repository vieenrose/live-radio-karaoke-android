package io.github.vieenrose.liveradiokaraoke.data

import android.app.ActivityManager
import android.content.Context

/**
 * Port of performance_config.py, but the tier is chosen from device RAM instead of an
 * env var. Tunes ASR/LLM thread counts, the summary trigger threshold and LLM context.
 */
enum class DeviceTier(
    val asrThreads: Int,
    val summaryThresholdChars: Int,
    val llmContextChars: Int,
    val llmThreads: Int,
    val enableSummarizer: Boolean,
) {
    ULTRA_LOW(1, 10_000, 1024, 1, false),
    LOW(1, 1800, 2048, 1, true),
    NORMAL(2, 2000, 4096, 2, true);

    /** Keep ~5% of the transcript after a summary for continuity (CONTEXT_KEEPALIVE_CHARS). */
    val contextKeepAliveChars: Int get() = minOf(100, llmContextChars / 20)

    companion object {
        fun forDevice(context: Context): DeviceTier {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalGb = mem.totalMem / (1024.0 * 1024 * 1024)
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                totalGb < 3.0 -> ULTRA_LOW
                totalGb < 6.0 || cores < 6 -> LOW
                else -> NORMAL
            }
        }
    }
}
