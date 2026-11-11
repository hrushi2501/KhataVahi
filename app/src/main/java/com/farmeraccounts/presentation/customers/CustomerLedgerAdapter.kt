package com.farmeraccounts.presentation.customers

import com.farmeraccounts.core.util.DisplayLanguage
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.databinding.ItemLedgerEntryBinding
import com.farmeraccounts.domain.model.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomerLedgerAdapter(private val onEntryActions: (LedgerEntry, View) -> Unit) :
    ListAdapter<LedgerEntry, CustomerLedgerAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLedgerEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemLedgerEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LedgerEntry) {
            binding.btnRecordActions.contentDescription = binding.root.context.getString(com.farmeraccounts.R.string.record_actions_for_format, item.description)
            binding.btnRecordActions.setOnClickListener { onEntryActions(item, it) }
            val context = binding.root.context
            val dateStr = dateFormat.format(Date(item.date))

            binding.tvEntryDate.text = dateStr
            binding.tvEntryDescription.text = item.description

            if (!item.notes.isNullOrBlank()) {
                binding.tvEntryNotes.text = DisplayLanguage.text("નોંધ: ${item.notes}", "Note: ${item.notes}")
                binding.tvEntryNotes.visibility = View.VISIBLE
            } else {
                binding.tvEntryNotes.visibility = View.GONE
            }

            when (item) {
                is LedgerEntry.Work -> {
                    val amountStr = FinancialFormatters.formatPaiseToRupees(item.amountPaise)
                    binding.tvEntryAmount.text = DisplayLanguage.text("+$amountStr (બાકી)", "+$amountStr (Work)")
                    binding.tvEntryAmount.setTextColor(
                        ContextCompat.getColor(context, R.color.text_primary)
                    )
                }
                is LedgerEntry.PaymentReceived -> {
                    val amountStr = FinancialFormatters.formatPaiseToRupees(item.amountPaise)
                    binding.tvEntryAmount.text = DisplayLanguage.text("-$amountStr (જમા)", "-$amountStr (Paid)")
                    binding.tvEntryAmount.setTextColor(
                        ContextCompat.getColor(context, R.color.primary)
                    )
                }
            }

            val runningBal = item.runningBalancePaise
            val balText = if (runningBal >= 0) {
                "${context.getString(R.string.running_balance_prefix)}${FinancialFormatters.formatPaiseToRupees(runningBal)} (${context.getString(R.string.status_due)})"
            } else {
                "${context.getString(R.string.running_balance_prefix)}${FinancialFormatters.formatPaiseToRupees(-runningBal)} (${context.getString(R.string.status_advance)})"
            }
            binding.tvRunningBalance.text = balText
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LedgerEntry>() {
        override fun areItemsTheSame(oldItem: LedgerEntry, newItem: LedgerEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LedgerEntry, newItem: LedgerEntry): Boolean =
            oldItem == newItem
    }
}

