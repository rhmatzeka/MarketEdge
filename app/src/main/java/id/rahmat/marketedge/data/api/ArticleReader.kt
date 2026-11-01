package id.rahmat.marketedge.data.api

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL

// Downloads a news page and keeps only its readable paragraphs.

internal fun fetchArticleText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", "Mozilla/5.0 MarketEdge Android")
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
    }
    return try {
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        val html = connection.inputStream.bufferedReader().use { it.readText() }
        extractReadableArticle(html)
    } finally {
        connection.disconnect()
    }
}

internal fun extractReadableArticle(html: String): String {
    val articleHtml = Regex(
        "<article[\\s\\S]*?</article>",
        setOf(RegexOption.IGNORE_CASE)
    ).find(html)?.value ?: html
    val withoutNoise = articleHtml
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<nav[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<footer[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<aside[\\s\\S]*?</aside>", RegexOption.IGNORE_CASE), " ")
    val paragraphs = Regex("<p\\b[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)
        .findAll(withoutNoise)
        .map { htmlToText(it.groupValues[1]) }
        .filter { paragraph ->
            paragraph.length >= 45 &&
                !paragraph.contains("cookie", ignoreCase = true) &&
                !paragraph.contains("subscribe", ignoreCase = true) &&
                !paragraph.contains("advertisement", ignoreCase = true)
        }
        .distinct()
        .take(12)
        .toList()
    if (paragraphs.isEmpty()) error("paragraf artikel tidak ditemukan")
    return paragraphs.joinToString("\n\n")
}

internal fun htmlToText(value: String): String {
    val withBreaks = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(div|section|li|h\\d)>", RegexOption.IGNORE_CASE), "\n")
    return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
