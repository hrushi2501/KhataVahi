package com.farmeraccounts.data.sync

import com.farmeraccounts.data.remote.dto.SyncChangeDto

/** Cursor may advance only through the contiguous prefix fully reconciled in Room. */
internal object SyncCursorPolicy {
    fun safeNext(current: Long, page: List<SyncChangeDto>, firstConflictSequence: Long?): Long {
        require(current >= 0L)
        require(page.zipWithNext().all { (a, b) -> a.sequenceId < b.sequenceId })
        require(page.all { it.sequenceId > current })
        if (page.isEmpty()) return current
        if (firstConflictSequence == null) return page.last().sequenceId
        require(page.any { it.sequenceId == firstConflictSequence })
        return maxOf(current, firstConflictSequence - 1L)
    }
}
