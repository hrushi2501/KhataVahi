package com.farmeraccounts.core.util

import android.content.Intent
import com.farmeraccounts.domain.model.CustomerLedger
import com.farmeraccounts.domain.model.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates text in the selected app language for sharing customer ledger accounts via WhatsApp (ACTION_SEND).
 */
object LedgerShareFormatter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    fun formatLedgerForSharing(ledger: CustomerLedger): String {
        val sb = StringBuilder()
        val customer = ledger.customer
        val balance = ledger.balance

        sb.appendLine("🌾 *KhataVahi*")
        sb.appendLine(DisplayLanguage.text("📋 *હિસાબ પત્રક*", "📋 *Ledger statement*"))
        sb.appendLine("───────────────────────────")
        sb.appendLine("👤 ${DisplayLanguage.text("ગ્રાહક", "Customer")}: ${customer.name}")
        if (!customer.nickname.isNullOrBlank()) {
            sb.appendLine("   ${DisplayLanguage.text("ઉપનામ", "Nickname")}: ${customer.nickname}")
        }
        if (!customer.village.isNullOrBlank()) {
            sb.appendLine("   ${DisplayLanguage.text("ગામ", "Village")}: ${customer.village}")
        }
        if (!customer.phone.isNullOrBlank()) {
            sb.appendLine("   ${DisplayLanguage.text("મોબાઈલ", "Phone")}: ${customer.phone}")
        }
        sb.appendLine("📅 ${DisplayLanguage.text("તારીખ", "Date")}: ${dateFormat.format(Date())}")
        sb.appendLine("───────────────────────────")

        sb.appendLine("${DisplayLanguage.text("કુલ કામ", "Total work")}: ${FinancialFormatters.formatPaiseForLanguage(balance.totalWorkPaise)}")
        sb.appendLine("${DisplayLanguage.text("કુલ મળ્યા", "Total received")}: ${FinancialFormatters.formatPaiseForLanguage(balance.totalPaidPaise)}")

        when {
            balance.isDue -> {
                sb.appendLine(DisplayLanguage.text("❗ *બાકી રકમ:*", "❗ *Amount due:*"))
                sb.appendLine("👉 *${FinancialFormatters.formatPaiseForLanguage(balance.outstandingPaise)}*")
            }
            balance.isAdvance -> {
                sb.appendLine(DisplayLanguage.text("✅ *એડવાન્સ જમા રકમ:*", "✅ *Advance balance:*"))
                sb.appendLine("👉 *${FinancialFormatters.formatPaiseForLanguage(-balance.outstandingPaise)}*")
            }
            else -> {
                sb.appendLine("✅ *${DisplayLanguage.text("ખાતું સરભર છે", "Account settled")} — ${FinancialFormatters.formatPaiseForLanguage(0L)}*")
            }
        }

        if (ledger.entries.isNotEmpty()) {
            sb.appendLine("───────────────────────────")
            sb.appendLine(DisplayLanguage.text("📝 *લેવડ-દેવડ વિગત:*", "📝 *Transactions:*"))
            ledger.entries.takeLast(10).forEachIndexed { index, entry ->
                val dateStr = dateFormat.format(Date(entry.date))
                when (entry) {
                    is LedgerEntry.Work -> {
                        val amtStr = FinancialFormatters.formatPaiseForLanguage(entry.amountPaise)
                        sb.appendLine("${index + 1}. $dateStr — ${entry.description}")
                        sb.appendLine("   ${DisplayLanguage.text("બાકી", "Work")}: +$amtStr")
                    }
                    is LedgerEntry.PaymentReceived -> {
                        val amtStr = FinancialFormatters.formatPaiseForLanguage(entry.amountPaise)
                        sb.appendLine("${index + 1}. $dateStr — ${entry.description}")
                        sb.appendLine("   ${DisplayLanguage.text("જમા", "Paid")}: -$amtStr")
                    }
                }
            }
        }

        sb.appendLine("───────────────────────────")
        sb.append(DisplayLanguage.text("આ હિસાબ KhataVahi એપ દ્વારા ગણતરી કરેલ છે.", "Calculated by KhataVahi."))
        return sb.toString()
    }

    fun createShareIntent(messageText: String): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, messageText)
        }
        return Intent.createChooser(intent, DisplayLanguage.text("હિસાબ શેર કરો", "Share account"))
    }
}

