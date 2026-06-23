package io.github.vieenrose.liveradiokaraoke.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Radio Browser API client — port of api/radio_browser.py.
 * Dynamic station discovery + per-station language detection with ASR fallback.
 */
class RadioBrowserApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
) {
    private val base = "https://de1.api.radio-browser.info/json"
    private val ua = "LiveRadioKaraoke/1.0 (+https://github.com/vieenrose/live-radio-karaoke-android)"

    enum class Category(val tag: String?) { POPULAR(null), NEWS("news"), MUSIC("music"), TALK("talk") }

    suspend fun discover(
        search: String = "",
        country: String = "",
        language: String = "",
        category: Category = Category.POPULAR,
        limit: Int = 50,
    ): List<Station> = withContext(Dispatchers.IO) {
        val url = if (search.isBlank() && country.isBlank() && language.isBlank() && category == Category.POPULAR) {
            "$base/stations/topclick/$limit"
        } else {
            val q = buildList {
                if (search.isNotBlank()) add("name=${enc(search)}")
                if (country.isNotBlank()) add("countrycode=${enc(country)}")
                if (language.isNotBlank()) add("language=${enc(language)}")
                category.tag?.let { add("tag=${enc(it)}") }
                add("limit=$limit"); add("hidebroken=true"); add("order=clickcount"); add("reverse=true")
            }.joinToString("&")
            "$base/stations/search?$q"
        }
        runCatching { fetch(url) }.getOrDefault(emptyList())
    }

    private fun fetch(url: String): List<Station> {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return emptyList()
            val arr = JSONArray(body)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val streamUrl = o.optString("url_resolved").ifBlank { o.optString("url") }
                if (streamUrl.isBlank()) return@mapNotNull null
                val detected = detectLanguage(
                    name = o.optString("name").lowercase(),
                    country = o.optString("countrycode").uppercase(),
                    langTag = o.optString("language").lowercase(),
                    tags = o.optString("tags").lowercase(),
                )
                val (asrLang, fallback) = Config.resolveAsrLanguage(detected)
                Station(
                    name = o.optString("name").trim().ifBlank { "Unknown" },
                    url = streamUrl,
                    language = asrLang,
                    detectedLanguage = detected,
                    isFallbackLanguage = fallback,
                    homepage = o.optString("homepage"),
                    favicon = o.optString("favicon"),
                    country = o.optString("country"),
                    bitrate = o.optInt("bitrate"),
                    tags = o.optString("tags"),
                )
            }
        }
    }

    // Condensed port of RadioBrowserAPI._detect_language.
    private val countryToLang = buildMap {
        listOf("CN", "TW", "HK", "SG").forEach { put(it, "zh") }
        listOf("FR", "BE", "CH", "CA", "LU", "MC").forEach { put(it, "fr") }
    }
    private val keywordToLang = mapOf(
        "chinese" to "zh", "mandarin" to "zh", "中文" to "zh", "taiwan" to "zh", "華語" to "zh",
        "french" to "fr", "français" to "fr", "francais" to "fr",
    )

    private fun detectLanguage(name: String, country: String, langTag: String, tags: String): String {
        val hay = "$name $langTag $tags"
        keywordToLang.forEach { (k, v) -> if (hay.contains(k)) return v }
        countryToLang[country]?.let { return it }
        return "en"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
