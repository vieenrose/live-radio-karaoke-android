package io.github.vieenrose.liveradiokaraoke.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.vieenrose.liveradiokaraoke.data.AsrModelSpec
import io.github.vieenrose.liveradiokaraoke.data.Config
import io.github.vieenrose.liveradiokaraoke.data.ModelRepository
import io.github.vieenrose.liveradiokaraoke.data.Utterance
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Streaming transducer ASR over sherpa-onnx — port of core/asr_service.py.
 * Consumes 16 kHz mono float PCM chunks, emits partial/final [Utterance]s with
 * absolute player-timeline token times for the karaoke highlight.
 */
class AsrEngine(
    private val spec: AsrModelSpec,
    private val files: ModelRepository.AsrFiles,
    private val numThreads: Int,
) {
    private val converter = OpenCcConverter(spec.openCc)

    private fun buildRecognizer(): OnlineRecognizer {
        // Never hand sherpa-onnx an incomplete model — a missing .onnx makes createStream() crash
        // the whole process with a native SIGSEGV. Fail cleanly (caught by the coroutine) instead.
        listOf(files.tokens, files.encoder, files.decoder, files.joiner).forEach {
            val f = java.io.File(it)
            check(f.exists() && f.length() > 0L) { "ASR model file missing/empty: $it" }
        }
        val model = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = files.encoder, decoder = files.decoder, joiner = files.joiner,
            ),
            tokens = files.tokens,
            numThreads = numThreads,
            provider = "cpu",
            modelType = "zipformer2",
        )
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = Config.SAMPLE_RATE, featureDim = 80),
            modelConfig = model,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        return OnlineRecognizer(config = config)
    }

    /**
     * Run the decode loop until [pcm] closes or the coroutine is cancelled.
     * [mediaTimeSeconds] returns the current player position; used to anchor token times.
     */
    suspend fun run(
        pcm: ReceiveChannel<FloatArray>,
        mediaTimeSeconds: () -> Double,
        onUtterance: (Utterance) -> Unit,
        onFinalText: (id: Int, text: String) -> Unit,
    ) {
        val recognizer = buildRecognizer()
        var stream = recognizer.createStream()
        var utteranceId = 0
        var streamStart = mediaTimeSeconds()

        try {
            for (samples in pcm) {
                coroutineContext.ensureActive()
                stream.acceptWaveform(samples, Config.SAMPLE_RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)

                val result = recognizer.getResult(stream)
                val (text, tokens) = converter.apply(result.text, result.tokens.toList())
                val times = result.timestamps.map { streamStart + it }

                val endpoint = recognizer.isEndpoint(stream)
                // Split overly long monologues so translation stays responsive (MAX_SEGMENT_CHARS).
                val isFinal = endpoint || text.length >= Config.MAX_SEGMENT_CHARS

                onUtterance(
                    Utterance(
                        id = utteranceId,
                        tokens = tokens,
                        tokenTimes = times,
                        startTime = streamStart,
                        text = text,
                        isFinal = isFinal,
                    )
                )

                if (isFinal) {
                    if (text.isNotBlank()) onFinalText(utteranceId, text)
                    recognizer.reset(stream)
                    utteranceId++
                    streamStart = mediaTimeSeconds()
                }
            }
        } finally {
            stream.release()
            recognizer.release()
        }
    }

    companion object {
        /** True if the sherpa-onnx native library is present and loadable. */
        fun nativeAvailable(): Boolean = runCatching { System.loadLibrary("sherpa-onnx-jni"); true }
            .getOrDefault(false)
    }
}
