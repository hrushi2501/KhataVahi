package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.CustomerBalance
import com.farmeraccounts.domain.model.CustomerLedger
import com.farmeraccounts.domain.model.LedgerEntry
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.WorkRepository
import kotlinx.coroutines.flow.first

class GetCustomerLedgerUseCase(
    private val customerRepository: CustomerRepository,
    private val workRepository: WorkRepository,
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(customerId: String): CustomerLedger {
        val customer = customerRepository.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer with id $customerId not found")

        val workEntries = workRepository.getWorkEntriesForCustomer(customerId).first()
        val payments = paymentRepository.getPaymentsForCustomer(customerId).first()

        data class TimelineItem(
            val date: Long,
            val createdAt: Long,
            val work: WorkEntry? = null,
            val payment: Payment? = null
        )

        val items = ArrayList<TimelineItem>(workEntries.size + payments.size)
        for (w in workEntries) {
            items.add(TimelineItem(date = w.date, createdAt = w.createdAt, work = w))
        }
        for (p in payments) {
            items.add(TimelineItem(date = p.paymentDate, createdAt = p.createdAt, payment = p))
        }

        // Sort ascending by transaction date, then by creation timestamp
        items.sortWith(compareBy({ it.date }, { it.createdAt }))

        var runningBalance = 0L
        var totalWork = 0L
        var totalPaid = 0L
        val ledgerEntries = ArrayList<LedgerEntry>(items.size)

        for (item in items) {
            if (item.work != null) {
                val w = item.work
                totalWork += w.totalPaise
                runningBalance += w.totalPaise
                ledgerEntries.add(
                    LedgerEntry.Work(
                        id = w.id,
                        date = w.date,
                        description = "${com.farmeraccounts.core.util.DisplayLanguage.workType(w.workType)} (${w.quantityUnit.bilingualTitle()})",
                        notes = w.notes,
                        runningBalancePaise = runningBalance,
                        workType = w.workType,
                        quantityScaled = w.quantityScaled,
                        quantityUnit = w.quantityUnit,
                        ratePerUnitPaise = w.ratePerUnitPaise,
                        amountPaise = w.totalPaise
                    )
                )
            } else if (item.payment != null) {
                val p = item.payment
                totalPaid += p.amountPaise
                runningBalance -= p.amountPaise
                ledgerEntries.add(
                    LedgerEntry.PaymentReceived(
                        id = p.id,
                        date = p.paymentDate,
                        description = com.farmeraccounts.core.util.DisplayLanguage.text("જમા રકમ (${p.paymentMethod.bilingualTitle()})", "Payment (${p.paymentMethod.bilingualTitle()})"),
                        notes = p.notes,
                        runningBalancePaise = runningBalance,
                        paymentMethod = p.paymentMethod,
                        amountPaise = p.amountPaise
                    )
                )
            }
        }

        val balance = CustomerBalance(
            customerId = customerId,
            totalWorkPaise = totalWork,
            totalPaidPaise = totalPaid,
            outstandingPaise = runningBalance
        )

        return CustomerLedger(
            customer = customer,
            balance = balance,
            entries = ledgerEntries
        )
    }
}

