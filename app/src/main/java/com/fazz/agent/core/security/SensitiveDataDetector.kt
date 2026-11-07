package com.fazz.agent.core.security

/**
 * Heuristic guard that blocks obviously secret data from being saved to memory
 * (passwords, PINs, one-time codes, card numbers, API keys). Errs on the side of refusing.
 */
object SensitiveDataDetector {
    private val keywords = Regex(
        "\\b(password|passwd|passcode|pin|otp|one[- ]time code|cvv|cvc|security code|secret|api[ _-]?key|token|private key|seed phrase|recovery phrase|kata ?sandi|sandi)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val cardLike = Regex("(?:\\d[ -]?){13,19}")
    private val keyLike = Regex("\\b(?:sk|pk|AIza|ghp|xox[bp])[-_A-Za-z0-9]{16,}\\b")

    fun findReason(text: String): String? = when {
        keywords.containsMatchIn(text) -> "terlihat seperti password, PIN, kode, atau rahasia"
        keyLike.containsMatchIn(text) -> "terlihat seperti API key atau token akses"
        cardLike.findAll(text).any { luhn(it.value.filter(Char::isDigit)) } -> "terlihat seperti nomor kartu pembayaran"
        else -> null
    }

    private fun luhn(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, ch ->
            var d = ch - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }
}
