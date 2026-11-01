package id.rahmat.marketedge.domain.repository

import id.rahmat.marketedge.domain.model.ChatMessage
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.NewsArticle

interface MarketEdgeRepository {
    fun marketAssets(): List<MarketAsset>
    fun topNews(): List<NewsArticle>
    fun initialChat(): List<ChatMessage>
}
