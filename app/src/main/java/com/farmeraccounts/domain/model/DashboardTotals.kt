package com.farmeraccounts.domain.model

data class DashboardTotals(
    val totalReceivablesPaise: Long,
    val totalAdvancesPaise: Long,
    val totalWorkRevenuePaise: Long,
    val totalPaymentsCollectedPaise: Long,
    val totalExpensesPaise: Long,
    val netCashFlowPaise: Long
)

