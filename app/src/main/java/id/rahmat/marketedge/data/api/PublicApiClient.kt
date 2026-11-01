package id.rahmat.marketedge.data.api

import id.rahmat.marketedge.domain.model.MarketDetailStats
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.NewsArticle
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class PublicApiClient {
    private val idLocale = Locale("id", "ID")
    private val yahoo = YahooFinanceClient()
    private val news = MarketNewsClient()

    fun fetchYahooMarkets(): List<MarketAsset> = yahoo.fetchMarkets()

    fun fetchMarketNews(): List<NewsArticle> = news.fetchLatest()

    fun fetchCoinGeckoMarkets(): List<MarketAsset> {
        val endpoint = "https://api.coingecko.com/api/v3/coins/markets" +
            "?vs_currency=usd" +
            "&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=24h"
        val json = request(endpoint)
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val change = item.optDouble("price_change_percentage_24h", 0.0)
            val priceChange = item.optDouble("price_change_24h", 0.0)
            val high = item.optDouble("high_24h", 0.0)
            val low = item.optDouble("low_24h", 0.0)
            val price = item.optDouble("current_price", 0.0)
            MarketAsset(
                name = item.optString("name"),
                symbol = item.optString("symbol").uppercase(Locale.US),
                price = formatUsd(price),
                changeValue = formatSignedUsd(priceChange),
                changePercent = change,
                updatedAt = formatUpdatedAt(item.optString("last_updated")),
                sparkline = parseSparkline(item.optJSONObject("sparkline_in_7d")?.optJSONArray("price")),
                category = "Kripto",
                source = "Market Feed",
                unit = "${item.optString("symbol").uppercase(Locale.US)}/USD",
                chartId = item.optString("id").takeIf { it.isNotBlank() },
                bid = formatUsd(price * 0.9995),
                ask = formatUsd(price * 1.0005),
                dayRange = "${formatUsd(low)} - ${formatUsd(high)}",
                yearRange = "${formatUsd(item.optDouble("atl", 0.0))} - ${formatUsd(item.optDouble("ath", 0.0))}",
                previousClose = formatUsd(price - priceChange),
                // The markets endpoint has no open price; the price 24h ago is the closest.
                open = formatUsd(price - priceChange)
            )
        }
    }

    fun fetchCurrencyAndCommodityMarkets(): List<MarketAsset> {
        val current = JSONObject(request("https://latest.currency-api.pages.dev/v1/currencies/usd.json"))
        val date = current.optString("date")
        val currentRates = current.getJSONObject("usd")
        // One download per day, shared by every currency (this used to be 7 downloads per currency).
        val history = ratesHistory(date, currentRates, 7)
        val previousRates = history.getOrNull(history.size - 2)
        val specs = listOf(
            CurrencySpec("USD/IDR", "USD/IDR", "idr", false, "Mata Uang"),
            CurrencySpec("EUR/USD", "Euro", "eur", true, "Mata Uang"),
            CurrencySpec("GBP/USD", "British Pound", "gbp", true, "Mata Uang"),
            CurrencySpec("USD/JPY", "USD/JPY", "jpy", false, "Mata Uang"),
            CurrencySpec("AUD/USD", "Australian Dollar", "aud", true, "Mata Uang"),
            CurrencySpec("USD/SGD", "USD/SGD", "sgd", false, "Mata Uang"),
            CurrencySpec("USD/CNY", "USD/CNY", "cny", false, "Mata Uang"),
            CurrencySpec("USD/MYR", "USD/MYR", "myr", false, "Mata Uang"),
            CurrencySpec("USD/THB", "USD/THB", "thb", false, "Mata Uang"),
            CurrencySpec("USD/KRW", "USD/KRW", "krw", false, "Mata Uang"),
            CurrencySpec("Emas", "XAU/USD", "xau", true, "Komoditas"),
            CurrencySpec("Perak", "XAG/USD", "xag", true, "Komoditas")
        )
        return specs.mapNotNull { spec ->
            val currentRate = currentRates.optDouble(spec.code, Double.NaN)
            if (currentRate.isNaN()) return@mapNotNull null
            val currentPrice = if (spec.invert) 1.0 / currentRate else currentRate
            val previousRate = previousRates?.optDouble(spec.code, Double.NaN)
            val previousPrice = if (previousRate != null && !previousRate.isNaN()) {
                if (spec.invert) 1.0 / previousRate else previousRate
            } else {
                currentPrice
            }
            val change = currentPrice - previousPrice
            val changePercent = if (previousPrice == 0.0) 0.0 else (change / previousPrice) * 100.0
            val sparkline = history.map { rates ->
                val rate = rates?.optDouble(spec.code, Double.NaN) ?: Double.NaN
                (if (rate.isNaN() || rate == 0.0) currentPrice else if (spec.invert) 1.0 / rate else rate).toFloat()
            }
            val formatter = formatterFor(spec)
            MarketAsset(
                name = spec.name,
                symbol = spec.symbol,
                price = formatter(currentPrice),
                changeValue = formatSignedNumber(change, spec),
                changePercent = changePercent,
                updatedAt = date,
                sparkline = sparkline,
                category = spec.category,
                source = "FX Feed",
                unit = spec.symbol,
                chartId = spec.code,
                bid = formatter(currentPrice * 0.9996),
                ask = formatter(currentPrice * 1.0004),
                dayRange = "${formatter(minOf(currentPrice, previousPrice))} - ${formatter(maxOf(currentPrice, previousPrice))}",
                yearRange = "-",
                previousClose = formatter(previousPrice),
                open = formatter(previousPrice)
            )
        }
    }

    fun fetchMarketChart(asset: MarketAsset, days: Int): List<Float> {
        if (asset.source == YahooFinanceClient.SOURCE) return yahoo.fetchChart(asset, days)
        if (asset.category == "Kripto" && !asset.chartId.isNullOrBlank()) {
            val json = request(
                "https://api.coingecko.com/api/v3/coins/${asset.chartId}/market_chart" +
                    "?vs_currency=usd&days=$days&precision=full"
            )
            val prices = JSONObject(json).getJSONArray("prices")
            return parsePricePairs(prices)
        }

        val code = asset.chartId ?: return asset.sparkline
        val invert = asset.category == "Komoditas" ||
            asset.symbol in setOf("EUR/USD", "GBP/USD", "AUD/USD", "XAU/USD", "XAG/USD")
        val fallback = asset.sparkline.lastOrNull()?.toDouble() ?: 1.0
        return ratesHistory(null, null, 7).map { rates ->
            val rate = rates?.optDouble(code, Double.NaN) ?: Double.NaN
            (if (rate.isNaN() || rate == 0.0) fallback else if (invert) 1.0 / rate else rate).toFloat()
        }
    }

    fun fetchMarketDetailStats(asset: MarketAsset): MarketDetailStats {
        if (asset.source == YahooFinanceClient.SOURCE) return yahoo.fetchDetailStats(asset)
        if (asset.category == "Kripto" && !asset.chartId.isNullOrBlank()) {
            val detail = JSONObject(
                request(
                    "https://api.coingecko.com/api/v3/coins/${asset.chartId}" +
                        "?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=false"
                )
            )
            val marketData = detail.getJSONObject("market_data")
            val price = marketData.getJSONObject("current_price").optDouble("usd", Double.NaN)
            val high24h = marketData.getJSONObject("high_24h").optDouble("usd", Double.NaN)
            val low24h = marketData.getJSONObject("low_24h").optDouble("usd", Double.NaN)
            val priceChange24h = marketData.optDouble("price_change_24h", Double.NaN)
            val previousClose = if (!price.isNaN() && !priceChange24h.isNaN()) price - priceChange24h else Double.NaN
            val dailyPrices = runCatching { fetchMarketChart(asset, 1) }.getOrDefault(emptyList())
            val yearlyPrices = runCatching { fetchMarketChart(asset, 365) }.getOrDefault(emptyList())
            val orderBook = runCatching { fetchCoinbaseBidAsk(asset.symbol) }.getOrNull()
            val open = dailyPrices.firstOrNull()?.toDouble() ?: previousClose
            val dayLow = dailyPrices.minOrNull()?.toDouble()?.let { minOf(it, low24h) } ?: low24h
            val dayHigh = dailyPrices.maxOrNull()?.toDouble()?.let { maxOf(it, high24h) } ?: high24h
            val yearLow = yearlyPrices.minOrNull()?.toDouble()
            val yearHigh = yearlyPrices.maxOrNull()?.toDouble()
            return MarketDetailStats(
                bid = orderBook?.first?.let(::formatUsd).orEmpty(),
                ask = orderBook?.second?.let(::formatUsd).orEmpty(),
                dayRange = formatRange(dayLow, dayHigh),
                yearRange = formatRange(yearLow, yearHigh),
                previousClose = formatOptionalUsd(previousClose),
                open = formatOptionalUsd(open),
                volume24h = formatCompactUsd(marketData.getJSONObject("total_volume").optDouble("usd", Double.NaN)),
                marketCap = formatCompactUsd(marketData.getJSONObject("market_cap").optDouble("usd", Double.NaN)),
                rank = detail.optInt("market_cap_rank", 0).takeIf { it > 0 }?.let { "#$it" } ?: "-",
                circulatingSupply = formatQuantity(marketData.optDouble("circulating_supply", Double.NaN), asset.symbol),
                change7d = marketData.optDoubleOrNull("price_change_percentage_7d"),
                change30d = marketData.optDoubleOrNull("price_change_percentage_30d"),
                change1y = marketData.optDoubleOrNull("price_change_percentage_1y")
            )
        }

        val latest = asset.sparkline.lastOrNull()?.toDouble()
        val low = asset.sparkline.minOrNull()?.toDouble()
        val high = asset.sparkline.maxOrNull()?.toDouble()
        return MarketDetailStats(
            bid = "",
            ask = "",
            dayRange = asset.dayRange.takeIf { it.isNotBlank() } ?: formatRange(low, high),
            yearRange = asset.yearRange,
            previousClose = asset.previousClose,
            open = asset.open,
            volume24h = "-",
            marketCap = "-",
            rank = "-",
            circulatingSupply = latest?.let { formatQuantity(it, asset.symbol) } ?: "-",
            change7d = null,
            change30d = null,
            change1y = null
        )
    }

    private fun request(url: String): String = Http.get(url)

    private fun fetchCoinbaseBidAsk(symbol: String): Pair<Double, Double>? {
        val json = request("https://api.exchange.coinbase.com/products/${symbol.uppercase(Locale.US)}-USD/book?level=1")
        val book = JSONObject(json)
        val bid = book.optJSONArray("bids")?.optJSONArray(0)?.optString(0)?.toDoubleOrNull()
        val ask = book.optJSONArray("asks")?.optJSONArray(0)?.optString(0)?.toDoubleOrNull()
        return if (bid != null && ask != null) bid to ask else null
    }

    private fun parseSparkline(values: JSONArray?): List<Float> {
        if (values == null || values.length() == 0) return emptyList()
        val step = (values.length() / 24).coerceAtLeast(1)
        val points = mutableListOf<Float>()
        var index = 0
        while (index < values.length()) {
            points.add(values.optDouble(index).toFloat())
            index += step
        }
        return points.takeLast(28)
    }

    private fun parsePricePairs(values: JSONArray): List<Float> {
        if (values.length() == 0) return emptyList()
        val step = (values.length() / 96).coerceAtLeast(1)
        val points = mutableListOf<Float>()
        var index = 0
        while (index < values.length()) {
            val pair = values.optJSONArray(index)
            val price = pair?.optDouble(1, Double.NaN) ?: Double.NaN
            if (!price.isNaN()) points.add(price.toFloat())
            index += step
        }
        return points.takeLast(120)
    }

    /**
     * Daily USD rate tables for the [count] days ending at [endDate] (oldest first).
     * [endRates] is reused for the last day when it was already downloaded; a day
     * that fails to load is null.
     */
    private fun ratesHistory(endDate: String?, endRates: JSONObject?, count: Int): List<JSONObject?> {
        val dates = lastDates(count, endDate)
        return dates.mapIndexed { index, date ->
            if (index == dates.lastIndex && endRates != null) {
                endRates
            } else {
                runCatching {
                    JSONObject(request("https://$date.currency-api.pages.dev/v1/currencies/usd.json")).getJSONObject("usd")
                }.getOrNull()
            }
        }
    }

    private fun formatUsd(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = if (abs(value) < 10) 4 else 2
            minimumFractionDigits = if (abs(value) < 10) 2 else 0
        }
        return "$" + formatter.format(value)
    }

    private fun formatIdrLike(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }
        return formatter.format(value)
    }

    private fun formatPlain(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = if (abs(value) < 10) 4 else 2
            minimumFractionDigits = if (abs(value) < 10) 2 else 0
        }
        return formatter.format(value)
    }

    private fun formatSignedUsd(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return sign + formatUsd(abs(value))
    }

    private fun formatSignedNumber(value: Double, spec: CurrencySpec): String {
        val sign = if (value >= 0) "+" else "-"
        return sign + formatterFor(spec)(abs(value))
    }

    private fun formatOptionalUsd(value: Double?): String =
        value?.takeIf { !it.isNaN() && !it.isInfinite() }?.let(::formatUsd) ?: "-"

    private fun formatRange(low: Double?, high: Double?): String {
        val left = formatOptionalUsd(low)
        val right = formatOptionalUsd(high)
        return if (left == "-" || right == "-") "-" else "$left - $right"
    }

    private fun formatCompactUsd(value: Double): String {
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return "-"
        val suffixes = listOf(
            1_000_000_000_000.0 to "T",
            1_000_000_000.0 to "B",
            1_000_000.0 to "M"
        )
        val match = suffixes.firstOrNull { value >= it.first }
        return if (match == null) {
            formatUsd(value)
        } else {
            "$" + String.format(Locale.US, "%.2f%s", value / match.first, match.second)
        }
    }

    private fun formatQuantity(value: Double, symbol: String): String {
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return "-"
        val suffixes = listOf(
            1_000_000_000.0 to "B",
            1_000_000.0 to "M",
            1_000.0 to "K"
        )
        val match = suffixes.firstOrNull { value >= it.first }
        return if (match == null) {
            "${formatPlain(value)} $symbol"
        } else {
            "${String.format(Locale.US, "%.2f%s", value / match.first, match.second)} $symbol"
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name, Double.NaN).takeIf { !it.isNaN() && !it.isInfinite() } else null

    private fun formatterFor(spec: CurrencySpec): (Double) -> String = when {
        spec.code == "idr" -> ::formatIdrLike
        spec.invert -> ::formatUsd
        else -> ::formatPlain
    }

    private fun formatUpdatedAt(iso: String): String {
        val date = parseIso(iso) ?: return "baru saja"
        return SimpleDateFormat("HH:mm 'WIB'", idLocale).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(date)
    }

    private fun parseIso(iso: String): Date? {
        val normalized = iso.replace(Regex("\\.\\d+Z$"), "Z")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)
        }.getOrNull()
    }

    private fun lastDates(count: Int, endDate: String? = null): List<String> {
        val utc = TimeZone.getTimeZone("UTC")
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
        val calendar = Calendar.getInstance(utc)
        endDate?.let { runCatching { formatter.parse(it) }.getOrNull() }?.let { calendar.time = it }
        val dates = mutableListOf<String>()
        repeat(count) {
            dates.add(formatter.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dates.reversed()
    }

    private data class CurrencySpec(
        val name: String,
        val symbol: String,
        val code: String,
        val invert: Boolean,
        val category: String
    )
}
