package com.farmeraccounts.data.sync

import com.farmeraccounts.data.remote.dto.SyncChangeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCursorPolicyTest {
    @Test
    fun `advances to last event only after whole page reconciles`() {
        assertEquals(9L, SyncCursorPolicy.safeNext(4L, page(5L, 7L, 9L), null))
    }

    @Test
    fun `stops immediately before earliest conflict and never moves backwards`() {
        assertEquals(6L, SyncCursorPolicy.safeNext(4L, page(5L, 7L, 9L), 7L))
        assertEquals(4L, SyncCursorPolicy.safeNext(4L, page(5L, 7L), 5L))
    }

    @Test
    fun `empty page leaves cursor unchanged`() {
        assertEquals(12L, SyncCursorPolicy.safeNext(12L, emptyList(), null))
    }

    @Test
    fun `rejects unsorted pages and conflicts outside the page`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncCursorPolicy.safeNext(0L, page(2L, 1L), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncCursorPolicy.safeNext(0L, page(2L), 3L)
        }
    }

    private fun page(vararg sequences: Long) = sequences.map {
        SyncChangeDto(it, "owner", "customers", "record-$it", "UPDATE")
    }
}
