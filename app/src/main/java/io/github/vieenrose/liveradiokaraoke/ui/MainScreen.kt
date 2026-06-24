package io.github.vieenrose.liveradiokaraoke.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.github.vieenrose.liveradiokaraoke.export.TranscriptExporter
import io.github.vieenrose.liveradiokaraoke.ui.theme.BlueDark
import io.github.vieenrose.liveradiokaraoke.vm.DownloadState
import io.github.vieenrose.liveradiokaraoke.vm.KaraokeViewModel

private val LANGUAGES = listOf(
    "Off" to null, "English" to "English", "French" to "French", "Chinese" to "Chinese",
    "Spanish" to "Spanish", "German" to "German", "Japanese" to "Japanese",
    "Korean" to "Korean", "Portuguese" to "Portuguese", "Arabic" to "Arabic",
)

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: KaraokeViewModel) {
    val context = LocalContext.current
    val playback by vm.playbackState.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()
    val station by vm.currentStation.collectAsState()
    val utterances by vm.utterances.collectAsState()
    val summaries by vm.summaries.collectAsState()
    val llmActivity by vm.llmActivity.collectAsState()
    val volume by vm.volume.collectAsState()
    val sync by vm.userSyncOffset.collectAsState()
    val download by vm.download.collectAsState()
    val dlVisible by vm.downloadDialogVisible.collectAsState()
    val sttLabel by vm.sttModelLabel.collectAsState()
    val llmLabel by vm.llmModelLabel.collectAsState()
    val discovered by vm.discovered.collectAsState()
    val target by vm.targetLanguage.collectAsState()

    var showStations by rememberSaveable { mutableStateOf(false) }
    var showDebug by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = BlueDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlueDark),
                title = { Text("Live Radio Karaoke", fontSize = 18.sp) },
                actions = {
                    TranslateMenu(target) { vm.setTargetLanguage(it) }
                    ExportMenu { srt ->
                        val items = vm.sessionForExport()
                        val (content, name, mime) = if (srt)
                            Triple(TranscriptExporter.asSrt(items), "transcript.srt", "application/x-subrip")
                        else Triple(TranscriptExporter.asText(items), "transcript.txt", "text/plain")
                        val ok = TranscriptExporter.saveToDownloads(context, name, content, mime)
                        Toast.makeText(context, if (ok) "Saved to Downloads" else "Export failed", Toast.LENGTH_SHORT).show()
                    }
                    IconButton(onClick = { showStations = true }) {
                        Icon(Icons.Filled.Radio, contentDescription = "Change station")
                    }
                    IconButton(onClick = { showDebug = true }) { Text("?", fontSize = 18.sp) }
                },
            )
        },
        bottomBar = {
            PlayerBar(
                state = playback, station = station, nowPlaying = nowPlaying, volume = volume,
                onVolume = vm::setVolume,
                onPlay = { station?.let { vm.selectAndPlay(it) } ?: run { showStations = true } },
                onStop = vm::stop,
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            val wide = maxWidth > 720.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TranscriptView(utterances, sync, { vm.controller.positionSeconds() }, Modifier.weight(1f).fillMaxHeight())
                    SummaryPanel(summaries, llmActivity, sttLabel, llmLabel, Modifier.width(320.dp).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TranscriptView(utterances, sync, { vm.controller.positionSeconds() }, Modifier.weight(1f).fillMaxWidth())
                    SummaryPanel(summaries, llmActivity, sttLabel, llmLabel, Modifier.fillMaxWidth().heightIn(max = 240.dp))
                }
            }
        }
    }

    if (showStations) {
        StationPicker(
            bundled = vm.bundledStations, discovered = discovered,
            onDiscover = vm::discover,
            onPick = { vm.selectAndPlay(it) },
            onDismiss = { showStations = false },
        )
    }

    when (val d = download) {
        is DownloadState.Running -> if (dlVisible) DownloadDialog(d) { vm.downloadDialogVisible.value = false }
        is DownloadState.NeedGemmaConsent -> GemmaConsentDialog(
            onAccept = { vm.acceptGemmaTermsAndContinue() },
            onUseOpen = { vm.useOpenModelInstead() },
            onDismiss = { vm.download.value = DownloadState.Idle },
        )
        is DownloadState.Error -> AlertDialog(
            onDismissRequest = { vm.download.value = DownloadState.Idle },
            confirmButton = { TextButton(onClick = { vm.download.value = DownloadState.Idle }) { Text("OK") } },
            title = { Text("Download failed") }, text = { Text(d.message) },
        )
        else -> {}
    }

    if (showDebug) {
        ModalBottomSheet(onDismissRequest = { showDebug = false }, containerColor = BlueDark) {
            DebugSheet(sync, vm::setSyncOffset, vm.deviceTier.name, vm.nativeAvailable)
        }
    }
}

@Composable
private fun TranslateMenu(current: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Language, contentDescription = "Translate") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label + if (value == current || (value == null && current == null)) "  ✓" else "") },
                    onClick = { onPick(value); open = false },
                )
            }
        }
    }
}

@Composable
private fun ExportMenu(onExport: (srt: Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Download, contentDescription = "Export") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Export .txt") }, onClick = { onExport(false); open = false })
            DropdownMenuItem(text = { Text("Export .srt") }, onClick = { onExport(true); open = false })
        }
    }
}
