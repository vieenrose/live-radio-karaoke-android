package io.github.vieenrose.liveradiokaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A pass-through Media3 audio processor that taps the decoded PCM stream: it forwards audio
 * to the speaker unchanged while emitting a parallel 16 kHz mono float copy for ASR. This is
 * the on-device equivalent of the Python ffmpeg dual path (playback + s16le@16k mono for ASR).
 */
@UnstableApi
class PcmTapAudioProcessor : BaseAudioProcessor() {

    /** 16 kHz mono float chunks (~100 ms) for the ASR engine. Drop-oldest under back-pressure. */
    val pcmChannel = Channel<FloatArray>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var inputRate = 16000
    private var channels = 1
    private var encoding = C.ENCODING_PCM_16BIT

    private val outChunk = 1600              // 100 ms @ 16 kHz
    private var outBuf = FloatArray(outChunk)
    private var outLen = 0

    // Linear resampler state (input-index space).
    private var step = 1.0
    private var nextPos = 0.0
    private var prevSample = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        step = inputRate.toDouble() / 16000.0
        nextPos = 0.0
        prevSample = 0f
        outLen = 0
        // Output format == input format → playback is untouched.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.limit() - inputBuffer.position()
        if (size <= 0) return

        // Tap a non-destructive view for ASR.
        tap(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))

        // Pass the bytes through to the sink unchanged.
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    private fun tap(buf: ByteBuffer) {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = (buf.limit() - buf.position()) / frameBytes
        if (frames <= 0) return

        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                acc += if (encoding == C.ENCODING_PCM_FLOAT) buf.float else buf.short / 32768f
            }
            mono[f] = acc / channels
        }
        resampleAndEmit(mono)
    }

    private fun resampleAndEmit(m: FloatArray) {
        val n = m.size
        fun sample(i: Int): Float = if (i < 0) prevSample else m[i]
        while (nextPos < n - 1 + 1) {           // need samples i and i+1, with i up to n-1
            val i = kotlin.math.floor(nextPos).toInt()
            if (i + 1 > n - 1) break
            val frac = (nextPos - i).toFloat()
            val a = sample(i); val b = sample(i + 1)
            emit(a + (b - a) * frac)
            nextPos += step
        }
        prevSample = m[n - 1]
        nextPos -= n
    }

    private fun emit(s: Float) {
        outBuf[outLen++] = s
        if (outLen == outChunk) {
            pcmChannel.trySend(outBuf.copyOf())
            outLen = 0
        }
    }

    override fun onReset() {
        outLen = 0; nextPos = 0.0; prevSample = 0f
    }
}
