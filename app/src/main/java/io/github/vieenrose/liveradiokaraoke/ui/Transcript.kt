package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vieenrose.liveradiokaraoke.data.Utterance
import io.github.vieenrose.liveradiokaraoke.ui.theme.*

private data class Highlight(val utteranceId: Int, val tokenIndex: Int)

/**
 * Live transcript with karaoke highlighting. A per-frame loop maps the player position
 * (minus the user sync offset) onto absolute token times — port of the web client's
 * requestAnimationFrame updateHighlights().
 */
@Composable
fun TranscriptView(
    utterances: List<Utterance>,
    syncOffset: Float,
    positionSeconds: () -> Double,
    modifier: Modifier = Modifier,
) {
    val uttState = rememberUpdatedState(utterances)
    val offsetState = rememberUpdatedState(syncOffset)
    var highlight by remember { mutableStateOf(Highlight(-1, -1)) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val now = positionSeconds() - offsetState.value
            val list = uttState.value
            val utt = list.lastOrNull { it.startTime <= now }
            if (utt == null) { highlight = Highlight(-1, -1); continue }
            var tokenIdx = -1
            for (i in utt.tokenTimes.indices) {
                if (utt.tokenTimes[i] <= now) tokenIdx = i else break
            }
            highlight = Highlight(utt.id, tokenIdx)
        }
    }

    val listState = rememberLazyListState()
    // Bottom-anchored, chat-style: keep the newest utterance pinned to the bottom as it grows AND
    // when a new one arrives. Keyed on the last utterance's id + token count (not list size, which
    // plateaus at the 20-item cap) so it keeps following past 20 utterances.
    val last = utterances.lastOrNull()
    LaunchedEffect(last?.id, last?.tokens?.size, last?.translation) {
        if (utterances.isNotEmpty()) runCatching { listState.animateScrollToItem(0) }
    }

    if (utterances.isEmpty()) {
        Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                "Press ▶ to start — live transcription will appear here.",
                color = TextSecondary, fontSize = 16.sp,
            )
        }
        return
    }

    // reverseLayout keeps content anchored to the bottom (newest first in the reversed list).
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(utterances.asReversed(), key = { it.id }) { u ->
            UtteranceRow(u, if (u.id == highlight.utteranceId) highlight.tokenIndex else -1)
        }
    }
}

@Composable
private fun UtteranceRow(u: Utterance, activeToken: Int) {
    Column {
        Text(
            text = buildKaraoke(u, activeToken),
            fontSize = 22.sp,
            lineHeight = 30.sp,
            color = if (u.summarized) StatusWarn.copy(alpha = 0.95f) else TextPrimary.copy(alpha = 0.85f),
        )
        if (u.translation.isNotBlank()) {
            Text(
                text = u.translation + if (u.translationStreaming) " …" else "",
                fontSize = 16.sp,
                color = Accent,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp),
            )
        }
    }
}

private fun buildKaraoke(u: Utterance, activeToken: Int): AnnotatedString = buildAnnotatedString {
    u.tokens.forEachIndexed { i, tok ->
        val active = i == activeToken
        withStyle(
            SpanStyle(
                color = if (active) androidx.compose.ui.graphics.Color.White else TextPrimary.copy(alpha = 0.85f),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        ) { append(tok) }
    }
}
