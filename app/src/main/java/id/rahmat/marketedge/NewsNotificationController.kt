package id.rahmat.marketedge

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.rahmat.marketedge.data.api.PublicApiClient
import id.rahmat.marketedge.data.repository.RealMarketEdgeRepository
import id.rahmat.marketedge.domain.model.NewsArticle

object NewsNotificationController {
    const val ACTION_CHECK_NEWS = "id.rahmat.marketedge.action.CHECK_NEWS"

    private const val TAG = "NewsNotification"
    private const val PREFS = "marketedge_news_notifications"
    private const val KEY_LAST_NEWS_ID = "last_news_id"
    private const val CHANNEL_ID = "marketedge_latest_news"
    private const val CHANNEL_NAME = "Berita terbaru"
    private const val NOTIFICATION_ID = 7031
    private const val CHECK_REQUEST_CODE = 7032
    private const val OPEN_REQUEST_CODE = 7033
    private const val FIRST_CHECK_DELAY_MS = 15 * 60 * 1000L

    fun initialize(context: Context) {
        createChannel(context)
        scheduleNewsChecks(context)
    }

    fun scheduleNewsChecks(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + FIRST_CHECK_DELAY_MS,
            AlarmManager.INTERVAL_HALF_HOUR,
            checkPendingIntent(context)
        )
    }

    fun checkLatestNews(context: Context) {
        val repository = RealMarketEdgeRepository(PublicApiClient())
        runCatching { repository.topNews() }
            .onSuccess { recordLatestFromArticles(context, it, notifyForChanges = true) }
            .onFailure { Log.w(TAG, "Failed to check latest news", it) }
    }

    fun recordLatestFromArticles(
        context: Context,
        articles: List<NewsArticle>,
        notifyForChanges: Boolean = true
    ) {
        val latest = articles.firstOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousId = prefs.getString(KEY_LAST_NEWS_ID, null)

        prefs.edit().putString(KEY_LAST_NEWS_ID, latest.id).apply()

        if (notifyForChanges && previousId != null && previousId != latest.id) {
            notifyLatestNews(context, latest)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifikasi saat MarketEdge menemukan headline terbaru."
        }
        manager.createNotificationChannel(channel)
    }

    private fun notifyLatestNews(context: Context, article: NewsArticle) {
        if (!canPostNotifications(context)) return
        createChannel(context)

        val title = "Berita terbaru dari ${article.source.ifBlank { "MarketEdge" }}"
        val summary = article.summary.ifBlank { article.title }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_news)
            .setContentTitle(shortText(title, 68))
            .setContentText(shortText(article.title, 96))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${article.title}\n\n${shortText(summary, 260)}"))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Failed to post latest news notification", it) }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun checkPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            CHECK_REQUEST_CODE,
            Intent(context, NewsNotificationReceiver::class.java).setAction(ACTION_CHECK_NEWS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun shortText(value: String, maxLength: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxLength) return clean
        return clean.take(maxLength).substringBeforeLast(" ").ifBlank { clean.take(maxLength) } + "..."
    }
}
