package io.github.vieenrose.liveradiokaraoke.llm

/**
 * Gemma 3 chat formatting. Gemma has no system role, so system guidance is merged into
 * the user turn. BOS is added by the tokenizer (add_special=true), so it is omitted here.
 * Prompts mirror core/summarizer_service.py (non-reasoning variants).
 */
object GemmaPrompt {

    private const val SUMMARY_SYSTEM =
        "You summarize live radio. Reply with ONLY a 1-2 sentence summary (max 40 words), " +
            "neutral and factual — no preamble, no labels, no headings."

    private fun turn(content: String) =
        "<start_of_turn>user\n$content<end_of_turn>\n<start_of_turn>model\n"

    fun summary(transcript: String): String =
        turn("$SUMMARY_SYSTEM\n\nTranscript:\n$transcript")

    fun translation(text: String, targetLanguage: String): String {
        val system = "You are a translator. Translate the user's text into $targetLanguage. " +
            "Output ONLY the translation — no notes, no quotes, no original."
        return turn("$system\n\n${text.trim()}")
    }
}
