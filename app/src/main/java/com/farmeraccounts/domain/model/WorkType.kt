package com.farmeraccounts.domain.model

enum class WorkType(val gujarati: String, val english: String) {
    PLOWING("ખેડ", "Plowing"),
    SOWING("વાવણી", "Sowing"),
    ROTAVATOR("રોટાવેટર", "Rotavator"),
    CULTIVATOR("રાંપ / કલ્ટીવેટર", "Cultivator"),
    HARVESTING("લણણી / હાર્વેસ્ટિંગ", "Harvesting"),
    THRESHING("થ્રેશર", "Threshing"),
    TROLLEY_HAULAGE("ટ્રોલી ભાડું", "Trolley Haulage"),
    LEVELING("સપાટ કરવું", "Leveling"),
    OTHER("અન્ય કામ", "Other Work");

    fun bilingualTitle(): String = com.farmeraccounts.core.util.DisplayLanguage.text(gujarati, english)

    companion object {
        fun fromString(value: String?): WorkType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}

