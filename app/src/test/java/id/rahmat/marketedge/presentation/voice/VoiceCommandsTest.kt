package id.rahmat.marketedge.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun matchesWholeWordsOnly() {
        val command = normalizeVoiceCommand("Iya, saya juga tunanetra!")
        assertEquals("iya saya juga tunanetra", command)
        assertFalse(commandContains(command, "ga", "gak"))
        assertTrue(commandContains(command, "iya"))
        assertFalse(commandContains("buka program", "pro"))
        assertTrue(commandContains("buka daftar pantau", "daftar pantau"))
    }

    @Test
    fun readsNewsNumbers() {
        assertEquals(2, extractVoiceNumber("buka berita kedua"))
        assertEquals(3, extractVoiceNumber("berita 3"))
        assertEquals(1, extractVoiceNumber("berita pertama"))
        assertNull(extractVoiceNumber("buka berita"))
    }

    @Test
    fun extractsTheAiQuestion() {
        assertEquals("Bitcoin hari ini", aiQuestionFromSpeech("Tanya AI tentang Bitcoin hari ini"))
        assertEquals("", aiQuestionFromSpeech("buka berita"))
    }

    @Test
    fun shortensSpeechOnAWordBoundary() {
        assertEquals("Harga emas", shortSpeech("Harga emas naik tajam", 12))
        assertEquals("pendek", shortSpeech("pendek", 12))
    }
}
