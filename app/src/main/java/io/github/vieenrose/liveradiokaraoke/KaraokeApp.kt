package io.github.vieenrose.liveradiokaraoke

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class KaraokeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PLAYBACK, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    companion object { const val CHANNEL_PLAYBACK = "playback" }
}
