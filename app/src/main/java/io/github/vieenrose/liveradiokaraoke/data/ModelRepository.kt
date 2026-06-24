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

    // Model files are large (the X-ASR zh/en model is ~128 MB). Use no read/call timeout so a
    // slow mobile connection doesn't abort the download mid-stream; only the connect is bounded.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class Progress(val label: String, val downloadedBytes: Long, val totalBytes: Long, val done: Boolean)

    // --- Readiness ---------------------------------------------------------
    fun asrDir(spec: AsrModelSpec) = File(modelsDir, spec.dirName)

    /**
     * A model is "ready" only if tokens.txt AND all three ONNX files exist and are non-empty.
     * Checking only tokens.txt was a trap: a partial/aborted download can leave tokens.txt but
     * no encoder/decoder/joiner, and handing that to sherpa-onnx crashes the app with a native
     * SIGSEGV in OnlineRecognizer_createStream. A false here forces a clean re-download.
     */
    fun isAsrReady(spec: AsrModelSpec): Boolean {
        val tokens = File(asrDir(spec), "tokens.txt")
        if (!tokens.exists() || tokens.length() == 0L) return false
        val f = resolveAsrFiles(spec)
        return listOf(f.encoder, f.decoder, f.joiner).all { File(it).let { x -> x.exists() && x.length() > 0L } }
    }
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
        // Clear any partial/corrupt prior attempt so we never load a half-extracted model.
        val dir = asrDir(spec)
        dir.deleteRecursively()
        dir.mkdirs()
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
        // 1) Download the .tar.bz2 to a temp file fully (robust to mid-stream hiccups), then extract.
        val tmp = File(dir, ".download.tar.bz2")
        try {
            val req = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $url")
                val total = resp.body?.contentLength() ?: -1L
                var soFar = 0L
                val input = resp.body?.byteStream() ?: error("empty body")
                input.use { inp ->
                    tmp.outputStream().use { o ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            o.write(buf, 0, n); soFar += n
                            onProgress(Progress(label, soFar, total, false))
                        }
                    }
                }
                if (total > 0 && tmp.length() != total)
                    error("incomplete download: ${tmp.length()}/$total bytes")
            }

            // 2) Extract from the completed file.
            TarArchiveInputStream(BZip2CompressorInputStream(tmp.inputStream().buffered())).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val rel = entry.name.substringAfter('/', entry.name)   // strip top-level dir
                    if (rel.isNotBlank() && !entry.isDirectory) {
                        val out = File(dir, rel).apply { parentFile?.mkdirs() }
                        if (!out.canonicalPath.startsWith(dir.canonicalPath)) error("path traversal: ${entry.name}")
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextEntry
                }
            }
        } finally {
            tmp.delete()
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
