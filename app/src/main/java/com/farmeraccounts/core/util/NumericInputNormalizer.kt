package com.farmeraccounts.core.util

import java.math.BigInteger

/**
 * Normalizes user numeric input supporting both English, Gujarati, and Devanagari numerals.
 * Strictly maintains integer/paise precision without converting through Float or Double.
 */
object NumericInputNormalizer {

    /**
     * Translates Gujarati (૦-૯) and Devanagari (०-९) digits into ASCII 0-9.
     * Strips commas, currency symbols, and extra spaces.
     */
    fun normalizeDigits(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when (ch) {
                // Gujarati numerals (U+0AE6 to U+0AEF)
                in '\u0AE6'..'\u0AEF' -> sb.append(ch - '\u0AE6')
                // Devanagari numerals (U+0966 to U+096F)
                in '\u0966'..'\u096F' -> sb.append(ch - '\u0966')
                // Standard ASCII numerals and decimal separator
                in '0'..'9', '.' -> sb.append(ch)
                // Ignore commas, spaces, currency symbols
                ',', ' ', '₹', '-' -> {
                    if (ch == '-' && sb.isEmpty()) sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Converts rupee input string (e.g. "1250", "૧૨૫૦", "50.25", "૫૦.૨૫") into Long integer paise.
     * Guaranteed zero floating-point conversions.
     */
    fun parseRupeesToPaise(input: String?): Long {
        val normalized = normalizeNumericInput(input) ?: return 0L
        if (normalized.isEmpty()) return 0L
        val negative = normalized.startsWith('-')
        val number = if (negative) normalized.drop(1) else normalized
        val parts = number.split('.')
        if (parts.size > 2 || parts.any { part -> part.any { !it.isDigit() } }) return 0L
        if (parts.sumOf { it.length } == 0 || (parts.size == 2 && parts[1].length > 2)) return 0L

        val rupees = parts[0].ifEmpty { "0" }
        val paise = parts.getOrElse(1) { "" }.padEnd(2, '0').ifEmpty { "00" }
        val value = BigInteger(rupees).multiply(BigInteger.valueOf(100L))
            .add(BigInteger(paise))
            .let { if (negative) it.negate() else it }
        return value.takeIf { it >= LONG_MIN && it <= LONG_MAX }?.toLong() ?: 0L
    }

    /**
     * Converts land/quantity input string (e.g. "2.5", "૨.૫", "3", "૩") into scaled integer units.
     */
    fun parseQuantityToScaled(input: String?): Long {
        val normalized = normalizeNumericInput(input) ?: return 0L
        if (normalized.isEmpty()) return 0L
        val negative = normalized.startsWith('-')
        val number = if (negative) normalized.drop(1) else normalized
        val parts = number.split('.')
        if (parts.size > 2 || parts.any { part -> part.any { !it.isDigit() } }) return 0L
        if (parts.sumOf { it.length } == 0 || (parts.size == 2 && parts[1].length > 3)) return 0L

        val whole = BigInteger(parts[0].ifEmpty { "0" })
        val fraction = BigInteger(parts.getOrElse(1) { "" }.padEnd(3, '0').ifEmpty { "000" })
        val value = whole.multiply(BigInteger.valueOf(ScaledQuantity.SCALE)).add(fraction)
            .let { if (negative) it.negate() else it }
        return value.takeIf { it >= LONG_MIN && it <= LONG_MAX }?.toLong() ?: 0L
    }

    /** Return only supported display characters and digits; unknown characters invalidate input. */
    private fun normalizeNumericInput(input: String?): String? {
        if (input.isNullOrBlank()) return ""
        val result = StringBuilder()
        for (ch in input.trim()) {
            when (ch) {
                in '\u0AE6'..'\u0AEF' -> result.append(ch - '\u0AE6')
                in '\u0966'..'\u096F' -> result.append(ch - '\u0966')
                in '0'..'9', '.' -> result.append(ch)
                ',', ' ', '\u00A0', '₹' -> Unit
                '-' -> if (result.isEmpty()) result.append(ch) else return null
                else -> return null
            }
        }
        return result.toString()
    }

    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}

