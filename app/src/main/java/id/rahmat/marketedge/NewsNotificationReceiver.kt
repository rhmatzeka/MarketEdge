package id.rahmat.marketedge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NewsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NewsNotificationController.scheduleNewsChecks(context.applicationContext)
            return
        }

        val pendingResult = goAsync()
        Thread {
            try {
                NewsNotificationController.checkLatestNews(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
