package com.farmeraccounts.domain.model

/**
 * Customer balance state.
 * Per project rules:
 * - Customer outstanding = active work totals - active payment totals.
 * - Supports customer advances (outstandingPaise < 0).
 * - Soft-deleted records must not affect balances.
 */
data class CustomerBalance(
    val customerId: String,
    val totalWorkPaise: Long,
    val totalPaidPaise: Long,
    val outstandingPaise: Long
) {
    val isAdvance: Boolean get() = outstandingPaise < 0L
    val isDue: Boolean get() = outstandingPaise > 0L
    val isSettled: Boolean get() = outstandingPaise == 0L
}

