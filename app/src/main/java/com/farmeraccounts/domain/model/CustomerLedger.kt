package com.farmeraccounts.domain.model

sealed class LedgerEntry {
    abstract val id: String
    abstract val date: Long
    abstract val description: String
    abstract val notes: String?
    abstract val runningBalancePaise: Long

    data class Work(
        override val id: String,
        override val date: Long,
        override val description: String,
        override val notes: String?,
        override val runningBalancePaise: Long,
        val workType: String,
        val quantityScaled: Long,
        val quantityUnit: QuantityUnit,
        val ratePerUnitPaise: Long,
        val amountPaise: Long
    ) : LedgerEntry()

    data class PaymentReceived(
        override val id: String,
        override val date: Long,
        override val description: String,
        override val notes: String?,
        override val runningBalancePaise: Long,
        val paymentMethod: PaymentMethod,
        val amountPaise: Long
    ) : LedgerEntry()
}

data class CustomerLedger(
    val customer: Customer,
    val balance: CustomerBalance,
    val entries: List<LedgerEntry>
)

