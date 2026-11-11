package com.farmeraccounts.presentation.customers

import com.farmeraccounts.core.util.DisplayLanguage
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.databinding.ItemCustomerBinding

class CustomerAdapter(
    private val onCustomerClick: (CustomerWithBalanceUiItem) -> Unit
) : ListAdapter<CustomerWithBalanceUiItem, CustomerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CustomerWithBalanceUiItem) {
            val customer = item.customer
            val balance = item.balance
            val context = binding.root.context

            binding.tvCustomerName.text = customer.name

            val detailsList = mutableListOf<String>()
            if (!customer.nickname.isNullOrBlank()) detailsList.add("(${customer.nickname})")
            if (!customer.fatherName.isNullOrBlank()) detailsList.add(DisplayLanguage.text("પિતા: ${customer.fatherName}", "Father: ${customer.fatherName}"))
            if (!customer.village.isNullOrBlank()) detailsList.add(DisplayLanguage.text("ગામ: ${customer.village}", "Village: ${customer.village}"))

            binding.tvCustomerDetails.text = if (detailsList.isNotEmpty()) {
                detailsList.joinToString(" • ")
            } else {
                DisplayLanguage.text("ગામ નોંધાયેલ નથી", "No village info")
            }

            binding.tvCustomerPhone.text = customer.phone ?: ""
            binding.tvCustomerPhone.visibility = if (customer.phone.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE

            when {
                balance.isDue -> {
                    binding.tvBalanceStatus.text = context.getString(R.string.status_due)
                    binding.tvBalanceStatus.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                    binding.tvCustomerBalance.text = FinancialFormatters.formatPaiseToRupees(balance.outstandingPaise)
                    binding.tvCustomerBalance.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                }
                balance.isAdvance -> {
                    binding.tvBalanceStatus.text = context.getString(R.string.status_advance)
                    binding.tvBalanceStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.tvCustomerBalance.text = FinancialFormatters.formatPaiseToRupees(-balance.outstandingPaise)
                    binding.tvCustomerBalance.setTextColor(ContextCompat.getColor(context, R.color.primary))
                }
                else -> {
                    binding.tvBalanceStatus.text = context.getString(R.string.status_settled)
                    binding.tvBalanceStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    binding.tvCustomerBalance.text = "₹ 0.00"
                    binding.tvCustomerBalance.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }

            binding.root.setOnClickListener {
                onCustomerClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CustomerWithBalanceUiItem>() {
        override fun areItemsTheSame(
            oldItem: CustomerWithBalanceUiItem,
            newItem: CustomerWithBalanceUiItem
        ): Boolean = oldItem.customer.id == newItem.customer.id

        override fun areContentsTheSame(
            oldItem: CustomerWithBalanceUiItem,
            newItem: CustomerWithBalanceUiItem
        ): Boolean = oldItem == newItem
    }
}

