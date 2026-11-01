package id.rahmat.marketedge.data.api

import android.text.Html
import android.util.Xml
import id.rahmat.marketedge.domain.model.NewsArticle
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Market headlines from the CNBC Indonesia market RSS feed (Indonesian, with images). */
class MarketNewsClient {
    fun fetchLatest(limit: Int = 40): List<NewsArticle> {
        val xml = Http.get(FEED_URL, accept = "application/rss+xml, application/xml, text/xml")
        return parseItems(xml).take(limit).mapNotNull { it.toArticle() }
    }

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

    private fun Map<String, String>.toArticle(): NewsArticle? {
        val title = this["title"].orEmpty().trim()
        val link = (this["link"] ?: this["guid"]).orEmpty().trim()
        if (title.isBlank() || link.isBlank()) return null
        val description = this["description"].orEmpty()
        val summary = Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ') // image placeholder left by fromHtml
            .replace(Regex("\\s+"), " ")
            .trim()
        val image = this["image"] ?: Regex("<img[^>]+src=\"([^\"]+)\"").find(description)?.groupValues?.get(1)
        return NewsArticle(
            id = link,
            title = title,
            source = SOURCE,
            timeAgo = relativeTime(parseDate(this["pubDate"])),
            category = categoryFor(title, summary),
            summary = summary,
            content = summary,
            imageColor = placeholderColor(title),
            tags = emptyList(),
            sourceUrl = link,
            imageUrl = image?.replace("&amp;", "&")
        )
    }

    private fun parseDate(value: String?): Date? = value?.let {
        runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(it) }.getOrNull()
    }

    companion object {
        const val SOURCE = "CNBC Indonesia"
        private const val FEED_URL = "https://www.cnbcindonesia.com/market/rss"

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
