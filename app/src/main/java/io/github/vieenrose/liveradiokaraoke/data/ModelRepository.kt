package io.github.vieenrose.liveradiokaraoke.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/** Streamed download/extraction of on-device models (port of app.py download_models_if_needed). */
class ModelRepository(context: Context) {

    private val appContext = context.applicationContext
    val modelsDir = File(appContext.filesDir, "models").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    data class Progress(val label: String, val downloadedBytes: Long, val totalBytes: Long, val done: Boolean)

    // --- Readiness ---------------------------------------------------------
    fun asrDir(spec: AsrModelSpec) = File(modelsDir, spec.dirName)
    fun isAsrReady(spec: AsrModelSpec) = File(asrDir(spec), "tokens.txt").exists()
    fun llmFile(spec: LlmModelSpec) = File(modelsDir, spec.fileName)
    fun isLlmReady(spec: LlmModelSpec) = llmFile(spec).exists() && llmFile(spec).length() > 0

    /** Resolve concrete ONNX paths with the same int8/float fallbacks as config.get_asr_config(). */
    data class AsrFiles(val tokens: String, val encoder: String, val decoder: String, val joiner: String)

    fun resolveAsrFiles(spec: AsrModelSpec): AsrFiles {
        val dir = asrDir(spec)
        fun pick(vararg names: String) = names.map { File(dir, it) }.firstOrNull { it.exists() }?.absolutePath
            ?: File(dir, names.first()).absolutePath
        return AsrFiles(
            tokens = File(dir, "tokens.txt").absolutePath,
            encoder = pick("encoder.int8.onnx", "encoder.onnx"),
            decoder = pick("decoder.onnx", "decoder.int8.onnx"),
            joiner = pick("joiner.int8.onnx", "joiner.onnx"),
        )
    }

    // --- ASR ---------------------------------------------------------------
    suspend fun downloadAsr(spec: AsrModelSpec, onProgress: (Progress) -> Unit) = withContext(Dispatchers.IO) {
        if (isAsrReady(spec)) return@withContext
        val dir = asrDir(spec).apply { mkdirs() }
        when (val src = spec.source) {
            is AsrSource.GithubTar -> downloadAndExtractTar(src.url, dir, spec.key, onProgress)
            is AsrSource.Hf -> downloadHfSnapshot(src.repoId, dir, spec.key, onProgress)
        }
    }

    private fun downloadHfSnapshot(repoId: String, dir: File, label: String, onProgress: (Progress) -> Unit) {
        val treeJson = httpString("https://huggingface.co/api/models/$repoId/tree/main?recursive=true")
        val files = JSONArray(treeJson).let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("type") == "file" }
                .map { it.getString("path") to it.optLong("size") }
        }
        val total = files.sumOf { it.second }
        var soFar = 0L
        for ((path, _) in files) {
            val out = File(dir, path).apply { parentFile?.mkdirs() }
            downloadTo("https://huggingface.co/$repoId/resolve/main/$path", out) { delta ->
                soFar += delta
                onProgress(Progress(label, soFar, total, false))
            }
        }
        onProgress(Progress(label, total, total, true))
    }

    private fun downloadAndExtractTar(url: String, dir: File, label: String, onProgress: (Progress) -> Unit) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            val total = resp.body?.contentLength() ?: -1L
            val src = resp.body?.byteStream() ?: error("empty body")
            var read = 0L
            val counting = object : java.io.FilterInputStream(src) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) { read += n; onProgress(Progress(label, read, total, false)) }
                    return n
                }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(counting)).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    // Strip the leading top-level directory so files land directly in `dir`.
                    val rel = entry.name.substringAfter('/', entry.name)
                    if (rel.isNotBlank() && !entry.isDirectory) {
                        val out = File(dir, rel).apply { parentFile?.mkdirs() }
                        if (!out.canonicalPath.startsWith(dir.canonicalPath)) error("path traversal: ${entry.name}")
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        onProgress(Progress(label, 1, 1, true))
    }

    // --- LLM ---------------------------------------------------------------
    suspend fun downloadLlm(spec: LlmModelSpec, onProgress: (Progress) -> Unit) = withContext(Dispatchers.IO) {
        if (isLlmReady(spec)) return@withContext
        val out = llmFile(spec)
        val tmp = File(out.absolutePath + ".part")
        val url = Config.hfFileUrl(spec.repoId, spec.fileName)
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            val total = resp.body?.contentLength() ?: -1L
            var soFar = 0L
            resp.body?.byteStream()?.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); soFar += n
                        onProgress(Progress(spec.key, soFar, total, false))
                    }
                }
            }
        }
        tmp.renameTo(out)
        onProgress(Progress(spec.key, out.length(), out.length(), true))
    }

    // --- helpers -----------------------------------------------------------
    private fun httpString(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }

    private fun downloadTo(url: String, out: File, onDelta: (Long) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            resp.body?.byteStream()?.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); onDelta(n.toLong())
                    }
                }
            }
        }
    }
}
