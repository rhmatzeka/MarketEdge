package id.rahmat.marketedge.data.ai

import id.rahmat.marketedge.domain.model.MarketAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarrenAiTest {
    private val ai = WarrenAi(apiKey = "", baseUrl = "", model = "")

    private fun asset(symbol: String, change: Double) =
        MarketAsset(symbol, symbol, "$1", "+$0", change, "baru saja", emptyList())

    @Test
    fun removesMarkdownFromAnswers() {
        val cleaned = ai.sanitize("**Kesimpulan:** naik\n- poin satu\n1. poin dua\n`kode`")
        assertEquals("Kesimpulan: naik\n\npoin satu\n\npoin dua\n\nkode", cleaned)
    }

    @Test
    fun answersLocallyWithoutAnApiKey() {
        val answer = ai.answer("Ringkas market", listOf(asset("BTC", 4.2), asset("ETH", -2.5)), emptyList())
        assertTrue(answer.text.contains("BTC sedang paling kuat (+4,20%)"))
        assertTrue(answer.text.contains("ETH sedang paling lemah (-2,50%)"))
        assertEquals(listOf("BTC", "ETH"), answer.recommendations.map { it.symbol })
    }
}
