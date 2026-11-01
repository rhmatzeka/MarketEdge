package id.rahmat.marketedge.data.ai

import id.rahmat.marketedge.data.api.formatPercent
import id.rahmat.marketedge.domain.model.ChatMessage
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.NewsArticle
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * WarrenAI answers. Uses an OpenAI-compatible chat endpoint when [apiKey] is set and
 * falls back to a local summary of the loaded prices and headlines otherwise.
 * [answer] blocks on the network: call it off the main thread.
 */
class WarrenAi(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    fun answer(query: String, assets: List<MarketAsset>, news: List<NewsArticle>): ChatMessage {
        if (apiKey.isBlank()) return localAnswer(query, assets, news)
        val response = runCatching { requestCompletion(query, assets, news) }.getOrElse {
            return localAnswer(query, assets, news, friendlyError(it.message.orEmpty()))
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = sanitize(response),
            timestamp = "Baru saja",
            fromUser = false,
            recommendations = assets.sortedByDescending { kotlin.math.abs(it.changePercent) }.take(3),
            relatedNews = news.take(2)
        )
    }

    fun localAnswer(query: String, assets: List<MarketAsset>, news: List<NewsArticle>, notice: String? = null): ChatMessage {
        val strongest = assets.maxByOrNull { it.changePercent }
        val weakest = assets.minByOrNull { it.changePercent }
        val latest = news.firstOrNull()
        val titleFromQuestion = Regex("\"([^\"]+)\"").find(query)?.groupValues?.getOrNull(1)
        val focusTitle = titleFromQuestion ?: latest?.title
        val text = buildString {
            if (notice != null) {
                append(notice)
                append("\n\n")
            } else {
                append("Saya pakai data market dan headline yang sudah dimuat aplikasi.\n\n")
            }
            append("Kesimpulan:\n")
            append(
                if (focusTitle != null) {
                    "Berita \"$focusTitle\" lebih dulu dibaca sebagai pemicu sentimen. Dampaknya ke harga belum otomatis pasti, jadi perlu dicek bersama pergerakan aset.\n\n"
                } else {
                    "Belum ada headline yang cukup spesifik. Analisis ini memakai pergerakan aset terbaru sebagai konteks awal.\n\n"
                }
            )
            append("Aset yang perlu dipantau:\n")
            if (strongest != null && weakest != null) {
                append("${strongest.symbol} sedang paling kuat (${formatPercent(strongest.changePercent)}). ")
                append("${weakest.symbol} sedang paling lemah (${formatPercent(weakest.changePercent)}).\n\n")
            } else {
                append("Data aset masih dimuat, jadi belum bisa menentukan penggerak terkuat dan terlemah.\n\n")
            }
            append("Cara membacanya:\n")
            append("Kalau berita memengaruhi minat risiko global, aset berisiko seperti kripto bisa ikut bergerak. Kalau beritanya hanya spesifik sektor, efeknya bisa kecil atau terlambat.\n\n")
            append("Risiko:\n")
            append("Jangan anggap satu headline sebagai penyebab tunggal. Cocokkan dengan volume, arah market utama, dan berita lanjutan. Ini ringkasan awal, bukan saran investasi personal.")
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = sanitize(text),
            timestamp = "Baru saja",
            fromUser = false,
            recommendations = assets.sortedByDescending { kotlin.math.abs(it.changePercent) }.take(3),
            relatedNews = news.take(2)
        )
    }

    private fun requestCompletion(query: String, assets: List<MarketAsset>, news: List<NewsArticle>): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Kamu adalah analis market MarketEdge. Jawab dalam Bahasa Indonesia yang jelas dan ringkas. Jangan pakai markdown, tanda bintang, bullet simbol, tabel, emoji, atau format tebal. Gunakan label teks biasa: Kesimpulan, Dampak, Aset yang dipantau, Risiko. Maksimal 5 paragraf pendek. Jelaskan hubungan berita ke aset dengan hati-hati dan jangan memberi saran investasi personal.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", buildContext(query, assets, news))
                })
            })
            put("temperature", 0.4)
        }
        val connection = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${apiKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error(parseAiError(connection.responseCode, body))
            sanitize(JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim())
        } finally {
            connection.disconnect()
        }
    }

    internal fun sanitize(value: String): String {
        val withoutMarkdown = value
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
            .replace(Regex("""__(.*?)__"""), "$1")
            .replace(Regex("""`([^`]*)`"""), "$1")
            .replace("*", "")
            .replace("#", "")
            .replace("•", "")
        return withoutMarkdown
            .lines()
            .map { line ->
                line.trim()
                    .replace(Regex("""^[-]+[\s]+"""), "")
                    .replace(Regex("""^[0-9]+[.)][\s]*"""), "")
            }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    private fun parseAiError(code: Int, body: String): String {
        val parsed = runCatching {
            val error = JSONObject(body).getJSONObject("error")
            val type = error.optString("type")
            val message = error.optString("message")
            when {
                type == "insufficient_quota" -> "Kuota AI habis atau billing belum aktif."
                message.isNotBlank() -> message
                else -> "AI HTTP $code"
            }
        }.getOrDefault("AI HTTP $code")
        return parsed
    }

    fun friendlyError(message: String): String = when {
        message.contains("insufficient_quota", ignoreCase = true) ||
            message.contains("Kuota AI habis", ignoreCase = true) ->
            "Layanan AI sedang tidak tersedia. Saya pakai analisis lokal dari data aplikasi."
        message.contains("429") ->
            "AI sedang membatasi request atau kuota akun habis."
        message.contains("401") || message.contains("invalid", ignoreCase = true) ->
            "Layanan AI belum bisa dipakai. Saya pakai analisis lokal dari data aplikasi."
        message.isBlank() ->
            "AI online belum bisa dihubungi."
        else ->
            "AI online belum bisa dipakai: ${message.take(120)}"
    }

    private fun buildContext(query: String, assets: List<MarketAsset>, news: List<NewsArticle>): String = buildString {
        appendLine("Pertanyaan user: $query")
        appendLine("Format jawaban wajib:")
        appendLine("Kesimpulan: satu kalimat paling penting.")
        appendLine("Dampak: jelaskan pengaruh berita ke sentimen atau aset secara hati-hati.")
        appendLine("Aset yang dipantau: sebutkan aset relevan dari data aplikasi.")
        appendLine("Risiko: sebutkan batasan analisis. Tanpa markdown, tanpa tanda bintang, tanpa bullet simbol.")
        appendLine()
        appendLine("Data market terbaru:")
        assets.take(20).forEach {
            appendLine("- ${it.symbol} ${it.name}: ${it.price}, perubahan ${formatPercent(it.changePercent)}, kategori ${it.category}")
        }
        appendLine()
        appendLine("Headline berita terbaru:")
        news.take(8).forEach {
            appendLine("- ${it.title} (${it.source}, ${it.timeAgo}) ringkasan: ${it.summary}")
        }
    }
}
