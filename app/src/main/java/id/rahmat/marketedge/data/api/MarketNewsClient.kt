package id.rahmat.marketedge.data.api

import android.text.Html
import android.util.Xml
import id.rahmat.marketedge.domain.model.NewsArticle
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * Indonesian market headlines (with images) from the CNBC Indonesia and ANTARA RSS
 * feeds, merged newest first. One feed refusing the request (CNBC blocks some
 * networks) still leaves the other.
 */
class MarketNewsClient {
    fun fetchLatest(limit: Int = 40): List<NewsArticle> {
        val results = FEEDS.map { (source, url) ->
            CompletableFuture.supplyAsync {
                runCatching { parseFeed(Http.get(url, accept = "application/rss+xml, application/xml, text/xml"), source) }
            }
        }.map { it.join() }
        val items = results.flatMap { it.getOrDefault(emptyList()) }
        if (items.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IllegalStateException("Berita kosong")
        }
        return items.sortedByDescending { it.published?.time ?: 0L }.take(limit).map { it.article }
    }

    internal fun parse(xml: String, source: String = SOURCE, limit: Int = 40): List<NewsArticle> =
        parseFeed(xml, source).take(limit).map { it.article }

    private class Item(val article: NewsArticle, val published: Date?)

    private fun parseFeed(xml: String, source: String): List<Item> =
        parseItems(xml).mapNotNull { it.toItem(source) }

    private fun parseItems(xml: String): List<Map<String, String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val items = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (val tag = parser.name) {
                    "item" -> current = mutableMapOf()
                    "enclosure", "media:content" -> parser.getAttributeValue(null, "url")?.let { url ->
                        current?.putIfAbsent("image", url)
                    }
                    "title", "link", "guid", "pubDate", "description" -> current?.let { item ->
                        item[tag] = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    current?.let(items::add)
                    current = null
                }
            }
        }
        return items
    }

    private fun Map<String, String>.toItem(source: String): Item? {
        val title = this["title"].orEmpty().trim()
        val link = (this["link"] ?: this["guid"]).orEmpty().trim()
        if (title.isBlank() || link.isBlank()) return null
        val description = this["description"].orEmpty()
        val summary = Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ') // image placeholder left by fromHtml
            .replace(Regex("\\s+"), " ")
            .trim()
        val image = this["image"] ?: Regex("<img[^>]+src=\"([^\"]+)\"").find(description)?.groupValues?.get(1)
        val published = parseDate(this["pubDate"])
        return Item(NewsArticle(
            id = link,
            title = title,
            source = source,
            timeAgo = relativeTime(published),
            category = categoryFor(title, summary),
            summary = summary,
            content = summary,
            imageColor = placeholderColor(title),
            tags = emptyList(),
            sourceUrl = link,
            imageUrl = image?.replace("&amp;", "&")
        ), published)
    }

    private fun parseDate(value: String?): Date? = value?.let {
        runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(it) }.getOrNull()
    }

    companion object {
        const val SOURCE = "CNBC Indonesia"
        const val ANTARA = "ANTARA"
        private val FEEDS = listOf(
            SOURCE to "https://www.cnbcindonesia.com/market/rss",
            ANTARA to "https://www.antaranews.com/rss/ekonomi-bursa.xml"
        )

        val CATEGORIES = listOf("Saham", "Kripto", "Forex & Emas", "Komoditas", "Global", "Ekonomi")

        /** Picks a category from whole words in the headline and summary. */
        fun categoryFor(title: String, summary: String): String {
            val text = " " + "$title $summary".lowercase(ID_LOCALE).replace(Regex("[^a-z0-9]+"), " ") + " "
            fun has(vararg words: String) = words.any { text.contains(" $it ") }
            return when {
                has("kripto", "crypto", "bitcoin", "btc", "ethereum", "blockchain", "stablecoin", "solana") -> "Kripto"
                has("rupiah", "dolar", "dollar", "kurs", "valas", "emas", "perak", "yen", "euro", "yuan", "forex") -> "Forex & Emas"
                has("minyak", "batu bara", "batubara", "cpo", "sawit", "nikel", "timah", "tembaga", "komoditas", "gas") -> "Komoditas"
                has("saham", "ihsg", "emiten", "bei", "bursa", "ipo", "dividen", "lq45", "reksa dana") -> "Saham"
                has("wall street", "the fed", "fed", "as", "amerika", "china", "eropa", "jepang", "global", "trump") -> "Global"
                else -> "Ekonomi"
            }
        }
    }
}
