package id.rahmat.marketedge.data.api

import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.MarketDetailStats
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Stocks, indices, index futures and energy commodities from Yahoo Finance's public
 * chart endpoints (no API key).
 */
class YahooFinanceClient {
    fun fetchMarkets(): List<MarketAsset> {
        val chunks = SPECS.chunked(SPARK_BATCH).map { chunk -> runCatching { fetchSpark(chunk) } }
        val assets = chunks.flatMap { it.getOrDefault(emptyList()) }
        if (assets.isEmpty()) chunks.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        return assets
    }

    fun fetchChart(asset: MarketAsset, days: Int): List<Float> {
        val ticker = asset.chartId ?: return asset.sparkline
        val (range, interval) = when {
            days <= 1 -> "1d" to "5m"
            days <= 7 -> "5d" to "30m"
            days <= 31 -> "1mo" to "1d"
            days <= 366 -> "1y" to "1d"
            days <= 5 * 366 -> "5y" to "1wk"
            else -> "max" to "1mo"
        }
        val result = chartResult(ticker, range, interval)
        return closes(result).map { it.toFloat() }.downsample(MAX_CHART_POINTS)
    }

    fun fetchDetailStats(asset: MarketAsset): MarketDetailStats {
        val ticker = asset.chartId ?: error("Ticker tidak tersedia")
        val prefix = specFor(ticker)?.prefix.orEmpty()
        val result = chartResult(ticker, "1d", "5m")
        val meta = result.getJSONObject("meta")
        val firstOpen = result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)
            ?.optJSONArray("open")?.doubles()?.firstOrNull()
        fun range(low: String, high: String): String {
            val lowValue = meta.optDouble(low, Double.NaN)
            val highValue = meta.optDouble(high, Double.NaN)
            return if (lowValue.isNaN() || highValue.isNaN()) "-" else "${formatPrice(lowValue, prefix)} - ${formatPrice(highValue, prefix)}"
        }
        return MarketDetailStats(
            bid = "",
            ask = "",
            dayRange = range("regularMarketDayLow", "regularMarketDayHigh"),
            yearRange = range("fiftyTwoWeekLow", "fiftyTwoWeekHigh"),
            previousClose = formatPrice(meta.optDouble("chartPreviousClose", Double.NaN), prefix),
            open = firstOpen?.let { formatPrice(it, prefix) } ?: "-",
            volume24h = formatCompact(meta.optDouble("regularMarketVolume", Double.NaN)),
            marketCap = "-",
            rank = "-",
            circulatingSupply = "-",
            change7d = null,
            change30d = null,
            change1y = null
        )
    }

    private fun fetchSpark(chunk: List<Spec>): List<MarketAsset> {
        val symbols = chunk.joinToString(",") { URLEncoder.encode(it.ticker, "UTF-8") }
        val json = JSONObject(Http.get("$BASE/spark?symbols=$symbols&range=1d&interval=15m"))
        return chunk.mapNotNull { spec ->
            val item = json.optJSONObject(spec.ticker) ?: return@mapNotNull null
            val values = item.optJSONArray("close")?.doubles().orEmpty()
            val last = values.lastOrNull() ?: return@mapNotNull null
            val previous = item.optDouble("chartPreviousClose", Double.NaN).takeIf { !it.isNaN() && it != 0.0 } ?: values.first()
            val change = last - previous
            val timestamps = item.optJSONArray("timestamp")
            val updated = timestamps?.optLong(timestamps.length() - 1) ?: 0L
            MarketAsset(
                name = spec.name,
                symbol = spec.symbol,
                price = formatPrice(last, spec.prefix),
                changeValue = formatSignedPrice(change, spec.prefix),
                changePercent = if (previous == 0.0) 0.0 else change / previous * 100.0,
                updatedAt = formatJakartaTime(updated),
                sparkline = values.map { it.toFloat() }.downsample(MAX_SPARKLINE_POINTS),
                category = spec.category,
                source = SOURCE,
                unit = spec.unit,
                chartId = spec.ticker,
                bid = "",
                ask = "",
                dayRange = "${formatPrice(values.min(), spec.prefix)} - ${formatPrice(values.max(), spec.prefix)}",
                yearRange = "-",
                previousClose = formatPrice(previous, spec.prefix),
                open = formatPrice(values.first(), spec.prefix)
            )
        }
    }

    private fun chartResult(ticker: String, range: String, interval: String): JSONObject {
        val encoded = URLEncoder.encode(ticker, "UTF-8")
        val chart = JSONObject(Http.get("$BASE/chart/$encoded?range=$range&interval=$interval")).getJSONObject("chart")
        return chart.optJSONArray("result")?.optJSONObject(0)
            ?: error(chart.optJSONObject("error")?.optString("description").orEmpty().ifBlank { "Data $ticker tidak tersedia" })
    }

    private fun closes(result: JSONObject): List<Double> =
        result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)
            ?.optJSONArray("close")?.doubles().orEmpty()

    /** Numbers of a JSON array, skipping nulls (Yahoo uses null for missing candles). */
    private fun JSONArray.doubles(): List<Double> =
        (0 until length()).mapNotNull { index ->
            if (isNull(index)) null else optDouble(index, Double.NaN).takeIf { !it.isNaN() }
        }

    private data class Spec(
        val ticker: String,
        val symbol: String,
        val name: String,
        val category: String,
        val unit: String,
        val prefix: String
    )

    companion object {
        const val SOURCE = "Yahoo Finance"

        private const val BASE = "https://query1.finance.yahoo.com/v8/finance"
        private const val SPARK_BATCH = 12 // the endpoint accepts at most 20 symbols
        private const val MAX_SPARKLINE_POINTS = 28
        private const val MAX_CHART_POINTS = 120

        private fun index(ticker: String, symbol: String, name: String, unit: String) =
            Spec(ticker, symbol, name, "Indeks", unit, "")

        private fun future(ticker: String, symbol: String, name: String) =
            Spec(ticker, symbol, name, "Futures Indeks", "CME • USD", "")

        private fun idxStock(code: String, name: String) =
            Spec("$code.JK", code, name, "Saham", "IDX • IDR", "Rp")

        private fun usStock(code: String, name: String) =
            Spec(code, code, name, "Saham", "NASDAQ • USD", "$")

        private fun commodity(ticker: String, symbol: String, name: String) =
            Spec(ticker, symbol, name, "Komoditas", "$symbol/USD", "$")

        private val SPECS = listOf(
            index("^JKSE", "IHSG", "IHSG (IDX Composite)", "IDX • IDR"),
            index("^GSPC", "SPX", "S&P 500", "S&P • USD"),
            index("^IXIC", "IXIC", "Nasdaq Composite", "NASDAQ • USD"),
            index("^DJI", "DJI", "Dow Jones", "DJ • USD"),
            index("^N225", "N225", "Nikkei 225", "Osaka • JPY"),
            index("^HSI", "HSI", "Hang Seng", "HKEX • HKD"),
            future("ES=F", "ES", "S&P 500 Futures"),
            future("NQ=F", "NQ", "Nasdaq 100 Futures"),
            future("YM=F", "YM", "Dow Jones Futures"),
            commodity("CL=F", "WTI", "Minyak WTI"),
            commodity("BZ=F", "BRENT", "Minyak Brent"),
            commodity("NG=F", "NATGAS", "Gas Alam"),
            idxStock("BBCA", "Bank Central Asia"),
            idxStock("BBRI", "Bank Rakyat Indonesia"),
            idxStock("BMRI", "Bank Mandiri"),
            idxStock("TLKM", "Telkom Indonesia"),
            idxStock("ASII", "Astra International"),
            idxStock("GOTO", "GoTo Gojek Tokopedia"),
            usStock("AAPL", "Apple"),
            usStock("NVDA", "NVIDIA"),
            usStock("MSFT", "Microsoft"),
            usStock("TSLA", "Tesla"),
            usStock("GOOGL", "Alphabet"),
            usStock("AMZN", "Amazon")
        )

        private fun specFor(ticker: String): Spec? = SPECS.firstOrNull { it.ticker == ticker }
    }
}
