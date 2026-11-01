package id.rahmat.marketedge.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Loads remote images into views on a small thread pool, with a memory-bounded cache. */
class ImageLoader {
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String, imageView: ImageView) {
        imageView.tag = url
        cache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        executor.execute {
            val bitmap = runCatching { downloadBitmap(url) }.getOrNull() ?: return@execute
            cache.put(url, bitmap)
            mainHandler.post { if (imageView.tag == url) imageView.setImageBitmap(bitmap) }
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "MarketEdge Android")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, 1200, 800)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }

    private fun bitmapSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((width / sample) > maxWidth || (height / sample) > maxHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
