package com.farmeraccounts.data.sync

import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
    @Test fun `fresh installation and unfinished download must pull`() {
        assertTrue(SyncPolicy.shouldPull(100L, 0L, false))
        assertTrue(SyncPolicy.shouldPull(100L, 99L, true))
    }
    @Test fun `recent empty poll skips repeat fetches until interval expires`() {
        assertFalse(SyncPolicy.shouldPull(1100L, 1000L, false))
        assertFalse(SyncPolicy.shouldPull(1000L + SyncPolicy.PULL_INTERVAL_MS - 1L, 1000L, false))
        assertTrue(SyncPolicy.shouldPull(1000L + SyncPolicy.PULL_INTERVAL_MS, 1000L, false))
    }
    @Test fun `clock rollback does not suppress downloads forever`() {
        assertTrue(SyncPolicy.shouldPull(900L, 1000L, false))
    }
}
