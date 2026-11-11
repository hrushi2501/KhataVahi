package com.farmeraccounts.data.remote.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimestampIsoConverterTest {
    @Test
    fun `parses UTC and offset ISO timestamps to the same instant`() {
        val utc = TimestampIsoConverter.toEpochMillis("2026-11-11T04:30:00Z")
        val offset = TimestampIsoConverter.toEpochMillis("2026-11-11T10:00:00+05:30")
        assertEquals(utc, offset)
    }

    @Test
    fun `formats and parses epoch milliseconds without losing precision`() {
        val timestamp = 1_794_360_600_123L
        assertEquals(timestamp, TimestampIsoConverter.toEpochMillis(TimestampIsoConverter.toIsoString(timestamp)))
    }

    @Test
    fun `rejects malformed and blank timestamps instead of inventing current time`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimestampIsoConverter.toEpochMillis(" ")
        }
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            TimestampIsoConverter.toEpochMillis("not-a-timestamp")
        }
    }
}
