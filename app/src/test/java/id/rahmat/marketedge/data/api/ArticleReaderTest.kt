package id.rahmat.marketedge.data.api

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArticleReaderTest {
    @Test
    fun keepsOnlyReadableArticleParagraphs() {
        val html = """
            <html><body>
            <nav><p>Menu navigasi yang panjang sekali dan tidak boleh ikut masuk ke artikel.</p></nav>
            <article>
              <p>Jakarta, CNBC Indonesia - IHSG ditutup menguat pada perdagangan hari ini setelah investor asing kembali masuk.</p>
              <p>Pendek.</p>
              <p>Subscribe untuk mendapatkan berita terbaru langsung di email kamu setiap pagi hari.</p>
              <script>var x = "<p>skrip yang tidak boleh terbaca sebagai paragraf artikel sama sekali</p>";</script>
              <p>Saham perbankan memimpin penguatan &amp; nilai transaksi mencapai Rp12 triliun.</p>
            </article>
            </body></html>
        """.trimIndent()

        assertEquals(
            "Jakarta, CNBC Indonesia - IHSG ditutup menguat pada perdagangan hari ini setelah investor asing kembali masuk.\n\n" +
                "Saham perbankan memimpin penguatan & nilai transaksi mencapai Rp12 triliun.",
            extractReadableArticle(html)
        )
    }
}
