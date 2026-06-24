package io.github.vieenrose.liveradiokaraoke.llm

/**
 * Builds (system, user) message pairs. The JNI bridge applies the LOADED MODEL's own chat template
 * (Gemma, LFM2/ChatML, Qwen…), so we must NOT add any role markers here — doing so leaks tokens
 * like `<start_of_turn>` into the output when the model isn't Gemma.
 */
object ChatPrompt {

    private const val SUMMARY_SYSTEM =
        "You summarize live radio. Reply with ONLY a 1-2 sentence summary (max 40 words), " +
            "neutral and factual — no preamble, no labels, no headings."

    /** Returns (system, user). */
    fun summary(transcript: String): Pair<String, String> =
        SUMMARY_SYSTEM to "Transcript:\n$transcript"

    fun translation(text: String, targetLanguage: String): Pair<String, String> {
        val system = "You are a translator. Translate the user's text into $targetLanguage. " +
            "Output ONLY the translation — no notes, no quotes, no original text."
        return system to text.trim()
    }
}
