package io.github.vieenrose.liveradiokaraoke.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.extractor.metadata.icy.IcyInfo
import io.github.vieenrose.liveradiokaraoke.data.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ExoPlayer-backed radio playback with a PCM tap for ASR. Replaces the Python
 * AudioStreamer (aiohttp fetch + ffmpeg). One decode path serves both speaker and ASR.
 */
@UnstableApi
class RadioStreamController(private val context: Context) {

    val tap = PcmTapAudioProcessor()
    val playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val nowPlaying = MutableStateFlow<String?>(null)

    var player: ExoPlayer? = null
        private set

    fun ensurePlayer(): ExoPlayer = player ?: build().also { player = it }

    private fun build(): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(tap))
                .build()
        }.setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build().apply { addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            playbackState.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> PlaybackState.PLAYING
                Player.STATE_IDLE -> PlaybackState.STOPPED
                else -> playbackState.value
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackState.value = PlaybackState.ERROR
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                (metadata[i] as? IcyInfo)?.title?.takeIf { it.isNotBlank() }?.let {
                    nowPlaying.value = it
                }
            }
        }
    }

    fun play(url: String) {
        val p = ensurePlayer()
        playbackState.value = PlaybackState.CONNECTING
        nowPlaying.value = null
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    fun stop() {
        player?.run { playWhenReady = false; stop() }
        playbackState.value = PlaybackState.STOPPED
    }

    /** Current playback position, seconds — anchors ASR token times. */
    fun positionSeconds(): Double = (player?.currentPosition ?: 0L) / 1000.0

    fun setVolume(v: Float) { player?.volume = v.coerceIn(0f, 1f) }

    fun release() {
        player?.release(); player = null
        tap.pcmChannel.close()
    }
}
