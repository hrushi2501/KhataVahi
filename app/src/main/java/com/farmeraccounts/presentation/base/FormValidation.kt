package com.farmeraccounts.presentation.base

import com.farmeraccounts.core.util.NumericInputNormalizer

/** Strict form validation before a local financial write. */
object FormValidation {
    fun positiveAmount(text: String?): Boolean =
        NumericInputNormalizer.parseRupeesToPaise(text) > 0L

    fun positiveQuantity(text: String?): Boolean =
        NumericInputNormalizer.parseQuantityToScaled(text) > 0L

    fun optionalPayment(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        if (positiveAmount(text)) return true
        // The parser uses zero for invalid input too. Accept only an explicit valid zero.
        return text.trim().matches(Regex("[0૦०]+([.][0૦०]{0,2})?"))
    }
}
