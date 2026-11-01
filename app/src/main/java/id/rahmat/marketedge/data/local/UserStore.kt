package id.rahmat.marketedge.data.local

import android.content.Context
import id.rahmat.marketedge.domain.model.NewsArticle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the signed-in account, subscription, watchlist and saved articles on the
 * device so they survive an app restart.
 */
class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val userName: String? get() = prefs.getString(KEY_USER_NAME, null)
    val userEmail: String? get() = prefs.getString(KEY_USER_EMAIL, null)
    val subscriptionTier: String? get() = prefs.getString(KEY_SUBSCRIPTION, null)

    fun saveAccount(name: String?, email: String?, subscriptionTier: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_SUBSCRIPTION, subscriptionTier)
            .apply()
    }

    fun watchlist(default: List<String>): List<String> {
        val raw = prefs.getString(KEY_WATCHLIST, null) ?: return default
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(default)
    }

    fun saveWatchlist(symbols: Collection<String>) {
        prefs.edit().putString(KEY_WATCHLIST, JSONArray(symbols.toList()).toString()).apply()
    }

    fun savedArticles(): List<NewsArticle> {
        val raw = prefs.getString(KEY_SAVED_ARTICLES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getJSONObject(it).toArticle() }
        }.getOrDefault(emptyList())
    }

    fun saveArticles(articles: Collection<NewsArticle>) {
        val array = JSONArray()
        articles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SAVED_ARTICLES, array.toString()).apply()
    }

    private fun NewsArticle.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("source", source)
        put("timeAgo", timeAgo)
        put("category", category)
        put("summary", summary)
        put("content", content)
        put("imageColor", imageColor)
        put("sourceUrl", sourceUrl.orEmpty())
        put("imageUrl", imageUrl.orEmpty())
    }

    private fun JSONObject.toArticle(): NewsArticle = NewsArticle(
        id = getString("id"),
        title = optString("title"),
        source = optString("source"),
        timeAgo = optString("timeAgo"),
        category = optString("category"),
        summary = optString("summary"),
        content = optString("content"),
        imageColor = optInt("imageColor"),
        tags = emptyList(),
        sourceUrl = optString("sourceUrl").ifBlank { null },
        imageUrl = optString("imageUrl").ifBlank { null }
    )

    private companion object {
        const val PREFS = "marketedge_user"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_SUBSCRIPTION = "subscription_tier"
        const val KEY_WATCHLIST = "watchlist_symbols"
        const val KEY_SAVED_ARTICLES = "saved_articles"
    }
}
