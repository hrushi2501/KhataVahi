package com.farmeraccounts.domain.model

/**
 * Supported units of agricultural work and land measurement.
 */
enum class QuantityUnit(val gujarati: String, val english: String) {
    BIGHA("વીઘા", "Bigha"),
    ACRE("એકર", "Acre"),
    HOUR("કલાક", "Hour"),
    TRIP("ફેરો", "Trip");

    fun bilingualTitle(): String = com.farmeraccounts.core.util.DisplayLanguage.text(gujarati, english)

    companion object {
        fun fromString(value: String?): QuantityUnit {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BIGHA
        }
    }
}

