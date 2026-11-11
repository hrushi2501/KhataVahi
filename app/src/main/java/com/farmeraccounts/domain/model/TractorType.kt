package com.farmeraccounts.domain.model

enum class TractorType(val gujarati: String, val english: String) {
    BIG("મોટો ટ્રેક્ટર", "Big Tractor"),
    MINI("નાનો ટ્રેક્ટર", "Mini Tractor");

    fun bilingualTitle(): String = com.farmeraccounts.core.util.DisplayLanguage.text(gujarati, english)

    companion object {
        fun fromString(value: String?): TractorType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BIG
        }
    }
}

