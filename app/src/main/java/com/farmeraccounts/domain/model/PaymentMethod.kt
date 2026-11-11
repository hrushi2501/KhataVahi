package com.farmeraccounts.domain.model

enum class PaymentMethod(val gujarati: String, val english: String) {
    CASH("રોકડ", "Cash"),
    ONLINE_UPI("ઓનલાઇન / UPI", "Online / UPI"),
    BANK_TRANSFER("બેંક ટ્રાન્સફર", "Bank Transfer"),
    CROP_EXCHANGE("અનાજ / પાક અવેજી", "Crop Exchange"),
    OTHER("અન્ય", "Other");

    fun bilingualTitle(): String = com.farmeraccounts.core.util.DisplayLanguage.text(gujarati, english)

    companion object {
        fun fromString(value: String?): PaymentMethod {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CASH
        }
    }
}

