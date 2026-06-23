package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    modifier: Modifier = Modifier,
) {
    val latest = summaries.lastOrNull()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("LIVE SUMMARY", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LlmActivityPill(activity)
        }
        if (latest == null) {
            Text("Summaries appear here as the broadcast continues.", color = TextSecondary, fontSize = 13.sp)
        } else {
            Text(latest.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
            Text(timeAgo(latest.timestampMillis), color = TextSecondary, fontSize = 11.sp)
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
