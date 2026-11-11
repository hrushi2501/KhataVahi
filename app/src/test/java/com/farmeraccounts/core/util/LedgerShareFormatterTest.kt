package com.farmeraccounts.core.util

import android.content.Intent
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.CustomerBalance
import com.farmeraccounts.domain.model.CustomerLedger
import com.farmeraccounts.domain.model.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.Locale
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LedgerShareFormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun useEnglish() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }


    private val sampleCustomer = Customer(
        id = UUID.randomUUID().toString(),
        name = "રમેશભાઈ પટેલ (Rameshbhai Patel)",
        nickname = "મોટા (Mota)",
        fatherName = "ઈશ્વરભાઈ",
        village = "આણંદ (Anand)",
        phone = "9876543210",
        createdAt = 1000L,
        updatedAt = 1000L,
        version = 1,
        isDeleted = false
    )

    @Test
    fun `formatLedgerForSharing formats due balance in English and preserves customer content`() {
        val balance = CustomerBalance(
            customerId = sampleCustomer.id,
            totalWorkPaise = 2500000L, // 25,000 Rs
            totalPaidPaise = 1000000L, // 10,000 Rs
            outstandingPaise = 1500000L // 15,000 Rs due
        )

        val entries = listOf(
            LedgerEntry.Work(
                id = "w1",
                date = 1700000000000L,
                description = "ખેડ (Ploughing) — ૫.૦૦ વીઘા (Bigha)",
                notes = null,
                runningBalancePaise = 2500000L,
                workType = "ખેડ (Ploughing)",
                quantityScaled = 5000L,
                quantityUnit = com.farmeraccounts.domain.model.QuantityUnit.BIGHA,
                ratePerUnitPaise = 50000L,
                amountPaise = 2500000L
            ),
            LedgerEntry.PaymentReceived(
                id = "p1",
                date = 1700100000000L,
                description = "રોકડ ચૂકવણી (Cash)",
                notes = null,
                runningBalancePaise = 1500000L,
                paymentMethod = com.farmeraccounts.domain.model.PaymentMethod.CASH,
                amountPaise = 1000000L
            )
        )

        val ledger = CustomerLedger(
            customer = sampleCustomer,
            balance = balance,
            entries = entries
        )

        val formatted = LedgerShareFormatter.formatLedgerForSharing(ledger)

        assertTrue(formatted.contains("*KhataVahi*"))
        assertTrue(formatted.contains(sampleCustomer.name))
        assertTrue(!formatted.contains("ગ્રાહક (Customer)"))
        assertTrue(!formatted.contains("કુલ કામ"))
        assertTrue(formatted.contains("આણંદ"))
        assertTrue(formatted.contains("9876543210"))
        assertTrue(formatted.contains("Total work: ₹ 25,000.00"))
        assertTrue(formatted.contains("Total received: ₹ 10,000.00"))
        assertTrue(formatted.contains("Amount due:"))
        assertTrue(formatted.contains("₹ 15,000.00"))
        assertTrue(formatted.contains("Work: +₹ 25,000.00"))
        assertTrue(formatted.contains("Paid: -₹ 10,000.00"))
    }

    @Test
    fun `formatLedgerForSharing formats customer advance clearly`() {
        val balance = CustomerBalance(
            customerId = sampleCustomer.id,
            totalWorkPaise = 500000L, // 5,000 Rs
            totalPaidPaise = 800000L, // 8,000 Rs
            outstandingPaise = -300000L // 3,000 Rs advance
        )

        val ledger = CustomerLedger(
            customer = sampleCustomer,
            balance = balance,
            entries = emptyList()
        )

        val formatted = LedgerShareFormatter.formatLedgerForSharing(ledger)

        assertTrue(formatted.contains("Advance balance:"))
        assertTrue(formatted.contains("₹ 3,000.00"))
    }

    @Test
    fun `formatLedgerForSharing formats settled balance when zero`() {
        val balance = CustomerBalance(
            customerId = sampleCustomer.id,
            totalWorkPaise = 1000000L,
            totalPaidPaise = 1000000L,
            outstandingPaise = 0L
        )

        val ledger = CustomerLedger(
            customer = sampleCustomer,
            balance = balance,
            entries = emptyList()
        )

        val formatted = LedgerShareFormatter.formatLedgerForSharing(ledger)

        assertTrue(formatted.contains("Account settled — ₹ 0.00"))
    }

    @Test
    fun `Gujarati statement uses Gujarati labels and currency and preserves user text`() {
        Locale.setDefault(Locale.forLanguageTag("gu"))
        val customer = sampleCustomer.copy(name = "Ramesh Patel", village = "Anand")
        val ledger = CustomerLedger(
            customer = customer,
            balance = CustomerBalance(customer.id, 500000L, 800000L, -300000L),
            entries = emptyList()
        )

        val formatted = LedgerShareFormatter.formatLedgerForSharing(ledger)

        assertTrue(formatted.contains("ગ્રાહક: Ramesh Patel"))
        assertTrue(formatted.contains("ગામ: Anand"))
        assertTrue(formatted.contains("એડવાન્સ જમા રકમ:"))
        assertTrue(formatted.contains("₹ ૩,૦૦૦.૦૦"))
        assertTrue(!formatted.contains("Advance balance"))
        assertTrue(!formatted.contains("₹ 3,000.00"))
    }

    @Test
    fun `createShareIntent creates valid chooser intent with ACTION_SEND`() {
        val message = "Test Message"
        val intent = LedgerShareFormatter.createShareIntent(message)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_CHOOSER, intent.action)

        val targetIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("text/plain", targetIntent?.type)
        assertEquals(message, targetIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }
}
