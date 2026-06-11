package id.rahmat.marketedge

import android.app.Application

class MarketEdgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewsNotificationController.initialize(this)
    }
}
