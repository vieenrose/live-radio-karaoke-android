package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vieenrose.liveradiokaraoke.data.PlaybackState
import io.github.vieenrose.liveradiokaraoke.data.Station
import io.github.vieenrose.liveradiokaraoke.ui.theme.*

@Composable
fun PlayerBar(
    state: PlaybackState,
    station: Station?,
    nowPlaying: String?,
    volume: Float,
    onVolume: (Float) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceGlass2)
            .navigationBarsPadding()   // sit above the system nav bar (edge-to-edge)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                station?.name ?: "No station",
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(statusLabel(state), color = statusColor(state), fontSize = 11.sp)
            if (!nowPlaying.isNullOrBlank()) {
                Text("🎵 $nowPlaying", color = StatusLive, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = TextSecondary)
            Slider(value = volume, onValueChange = onVolume, modifier = Modifier.width(96.dp))
        }

        val playing = state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING || state == PlaybackState.CONNECTING
        IconButton(onClick = { if (playing) onStop() else onPlay() }) {
            Icon(
                if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Stop" else "Play",
                tint = if (playing) RedPrimary else StatusLive,
            )
        }
    }
}

private fun statusLabel(s: PlaybackState) = when (s) {
    PlaybackState.STOPPED -> "Stopped"
    PlaybackState.CONNECTING -> "Connecting…"
    PlaybackState.BUFFERING -> "Buffering…"
    PlaybackState.PLAYING -> "Live ●"
    PlaybackState.RECONNECTING -> "Reconnecting…"
    PlaybackState.ERROR -> "Error — press ▶ to retry"
}

private fun statusColor(s: PlaybackState) = when (s) {
    PlaybackState.PLAYING -> StatusLive
    PlaybackState.ERROR -> RedPrimary
    PlaybackState.RECONNECTING, PlaybackState.BUFFERING, PlaybackState.CONNECTING -> StatusWarn
    else -> TextSecondary
}
