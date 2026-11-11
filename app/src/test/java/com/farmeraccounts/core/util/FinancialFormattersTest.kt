package com.farmeraccounts.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialFormattersTest {

    @Test
    fun `format zero paise produces formatted zero rupees`() {
        val result = FinancialFormatters.formatPaiseToRupees(0L)
        assertEquals("₹ 0.00", result)
    }

    @Test
    fun `format positive paise follows Indian numbering system`() {
        // 1,45,250 Rupees and 50 paise = 14525050L paise
        val result = FinancialFormatters.formatPaiseToRupees(14525050L)
        assertEquals("₹ 1,45,250.50", result)
    }

    @Test
    fun `format large crore balance correctly with Indian comma separators`() {
        // 1 Crore Rupees = 1,00,00,000 Rupees = 1000000000L paise
        val result = FinancialFormatters.formatPaiseToRupees(1000000000L)
        assertEquals("₹ 1,00,00,000.00", result)
    }

    @Test
    fun `format negative paise formats with negative sign`() {
        val result = FinancialFormatters.formatPaiseToRupees(-54000L)
        assertEquals("-₹ 540.00", result)
    }

    @Test
    fun `toGujaratiNumerals converts English digits to Gujarati equivalents`() {
        val input = "0123456789"
        val expected = "૦૧૨૩૪૫૬૭૮૯"
        assertEquals(expected, FinancialFormatters.toGujaratiNumerals(input))
    }

    @Test
    fun `bilingual currency format combines Gujarati and English representations`() {
        val result = FinancialFormatters.formatPaiseBilingual(25000L)
        assertEquals("₹ ૨૫૦.૦૦ (₹ 250.00)", result)
    }

    @Test
    fun `integer arithmetic does not suffer from floating point errors`() {
        // 3 installments of 33 paise = 99 paise (Long integer addition)
        val part1 = 33L
        val part2 = 33L
        val part3 = 33L
        val totalPaise = part1 + part2 + part3

        assertEquals(99L, totalPaise)
        assertEquals("₹ 0.99", FinancialFormatters.formatPaiseToRupees(totalPaise))
    }
}

