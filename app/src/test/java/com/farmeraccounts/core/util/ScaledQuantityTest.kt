package com.farmeraccounts.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaledQuantityTest {

    @Test
    fun `parseStringToScaled converts clean and fractional strings to scaled units`() {
        assertEquals(1000L, ScaledQuantity.parseStringToScaled("1"))
        assertEquals(1000L, ScaledQuantity.parseStringToScaled("1.0"))
        assertEquals(2500L, ScaledQuantity.parseStringToScaled("2.5"))
        assertEquals(2750L, ScaledQuantity.parseStringToScaled("2.75"))
        assertEquals(333L, ScaledQuantity.parseStringToScaled("0.333"))
        assertEquals(12500L, ScaledQuantity.parseStringToScaled("12.500"))
        assertEquals(0L, ScaledQuantity.parseStringToScaled(""))
        assertEquals(0L, ScaledQuantity.parseStringToScaled(null))
    }

    @Test
    fun `formatScaled formats scaled units back to user-facing decimals`() {
        assertEquals("1.00", ScaledQuantity.formatScaled(1000L))
        assertEquals("2.50", ScaledQuantity.formatScaled(2500L))
        assertEquals("2.75", ScaledQuantity.formatScaled(2750L))
        assertEquals("0.333", ScaledQuantity.formatScaled(333L))
        assertEquals("15.25", ScaledQuantity.formatScaled(15250L))
    }

    @Test
    fun `calculateTotalPaise computes exact whole amounts`() {
        // 2.0 Bigha at ₹600.00 (60,000 paise) = ₹1,200.00 (120,000 paise)
        val total = ScaledQuantity.calculateTotalPaise(
            quantityScaled = 2000L,
            ratePerUnitPaise = 60000L
        )
        assertEquals(120000L, total)
    }

    @Test
    fun `calculateTotalPaise computes fractional bigha accurately`() {
        // 2.50 Bigha at ₹600.00 (60,000 paise) = ₹1,500.00 (150,000 paise)
        val total = ScaledQuantity.calculateTotalPaise(
            quantityScaled = 2500L,
            ratePerUnitPaise = 60000L
        )
        assertEquals(150000L, total)
    }

    @Test
    fun `calculateTotalPaise performs deterministic half-up rounding on fractional paise`() {
        // 1.333 Bigha at ₹500.00 (50,000 paise)
        // Exact: 1.333 * 50,000 = 66,650 paise (666.50 Rupees)
        val total = ScaledQuantity.calculateTotalPaise(
            quantityScaled = 1333L,
            ratePerUnitPaise = 50000L
        )
        assertEquals(66650L, total)
    }

    @Test
    fun `calculateTotalPaise protects against 64-bit integer overflow on massive scale`() {
        // Large land area: 50,000 Bigha at ₹1,00,000 rate (10,000,000 paise)
        // quantityScaled = 50,000,000L, rate = 10,000,000L
        // Product = 500,000,000,000,000 / 1000 = 500,000,000,000 paise (₹500 Crore)
        val total = ScaledQuantity.calculateTotalPaise(
            quantityScaled = 50000000L,
            ratePerUnitPaise = 10000000L
        )
        assertEquals(500000000000L, total)
    }
}

