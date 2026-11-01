package id.rahmat.marketedge.data.api

import java.net.HttpURLConnection
import java.net.URL

/** Small blocking HTTP GET used by every API client. Call it off the main thread. */
internal object Http {
    fun get(
        url: String,
        accept: String = "application/json",
        userAgent: String = "MarketEdge Android",
        timeoutMs: Int = 10_000
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
