package com.farmeraccounts.core.util

import java.util.Locale

/**
 * Deterministic financial formatters.
 * Per project rules:
 * - Money is strictly stored and calculated as Long integer paise (1 Rupee = 100 paise).
 * - Floats and Doubles are strictly prohibited for financial calculations.
 */
object FinancialFormatters {

    private val GUJARATI_DIGITS = charArrayOf(
        '૦', '૧', '૨', '૩', '૪', '૫', '૬', '૭', '૮', '૯'
    )

    /**
     * Formats integer paise into a standard Indian numbering currency string:
     * e.g. 14525000L paise -> "₹ 1,45,250.00"
     * Handles negative paise gracefully: -5000L -> "-₹ 50.00"
     */
    fun formatPaiseToRupees(paise: Long): String {
        val isNegative = paise < 0
        val absPaise = if (isNegative) -paise else paise

        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val formattedRupees = formatIndianNumbering(rupees)
        val formattedPaise = String.format(Locale.US, "%02d", remainingPaise)

        val prefix = if (isNegative) "-₹ " else "₹ "
        return "$prefix$formattedRupees.$formattedPaise"
    }

    /**
     * Converts ASCII digits in a string to Gujarati numerals.
     */
    fun toGujaratiNumerals(input: String): String {
        val sb = java.lang.StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(GUJARATI_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Produces a bilingual currency representation:
     * e.g. "₹ ૧,૪૫,૨૫૦.૦૦ (₹ 1,45,250.00)"
     */
    fun formatPaiseForLanguage(paise: Long): String {
        val value = formatPaiseToRupees(paise)
        return DisplayLanguage.text(toGujaratiNumerals(value), value)
    }

    fun formatPaiseBilingual(paise: Long): String {
        val englishFormat = formatPaiseToRupees(paise)
        val gujaratiFormat = toGujaratiNumerals(englishFormat)
        return "$gujaratiFormat ($englishFormat)"
    }

    /**
     * Indian numbering system comma separation (Lakhs and Crores):
     * e.g. 12345678 -> "1,23,45,678"
     */
    private fun formatIndianNumbering(number: Long): String {
        val numStr = number.toString()
        val len = numStr.length
        if (len <= 3) return numStr

        val lastThree = numStr.substring(len - 3)
        var remaining = numStr.substring(0, len - 3)
        val sb = java.lang.StringBuilder()

        while (remaining.length > 2) {
            val chunk = remaining.substring(remaining.length - 2)
            sb.insert(0, ",$chunk")
            remaining = remaining.substring(0, remaining.length - 2)
        }
        if (remaining.isNotEmpty()) {
            sb.insert(0, remaining)
        }
        sb.append(",").append(lastThree)
        return sb.toString()
    }
}

