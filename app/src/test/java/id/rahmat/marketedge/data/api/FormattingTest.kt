package id.rahmat.marketedge.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun formatsPricesTheIndonesianWay() {
        assertEquals("Rp4.980", formatPrice(4980.0, "Rp"))
        assertEquals("$344,63", formatPrice(344.63, "$"))
        assertEquals("5.057,05", formatPrice(5057.05))
        assertEquals("$1,2346", formatPrice(1.23456, "$"))
        assertEquals("-", formatPrice(Double.NaN))
        assertEquals("-Rp40", formatSignedPrice(-40.0, "Rp"))
    }

    @Test
    fun formatsCompactNumbers() {
        assertEquals("286,39M", formatCompact(286_386_300.0))
        assertEquals("$1,20T", formatCompact(1.2e12, "$"))
        assertEquals("-", formatCompact(0.0))
    }

    @Test
    fun downsampleKeepsFirstAndLastPoint() {
        val points = (1..100).toList()
        val sampled = points.downsample(10)
        assertEquals(10, sampled.size)
        assertEquals(1, sampled.first())
        assertEquals(100, sampled.last())
        assertEquals(points, points.downsample(200))
    }

    @Test
    fun picksNewsCategoryFromWholeWords() {
        assertEquals("Kripto", MarketNewsClient.categoryFor("Bitcoin tembus US$100 ribu", ""))
        assertEquals("Forex & Emas", MarketNewsClient.categoryFor("Rupiah melemah ke Rp16.500 per dolar", ""))
        assertEquals("Saham", MarketNewsClient.categoryFor("IHSG ditutup naik 1%", ""))
        assertEquals("Komoditas", MarketNewsClient.categoryFor("Harga batu bara naik lagi", ""))
        assertEquals("Global", MarketNewsClient.categoryFor("Wall Street memerah", ""))
        // "as" inside "kelas" must not count as "AS" (United States)
        assertEquals("Ekonomi", MarketNewsClient.categoryFor("Ciri kelas menengah atas", ""))
    }
}
