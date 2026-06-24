package io.github.vieenrose.liveradiokaraoke.data

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings

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
    LOW(1, 1800, 2048, 2, true),
    // High-end (e.g. Pixel 6): use the 4 performance cores for the LLM decode; keep ASR at 2 so the
    // two engines don't oversubscribe the big cores while the summarizer/translator is running.
    NORMAL(2, 2000, 4096, 4, true);

    /** Keep ~5% of the transcript after a summary for continuity (CONTEXT_KEEPALIVE_CHARS). */
    val contextKeepAliveChars: Int get() = minOf(100, llmContextChars / 20)

    companion object {
        fun forDevice(context: Context): DeviceTier {
            // Debug/QA override: `adb shell settings put global lrk_force_tier {ULTRA_LOW|LOW|NORMAL}`.
            runCatching { Settings.Global.getString(context.contentResolver, "lrk_force_tier") }
                .getOrNull()?.uppercase()?.let { forced ->
                    entries.firstOrNull { it.name == forced }?.let { return it }
                }
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
