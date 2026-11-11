package com.farmeraccounts.core.util

import java.util.Locale

/** Selects app-owned display text; never transforms stored user content. */
object DisplayLanguage {
    fun workType(value: String): String {
        val type = com.farmeraccounts.domain.model.WorkType.entries.firstOrNull {
            value == it.name || value == it.english || value == it.gujarati || value == "${it.gujarati} (${it.english})"
        }
        return type?.let { text(it.gujarati, it.english) } ?: value
    }

    fun text(gujarati: String, english: String): String =
        if (Locale.getDefault().language == "gu") gujarati else english
}
