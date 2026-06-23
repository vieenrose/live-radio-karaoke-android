package io.github.vieenrose.liveradiokaraoke.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.vieenrose.liveradiokaraoke.data.Utterance

/** Export the full session transcript as SRT or plain text into the Downloads folder. */
object TranscriptExporter {

    fun asText(utterances: List<Utterance>): String =
        utterances.joinToString("\n") { it.text }

    fun asSrt(utterances: List<Utterance>): String = buildString {
        utterances.forEachIndexed { i, u ->
            val start = u.startTime
            val end = utterances.getOrNull(i + 1)?.startTime ?: (start + 4.0)
            append(i + 1).append('\n')
            append(srtTime(start)).append(" --> ").append(srtTime(end)).append('\n')
            append(u.text).append("\n\n")
        }
    }

    private fun srtTime(seconds: Double): String {
        val ms = (seconds * 1000).toLong().coerceAtLeast(0)
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val milli = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    /** Returns true on success. Uses MediaStore on API 29+, legacy path below. */
    fun saveToDownloads(context: Context, fileName: String, content: String, mime: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            java.io.File(dir, fileName).writeText(content)
        }
        true
    }.getOrDefault(false)
}
