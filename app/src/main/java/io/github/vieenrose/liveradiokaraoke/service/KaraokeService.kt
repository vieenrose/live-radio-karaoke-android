package io.github.vieenrose.liveradiokaraoke.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground MediaSessionService — hosts playback for lock-screen / background and keeps the
 * process alive so on-device transcription continues with the screen off.
 *
 * v1 ships this as the wired-up media-session host; the full pipeline (ASR + LLM) currently runs
 * in [io.github.vieenrose.liveradiokaraoke.vm.KaraokeViewModel]. Consolidating the
 * RadioStreamController into this service is the next step for true background transcription
 * (see README → "Background playback").
 */
class KaraokeService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
