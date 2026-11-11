package com.farmeraccounts.core.util

import java.math.BigInteger
import java.util.Locale

/**
 * Deterministic scaled integer arithmetic for land quantities and hours/trips.
 * Per project rules:
 * - Land quantity must use scaled integers.
 * - Never use Float or Double for financial calculations.
 * - Protect arithmetic against overflow and use deterministic rounding.
 *
 * Base scale factor = 1,000 (3 decimal precision: 1.000 bigha = 1,000 scaled units).
 */
object ScaledQuantity {

    const val SCALE = 1000L
    private val BIG_SCALE = BigInteger.valueOf(SCALE)
    private val BIG_HALF_SCALE = BigInteger.valueOf(SCALE / 2)

    /**
     * Converts a string representation (e.g. "2.5", "2.75", "3") into scaled units without Float/Double loss.
     */
    fun parseStringToScaled(input: String?): Long {
        if (input.isNullOrBlank()) return 0L
        val trimmed = input.trim()

        val parts = trimmed.split(".")
        val wholePart = parts[0].toLongOrNull() ?: 0L
        val isNegative = wholePart < 0 || trimmed.startsWith("-")
        val absWhole = kotlin.math.abs(wholePart)

        val fracScaled = if (parts.size > 1) {
            val fracStr = parts[1].take(3).padEnd(3, '0')
            fracStr.toLongOrNull() ?: 0L
        } else {
            0L
        }

        val totalScaled = absWhole * SCALE + fracScaled
        return if (isNegative) -totalScaled else totalScaled
    }

    /**
     * Formats scaled integer to a clean human-readable decimal string:
     * e.g. 2500L -> "2.50", 1000L -> "1.00", 2750L -> "2.75"
     */
    fun formatScaled(scaled: Long): String {
        val isNegative = scaled < 0
        val absScaled = kotlin.math.abs(scaled)

        val whole = absScaled / SCALE
        val fraction = absScaled % SCALE

        val sign = if (isNegative) "-" else ""
        return if (fraction == 0L) {
            String.format(Locale.US, "%s%d.00", sign, whole)
        } else {
            val fracStr = String.format(Locale.US, "%03d", fraction).trimEnd('0')
            val paddedFrac = if (fracStr.length == 1) fracStr + "0" else fracStr
            String.format(Locale.US, "%s%d.%s", sign, whole, paddedFrac)
        }
    }

    /**
     * Calculates total amount in paise deterministically with half-up rounding:
     * totalPaise = round((quantityScaled * ratePerUnitPaise) / SCALE)
     * Protected against Long overflow via BigInteger intermediate computation.
     */
    fun calculateTotalPaise(quantityScaled: Long, ratePerUnitPaise: Long): Long {
        if (quantityScaled == 0L || ratePerUnitPaise == 0L) return 0L

        val isNegative = (quantityScaled < 0) xor (ratePerUnitPaise < 0)
        val absQty = BigInteger.valueOf(kotlin.math.abs(quantityScaled))
        val absRate = BigInteger.valueOf(kotlin.math.abs(ratePerUnitPaise))

        val product = absQty.multiply(absRate)
        val rounded = product.add(BIG_HALF_SCALE).divide(BIG_SCALE)

        if (rounded > BigInteger.valueOf(Long.MAX_VALUE)) {
            throw ArithmeticException("Scaled arithmetic result out of Long range")
        }

        val total = rounded.toLong()
        return if (isNegative) -total else total
    }
}

