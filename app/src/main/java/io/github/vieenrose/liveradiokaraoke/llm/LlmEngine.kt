package io.github.vieenrose.liveradiokaraoke.llm

import io.github.vieenrose.liveradiokaraoke.data.DeviceTier
import io.github.vieenrose.liveradiokaraoke.data.LlmActivity
import io.github.vieenrose.liveradiokaraoke.data.SummaryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One on-device LLM (Gemma 3 1B) serving BOTH summarization and live translation,
 * serialized by a [Mutex] (llama.cpp is not concurrency-safe). Translation runs off a
 * small drop-oldest queue and yields to summaries. Port of core/summarizer_service.py.
 */
class LlmEngine(
    private val modelPath: String,
    private val tier: DeviceTier,
    private val scope: CoroutineScope,
) {
    val activity = MutableStateFlow(LlmActivity.IDLE)

    @Volatile var targetLanguage: String? = null

    var onSummary: ((SummaryItem) -> Unit)? = null
    var onTranslation: ((id: Int, text: String, streaming: Boolean) -> Unit)? = null

    private val bridge = LlamaBridge()
    private val llmLock = Mutex()
    @Volatile private var summarizing = false

    private val transcript = StringBuilder()
    private val pendingIds = mutableListOf<Int>()

    // Bounded, drop-oldest: translation never blocks the ASR/summary path.
    private val translateQueue = Channel<Pair<Int, String>>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun load(): Boolean = withContext(Dispatchers.Default) {
        bridge.load(modelPath, nCtx = tier.llmContextChars, nThreads = tier.llmThreads)
    }

    fun start() {
        scope.launch(Dispatchers.Default) {
            for ((id, text) in translateQueue) {
                val target = targetLanguage ?: continue
                if (summarizing) continue   // summaries have priority
                llmLock.withLock {
                    if (summarizing) return@withLock
                    activity.value = LlmActivity.TRANSLATING
                    val zh = target.contains("Chinese", ignoreCase = true)
                    val sb = StringBuilder()
                    var n = 0
                    val (tSys, tUsr) = ChatPrompt.translation(text, target)
                    generate(tSys, tUsr, maxTokens = 160) { piece ->
                        sb.append(piece)
                        if (++n % 4 == 0) onTranslation?.invoke(id, toTw(sb.toString(), zh), true)
                    }
                    onTranslation?.invoke(id, toTw(sb.toString().trim(), zh), false)
                    if (!summarizing) activity.value = LlmActivity.IDLE
                }
            }
        }
    }

    /** Feed a finalized utterance; may enqueue translation and/or trigger a summary. */
    fun onFinalUtterance(id: Int, text: String) {
        synchronized(transcript) {
            transcript.append(text).append(' ')
            pendingIds.add(id)
        }
        if (targetLanguage != null) translateQueue.trySend(id to text)
        maybeSummarize()
    }

    private fun maybeSummarize() {
        if (!tier.enableSummarizer || summarizing) return
        val len = synchronized(transcript) { transcript.length }
        if (len <= tier.summaryThresholdChars) return
        scope.launch(Dispatchers.Default) { runSummary() }
    }

    private suspend fun runSummary() {
        if (summarizing) return
        summarizing = true
        activity.value = LlmActivity.SUMMARIZING
        try {
            llmLock.withLock {
                val (input, ids) = synchronized(transcript) {
                    val budget = maxOf(256, tier.llmContextChars - 128 - 256)
                    transcript.toString().takeLast(budget) to pendingIds.toList()
                }
                val sb = StringBuilder()
                var n = 0
                val (sSys, sUsr) = ChatPrompt.summary(input)
                generate(sSys, sUsr, maxTokens = 128) { piece ->
                    sb.append(piece)
                    if (++n % 6 == 0) onSummary?.invoke(SummaryItem(sb.toString(), now(), ids, true))
                }
                onSummary?.invoke(SummaryItem(sb.toString().trim(), now(), ids, false))

                // Retain ~5% of the buffer for continuity; clear the rest.
                synchronized(transcript) {
                    val keep = transcript.takeLast(tier.contextKeepAliveChars).toString()
                    transcript.setLength(0); transcript.append(keep)
                    pendingIds.clear()
                }
            }
        } finally {
            activity.value = LlmActivity.IDLE
            summarizing = false
        }
    }

    private inline fun generate(system: String, user: String, maxTokens: Int, onPiece: (String) -> Unit) {
        bridge.start(system, user)
        var produced = 0
        while (produced < maxTokens) {
            val piece = bridge.next()
            if (piece.isEmpty()) break
            onPiece(piece)
            produced++
        }
    }

    fun free() {
        translateQueue.close()
        bridge.free()
    }

    private fun now() = System.currentTimeMillis()

    /** Convert a Chinese translation to Traditional (zh-TW), matching the original app's OpenCC step. */
    private fun toTw(s: String, isChinese: Boolean): String =
        if (isChinese && s.isNotBlank())
            runCatching { com.github.houbb.opencc4j.util.ZhConverterUtil.toTraditional(s) }.getOrDefault(s)
        else s
}
