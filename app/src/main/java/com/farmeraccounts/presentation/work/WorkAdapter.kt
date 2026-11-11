package com.farmeraccounts.presentation.work

import com.farmeraccounts.core.util.DisplayLanguage
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.core.util.ScaledQuantity
import com.farmeraccounts.databinding.ItemWorkEntryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkAdapter(
    private val onWorkClick: ((WorkEntryUiItem) -> Unit)? = null,
    private val onWorkActions: ((WorkEntryUiItem, View) -> Unit)? = null
) : ListAdapter<WorkEntryUiItem, WorkAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWorkEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WorkEntryUiItem) {
            val entry = item.workEntry

            binding.tvCustomerName.text = item.customerName
            binding.tvTotalAmount.text = FinancialFormatters.formatPaiseToRupees(entry.totalPaise)

            val qtyFormatted = ScaledQuantity.formatScaled(entry.quantityScaled)
            val rateFormatted = FinancialFormatters.formatPaiseToRupees(entry.ratePerUnitPaise)
            binding.tvWorkDetails.text = "${com.farmeraccounts.core.util.DisplayLanguage.workType(entry.workType)} • $qtyFormatted ${entry.quantityUnit.bilingualTitle()} @ $rateFormatted"

            val dateStr = dateFormat.format(Date(entry.date))
            binding.tvTractorAndDate.text = "$dateStr • ${item.tractorName}"

            if (!entry.notes.isNullOrBlank()) {
                binding.tvNotes.text = DisplayLanguage.text("નોંધ: ${entry.notes}", "Note: ${entry.notes}")
                binding.tvNotes.visibility = View.VISIBLE
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            binding.btnRecordActions.contentDescription = binding.root.context.getString(com.farmeraccounts.R.string.record_actions_for_format, item.customerName)
            binding.btnRecordActions.setOnClickListener { onWorkActions?.invoke(item, it) }
            binding.root.setOnClickListener {
                onWorkClick?.invoke(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<WorkEntryUiItem>() {
        override fun areItemsTheSame(oldItem: WorkEntryUiItem, newItem: WorkEntryUiItem): Boolean =
            oldItem.workEntry.id == newItem.workEntry.id

        override fun areContentsTheSame(oldItem: WorkEntryUiItem, newItem: WorkEntryUiItem): Boolean =
            oldItem == newItem
    }
}

