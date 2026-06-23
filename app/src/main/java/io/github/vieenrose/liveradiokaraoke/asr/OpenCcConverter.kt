package io.github.vieenrose.liveradiokaraoke.asr

import com.github.houbb.opencc4j.util.ZhConverterUtil
import io.github.vieenrose.liveradiokaraoke.data.OpenCcMode

/**
 * Simplified → Traditional conversion, port of the OpenCC handling in core/asr_service.py.
 * (opencc4j's toTraditional is the closest open equivalent of OpenCC s2twp.)
 */
class OpenCcConverter(private val mode: OpenCcMode) {

    private fun convert(text: String): String =
        if (text.isBlank()) text else runCatching { ZhConverterUtil.toTraditional(text) }.getOrDefault(text)

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF

    /** Drop a BPE word-boundary space before a CJK char (keeps spaces before Latin). */
    private fun stripCjkSpace(token: String): String =
        if (token.length >= 2 && token[0] == ' ' && isCjk(token[1])) token.substring(1) else token

    /** Returns (displayText, tokens) after conversion, matching the Python modes. */
    fun apply(rawText: String, rawTokens: List<String>): Pair<String, List<String>> = when (mode) {
        OpenCcMode.NONE -> rawText to rawTokens
        OpenCcMode.CHARS -> {
            val converted = convert(rawText)
            converted to converted.replace(" ", "").map { it.toString() }
        }
        OpenCcMode.TOKENS -> {
            val toks = rawTokens.map { stripCjkSpace(convert(it)) }
            toks.joinToString("") to toks
        }
    }
}
