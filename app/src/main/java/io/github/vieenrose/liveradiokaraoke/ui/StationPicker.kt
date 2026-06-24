package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.vieenrose.liveradiokaraoke.data.RadioBrowserApi
import io.github.vieenrose.liveradiokaraoke.data.Station
import io.github.vieenrose.liveradiokaraoke.ui.theme.*
import kotlin.math.absoluteValue

private val TABS = listOf("Favorites", "Discover", "News", "Popular")
private val LANG_SECTIONS = listOf("en" to "English", "fr" to "Français", "zh" to "中文")

@Composable
fun StationPicker(
    bundled: List<Station>,
    discovered: List<Station>,
    onDiscover: (search: String, country: String, language: String, category: RadioBrowserApi.Category) -> Unit,
    onPick: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }

    LaunchedEffect(tab) {
        when (tab) {
            2 -> onDiscover("", "", "", RadioBrowserApi.Category.NEWS)
            3 -> onDiscover("", "", "", RadioBrowserApi.Category.POPULAR)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(BlueDark)
                .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Choose a station", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = onDismiss) { Text("✕", color = TextSecondary, fontSize = 18.sp) }
            }

            // Pill tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TABS.forEachIndexed { i, t ->
                    val sel = tab == i
                    Text(
                        t, fontSize = 13.sp, color = if (sel) Color.White else TextSecondary,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Accent else SurfaceGlass)
                            .clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            if (tab == 1) {
                OutlinedTextField(search, { search = it }, label = { Text("Search name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(country, { country = it }, label = { Text("Country (US)") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(language, { language = it }, label = { Text("Language") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                Button(onClick = { onDiscover(search, country, language, RadioBrowserApi.Category.POPULAR) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Search") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                if (tab == 0) {
                    LANG_SECTIONS.forEach { (code, label) ->
                        val group = bundled.filter { it.language == code }
                        if (group.isNotEmpty()) {
                            item(key = "h_$code") {
                                Text(label.uppercase(), color = TextSecondary, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                            }
                            items(group, key = { it.name + it.url }) { StationItem(it) { onPick(it); onDismiss() } }
                        }
                    }
                } else {
                    items(discovered, key = { it.name + it.url }) { StationItem(it) { onPick(it); onDismiss() } }
                }
            }
        }
    }
}

@Composable
private fun StationItem(st: Station, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationAvatar(st)
        Column(Modifier.weight(1f)) {
            Text(st.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                st.country.ifBlank { null },
                st.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                st.tags.split(",").firstOrNull()?.trim()?.ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LanguageBadge(st.language, st.isFallbackLanguage)
    }
}

@Composable
private fun StationAvatar(st: Station) {
    val initial = st.name.trim().firstOrNull { it.isLetterOrDigit() }?.toString()?.uppercase() ?: "♪"
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(avatarColor(st.name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (st.favicon.isNotBlank()) {
            AsyncImage(
                model = st.favicon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFF2E9BFF), Color(0xFF49E08B), Color(0xFFFF8A5C),
    Color(0xFFB07BFF), Color(0xFFFFCE5C), Color(0xFF4EC6E0), Color(0xFFFF6F91),
)

private fun avatarColor(name: String): Color = AVATAR_COLORS[name.hashCode().absoluteValue % AVATAR_COLORS.size]

@Composable
private fun LanguageBadge(language: String, fallback: Boolean) {
    val supported = language in setOf("en", "fr", "zh")
    val c = if (supported) StatusLive else RedPrimary
    Text(
        language.uppercase() + if (fallback) "*" else "",
        color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
