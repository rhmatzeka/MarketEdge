package id.rahmat.marketedge.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parses real samples of the CNBC Indonesia RSS feed and the Yahoo spark endpoint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedParsingTest {
    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    @Test
    fun parsesCnbcMarketRss() {
        val articles = MarketNewsClient().parse(resource("cnbc_market_rss.xml"))

        assertEquals(3, articles.size)
        val first = articles.first()
        assertEquals("Pakai Info Rahasia Militer, Tentara Cuan Rp7,2 Miliar di Pasar Taruhan", first.title)
        assertEquals(MarketNewsClient.SOURCE, first.source)
        assertTrue(first.sourceUrl!!.startsWith("https://www.cnbcindonesia.com/market/"))
        assertEquals(first.sourceUrl, first.id)
        assertTrue(first.imageUrl!!.startsWith("https://awsimages.detik.net.id/"))
        assertFalse(first.imageUrl!!.contains("&amp;"))
        assertTrue(first.summary.startsWith("Seorang anggota militer AS"))
        assertFalse(first.summary.contains("<img"))
        assertFalse(first.summary.contains('￼'))
        assertEquals("Saham", articles[1].category)
    }

    @Test
    fun parsesYahooSpark() {
        val assets = YahooFinanceClient().parseSpark(resource("yahoo_spark.json"))

        assertEquals(listOf("IHSG", "ES", "WTI"), assets.map { it.symbol })

        val ihsg = assets[0]
        assertEquals("Indeks", ihsg.category)
        assertEquals("5.057,05", ihsg.price)
        assertEquals((5057.05 - 5098.057) / 5098.057 * 100, ihsg.changePercent, 1e-9)
        assertFalse(ihsg.isPositive)
        assertEquals("5.098,06", ihsg.previousClose)
        assertEquals("5.040,11", ihsg.open)
        assertEquals("4.989,72 - 5.069,53", ihsg.dayRange)
        assertEquals("^JKSE", ihsg.chartId)
        assertEquals(YahooFinanceClient.SOURCE, ihsg.source)
        assertTrue(ihsg.sparkline.size in 2..28)

        assertEquals("Futures Indeks", assets[1].category)
        assertEquals("7.614,00", assets[1].price)

        val oil = assets[2]
        assertEquals("Komoditas", oil.category)
        assertEquals("$104,12", oil.price)
        assertEquals("+$2,12", oil.changeValue)
        assertTrue(oil.isPositive)
    }
}
