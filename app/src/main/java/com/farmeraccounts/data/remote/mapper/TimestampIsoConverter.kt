package com.farmeraccounts.data.remote.mapper

import java.time.Instant

/**
 * Lossless ISO-8601 <-> Epoch Milliseconds timestamp converter using Android standard Java 8+ time API (minSdk 26).
 */
object TimestampIsoConverter {

    fun toIsoString(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).toString()
    }

    fun toEpochMillis(isoString: String): Long {
        require(isoString.isNotBlank()) { "Timestamp must not be blank" }
        return Instant.parse(isoString).toEpochMilli()
    }
}

