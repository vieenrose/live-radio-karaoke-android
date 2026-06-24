package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vieenrose.liveradiokaraoke.ui.theme.TextPrimary
import io.github.vieenrose.liveradiokaraoke.ui.theme.TextSecondary
import io.github.vieenrose.liveradiokaraoke.vm.DownloadState

@Composable
fun DownloadDialog(state: DownloadState.Running, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listen while it downloads") } },
        title = { Text("Setting up transcription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Radio is already playing. ${state.label} is downloading once.",
                    color = TextSecondary, fontSize = 13.sp)
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                Text("${(state.fraction * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                Text("Models stay on your device for next time.", color = TextSecondary, fontSize = 11.sp)
            }
        },
    )
}

/** Non-free Gemma terms must be accepted before download (keeps the app itself FOSS). */
@Composable
fun GemmaConsentDialog(onAccept: () -> Unit, onUseOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemma model terms") },
        text = {
            Text(
                "The on-device summary & translation model is Google's Gemma 3 1B, distributed under " +
                    "the Gemma Terms of Use (a non-free licence). It is downloaded from Hugging Face and " +
                    "never bundled with this app. You may instead use a fully-open model.",
                color = TextSecondary, fontSize = 13.sp,
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Accept & download") } },
        dismissButton = { TextButton(onClick = onUseOpen) { Text("Use an open model") } },
    )
}

@Composable
fun DebugSheet(syncOffset: Float, onSync: (Float) -> Unit, deviceTier: String, native: Boolean) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug", color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("Sync offset: ${"%.2f".format(syncOffset)} s", color = TextSecondary, fontSize = 12.sp)
        Slider(value = syncOffset, onValueChange = onSync, valueRange = -3f..3f)
        Text("Device tier: $deviceTier", color = TextSecondary, fontSize = 12.sp)
        Text(if (native) "On-device AI: enabled" else "On-device AI: not built (audio-only)", color = TextSecondary, fontSize = 12.sp)
    }
}
