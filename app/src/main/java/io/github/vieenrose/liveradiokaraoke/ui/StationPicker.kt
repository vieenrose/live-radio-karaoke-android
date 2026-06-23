package io.github.vieenrose.liveradiokaraoke.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.vieenrose.liveradiokaraoke.data.RadioBrowserApi
import io.github.vieenrose.liveradiokaraoke.data.Station
import io.github.vieenrose.liveradiokaraoke.ui.theme.*

private val TABS = listOf("Favorites", "Discover", "News", "Popular")

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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp)).background(BlueDark).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose a station", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TabRow(selectedTabIndex = tab, containerColor = SurfaceGlass) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) }) }
            }

            if (tab == 1) {
                OutlinedTextField(search, { search = it }, label = { Text("Search name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(country, { country = it }, label = { Text("Country (US)") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(language, { language = it }, label = { Text("Language") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                }
                Button(onClick = { onDiscover(search, country, language, RadioBrowserApi.Category.POPULAR) },
                    modifier = Modifier.fillMaxWidth()) { Text("Search") }
            }

            val list = if (tab == 0) bundled else discovered
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(list, key = { it.name + it.url }) { st -> StationItem(st) { onPick(st); onDismiss() } }
            }
        }
    }
}

@Composable
private fun StationItem(st: Station, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceGlass)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(st.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val sub = listOfNotNull(st.country.ifBlank { null }, st.bitrate.takeIf { it > 0 }?.let { "$it kbps" })
                .joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 11.sp)
        }
        LanguageBadge(st.language, st.isFallbackLanguage)
    }
}

@Composable
private fun LanguageBadge(language: String, fallback: Boolean) {
    val supported = language in setOf("en", "fr", "zh")
    val bg = if (supported) StatusLive.copy(alpha = 0.25f) else RedPrimary.copy(alpha = 0.25f)
    Text(
        language.uppercase() + if (fallback) "*" else "",
        color = if (supported) StatusLive else RedPrimary,
        fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
