package com.proj.automation.channel

/** Matching phone numbers written in different formats (+55 11 9..., 011 9..., 119...). */
object PhoneNumbers {

    private const val MIN_DIGITS = 8

    fun digits(number: String): String = number.filter(Char::isDigit)

    /**
     * Returns the allow-listed entry that [sender] corresponds to, or null. Numbers match when
     * their digits are equal or one ends with the other (country/area prefixes omitted), with at
     * least 8 digits in common. The allowlist is a noise filter, not authentication.
     */
    fun matchAllowed(sender: String, allowed: Collection<String>): String? {
        val s = digits(sender)
        if (s.length < MIN_DIGITS) return allowed.firstOrNull { it == sender }
        return allowed.firstOrNull { a ->
            val d = digits(a)
            d.length >= MIN_DIGITS && (d == s || d.endsWith(s) || s.endsWith(d))
        }
    }
}
