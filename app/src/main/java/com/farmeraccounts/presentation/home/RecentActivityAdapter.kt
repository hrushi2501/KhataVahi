package com.farmeraccounts.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.databinding.ItemRecentActivityBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentActivityAdapter :
    ListAdapter<RecentActivityUiItem, RecentActivityAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentActivityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentActivityUiItem) {
            binding.tvActivityTitle.text = item.title
            val formattedDate = dateFormat.format(Date(item.timestamp))
            binding.tvActivitySubtitle.text = "$formattedDate • ${item.subtitle}"

            val amountStr = FinancialFormatters.formatPaiseToRupees(item.amountPaise)
            if (item.isPayment) {
                binding.tvActivityAmount.text = "-$amountStr"
                binding.tvActivityAmount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.primary)
                )
            } else {
                binding.tvActivityAmount.text = "+$amountStr"
                binding.tvActivityAmount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_primary)
                )
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecentActivityUiItem>() {
        override fun areItemsTheSame(oldItem: RecentActivityUiItem, newItem: RecentActivityUiItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecentActivityUiItem, newItem: RecentActivityUiItem): Boolean =
            oldItem == newItem
    }
}

