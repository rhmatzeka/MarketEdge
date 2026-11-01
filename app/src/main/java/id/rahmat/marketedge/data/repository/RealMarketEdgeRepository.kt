package id.rahmat.marketedge.data.repository

import id.rahmat.marketedge.data.api.PublicApiClient
import id.rahmat.marketedge.domain.model.ChatMessage
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.NewsArticle
import id.rahmat.marketedge.domain.repository.MarketEdgeRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture

class RealMarketEdgeRepository(
    private val apiClient: PublicApiClient
) : MarketEdgeRepository {
    /**
     * Loads every feed at the same time. One feed failing (CoinGecko rate limits are
     * common) no longer hides the others; it only throws when nothing could be loaded.
     */
    override fun marketAssets(): List<MarketAsset> {
        val feeds = listOf(
            CompletableFuture.supplyAsync { runCatching { apiClient.fetchYahooMarkets() } },
            CompletableFuture.supplyAsync { runCatching { apiClient.fetchCurrencyAndCommodityMarkets() } },
            CompletableFuture.supplyAsync { runCatching { apiClient.fetchCoinGeckoMarkets() } }
        ).map { it.join() }
        val assets = feeds.flatMap { it.getOrDefault(emptyList()) }
        if (assets.isEmpty()) {
            throw feeds.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IllegalStateException("Data market kosong")
        }
        return assets
    }

    override fun topNews(): List<NewsArticle> = apiClient.fetchMarketNews()

    override fun initialChat(): List<ChatMessage> = listOf(
        ChatMessage(
            id = "hello",
            text = "Halo, saya WarrenAI. Saya memakai data pasar dan headline terbaru untuk membantu merangkum aset, berita, dan risiko utama.",
            timestamp = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date()),
            fromUser = false
        )
    )
}
