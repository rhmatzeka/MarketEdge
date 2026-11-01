package id.rahmat.marketedge.data.api

import android.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

internal val ID_LOCALE = Locale("id", "ID")
private val JAKARTA = TimeZone.getTimeZone("Asia/Jakarta")

/** "Rp4.980", "$344,63", "5.057,05": small prices keep 4 decimals. */
internal fun formatPrice(value: Double, prefix: String = ""): String {
    if (value.isNaN() || value.isInfinite()) return "-"
    val small = abs(value) < 10
    val formatter = NumberFormat.getNumberInstance(ID_LOCALE).apply {
        maximumFractionDigits = if (small) 4 else 2
        minimumFractionDigits = when {
            small -> 2
            prefix == "Rp" -> 0
            else -> 2
        }
    }
    return prefix + formatter.format(value)
}

internal fun formatSignedPrice(value: Double, prefix: String = ""): String =
    (if (value >= 0) "+" else "-") + formatPrice(abs(value), prefix)

/** "286,39M", "$1,20T". Returns "-" for missing or non-positive values. */
internal fun formatCompact(value: Double, prefix: String = ""): String {
    if (value.isNaN() || value.isInfinite() || value <= 0.0) return "-"
    val match = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K").firstOrNull { value >= it.first }
        ?: return formatPrice(value, prefix)
    return prefix + String.format(ID_LOCALE, "%.2f%s", value / match.first, match.second)
}

internal fun relativeTime(date: Date?): String {
    date ?: return "baru saja"
    val minutes = ((System.currentTimeMillis() - date.time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "baru saja"
        minutes < 60 -> "$minutes menit yang lalu"
        minutes < 24 * 60 -> "${minutes / 60} jam yang lalu"
        else -> "${minutes / (24 * 60)} hari yang lalu"
    }
}

/** "1 Nov, 07:40 WIB" for a Unix time in seconds. */
internal fun formatJakartaTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "baru saja"
    return SimpleDateFormat("d MMM, HH:mm 'WIB'", ID_LOCALE).apply { timeZone = JAKARTA }
        .format(Date(epochSeconds * 1000))
}

/** Stable placeholder colour for an article without an image. */
internal fun placeholderColor(seed: String): Int {
    val colors = intArrayOf(
        Color.rgb(255, 107, 0),
        Color.rgb(55, 114, 207),
        Color.rgb(27, 166, 115),
        Color.rgb(195, 125, 13),
        Color.rgb(212, 86, 86)
    )
    return colors[(seed.hashCode() and Int.MAX_VALUE) % colors.size]
}

/** Keeps at most [max] evenly spaced points, always including the latest one. */
internal fun <T> List<T>.downsample(max: Int): List<T> {
    if (size <= max || max < 2) return this
    val step = (size - 1).toDouble() / (max - 1)
    return List(max) { index -> this[(index * step).toInt().coerceAtMost(lastIndex)] }
}
