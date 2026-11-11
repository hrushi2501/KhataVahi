package com.farmeraccounts.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericInputNormalizerTest {

    @Test
    fun `normalizeDigits handles null or blank input`() {
        assertEquals("", NumericInputNormalizer.normalizeDigits(null))
        assertEquals("", NumericInputNormalizer.normalizeDigits(""))
        assertEquals("", NumericInputNormalizer.normalizeDigits("   "))
    }

    @Test
    fun `normalizeDigits converts Gujarati numerals to ASCII digits`() {
        // Gujarati ૦૧૨૩૪૫૬૭૮૯ -> 0123456789
        val gujaratiInput = "૦૧૨૩૪૫૬૭૮૯"
        assertEquals("0123456789", NumericInputNormalizer.normalizeDigits(gujaratiInput))
    }

    @Test
    fun `normalizeDigits converts Devanagari numerals to ASCII digits`() {
        // Devanagari ०१२३४५६७८९ -> 0123456789
        val devanagariInput = "०१२३४५६७८९"
        assertEquals("0123456789", NumericInputNormalizer.normalizeDigits(devanagariInput))
    }

    @Test
    fun `normalizeDigits strips currency symbol, commas, and spaces`() {
        val mixedInput = " ₹ ૧,૨૫,૦૦૦.૫૦ "
        assertEquals("125000.50", NumericInputNormalizer.normalizeDigits(mixedInput))
    }

    @Test
    fun `normalizeDigits preserves negative sign at start`() {
        val negativeInput = "- ૧,૫૦૦"
        assertEquals("-1500", NumericInputNormalizer.normalizeDigits(negativeInput))
    }

    @Test
    fun `parseRupeesToPaise parses whole rupees accurately into paise`() {
        // 500 Rupees = 50000 Paise
        assertEquals(50000L, NumericInputNormalizer.parseRupeesToPaise("500"))
        // Gujarati numerals: ૫૦૦
        assertEquals(50000L, NumericInputNormalizer.parseRupeesToPaise("૫૦૦"))
    }

    @Test
    fun `parseRupeesToPaise parses decimal rupees and paise accurately`() {
        // 1250.75 Rupees = 125075 Paise
        assertEquals(125075L, NumericInputNormalizer.parseRupeesToPaise("1250.75"))
        // Gujarati numerals: ૧૨૫૦.૭૫
        assertEquals(125075L, NumericInputNormalizer.parseRupeesToPaise("૧૨૫૦.૭૫"))
        // Single digit paise: 50.5 -> 5050 Paise
        assertEquals(5050L, NumericInputNormalizer.parseRupeesToPaise("50.5"))
        // Extra precision is rejected instead of silently changing the entered value.
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise("100.509"))
    }

    @Test
    fun `parseRupeesToPaise handles blank or invalid gracefully`() {
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise(null))
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise(""))
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise("abc"))
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise("12abc"))
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise("1.2.3"))
        assertEquals(0L, NumericInputNormalizer.parseRupeesToPaise("92233720368547759"))
        assertEquals(50L, NumericInputNormalizer.parseRupeesToPaise("₹ .50"))
    }

    @Test
    fun `parseQuantityToScaled parses integer and decimal quantities`() {
        // 2.5 bigha = 2500 scaled units (scale factor = 1000)
        assertEquals(2500L, NumericInputNormalizer.parseQuantityToScaled("2.5"))
        // Gujarati numerals: ૨.૫
        assertEquals(2500L, NumericInputNormalizer.parseQuantityToScaled("૨.૫"))
        // Whole number: 3
        assertEquals(3000L, NumericInputNormalizer.parseQuantityToScaled("3"))
        // Gujarati whole number: ૩
        assertEquals(3000L, NumericInputNormalizer.parseQuantityToScaled("૩"))
        assertEquals(0L, NumericInputNormalizer.parseQuantityToScaled("1.2345"))
        assertEquals(0L, NumericInputNormalizer.parseQuantityToScaled("2x"))
        assertEquals(0L, NumericInputNormalizer.parseQuantityToScaled("9223372036854776"))
    }
}

