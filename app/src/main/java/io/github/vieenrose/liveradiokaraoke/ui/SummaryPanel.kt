package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vieenrose.liveradiokaraoke.data.LlmActivity
import io.github.vieenrose.liveradiokaraoke.data.SummaryItem
import io.github.vieenrose.liveradiokaraoke.ui.theme.*

@Composable
fun SummaryPanel(
    summaries: List<SummaryItem>,
    activity: LlmActivity,
    sttLabel: String,
    llmLabel: String,
    modifier: Modifier = Modifier,
) {
    val latest = summaries.lastOrNull()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Fixed header — always visible regardless of summary length.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("LIVE SUMMARY", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            LlmActivityPill(activity)
        }
        // Fixed engine-info + timestamp.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("STT $sttLabel · LLM $llmLabel", color = TextSecondary, fontSize = 11.sp)
            if (latest != null) Text(timeAgo(latest.timestampMillis), color = TextSecondary, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderGlass))
        // Only the summary text scrolls (bounded) so it never masks the info above.
        if (latest == null) {
            Text("Summaries appear here as the broadcast continues.", color = TextSecondary, fontSize = 13.sp)
        } else {
            Text(
                latest.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp,
                modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun LlmActivityPill(activity: LlmActivity) {
    val (label, color) = when (activity) {
        LlmActivity.IDLE -> "Idle" to TextSecondary
        LlmActivity.SUMMARIZING -> "Summarizing" to StatusLive
        LlmActivity.TRANSLATING -> "Translating" to StatusLive
    }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(SurfaceGlass2).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Text(label, color = color, fontSize = 11.sp)
    }
}

private fun timeAgo(millis: Long): String {
    val secs = ((System.currentTimeMillis() - millis) / 1000).coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} min ago"
        else -> "${secs / 3600} h ago"
    }
}
