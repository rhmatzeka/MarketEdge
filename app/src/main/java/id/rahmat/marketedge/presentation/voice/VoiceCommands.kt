package id.rahmat.marketedge.presentation.voice

import java.util.Locale

// Pure text helpers for the voice assistant, kept apart from the UI so they can be unit tested.

internal fun normalizeVoiceCommand(value: String): String = value
    .lowercase(Locale("id", "ID"))
    .replace(Regex("[^a-z0-9\\s]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun commandContains(command: String, vararg keywords: String): Boolean {
    val padded = " $command "
    return keywords.any { padded.contains(" $it ") }
}

internal fun extractVoiceNumber(command: String): Int? {
    Regex("\\b([0-9]{1,2})\\b").find(command)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    val words = mapOf(
        "satu" to 1,
        "pertama" to 1,
        "dua" to 2,
        "kedua" to 2,
        "tiga" to 3,
        "ketiga" to 3,
        "empat" to 4,
        "keempat" to 4,
        "lima" to 5,
        "kelima" to 5,
        "enam" to 6,
        "keenam" to 6,
        "tujuh" to 7,
        "ketujuh" to 7,
        "delapan" to 8,
        "kedelapan" to 8,
        "sembilan" to 9,
        "kesembilan" to 9,
        "sepuluh" to 10,
        "kesepuluh" to 10
    )
    return words.firstNotNullOfOrNull { (word, number) -> number.takeIf { Regex("\\b$word\\b").containsMatchIn(command) } }
}

internal fun aiQuestionFromSpeech(spoken: String): String {
    val lower = spoken.lowercase(Locale("id", "ID"))
    val prefixes = listOf("tanyakan ai", "tanya ai", "kirim ai", "bertanya ai")
    val prefix = prefixes.firstOrNull { lower.contains(it) } ?: return ""
    val start = lower.indexOf(prefix) + prefix.length
    return spoken.drop(start).trim().removePrefix("tentang").trim()
}

internal fun shortSpeech(value: String, maxLength: Int): String =
    value.replace(Regex("\\s+"), " ").trim().let { clean ->
        if (clean.length <= maxLength) clean else clean.take(maxLength).substringBeforeLast(" ").ifBlank { clean.take(maxLength) }
    }
