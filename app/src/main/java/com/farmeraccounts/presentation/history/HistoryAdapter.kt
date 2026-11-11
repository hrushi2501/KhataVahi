package com.farmeraccounts.presentation.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.farmeraccounts.databinding.ItemHistoryBinding

data class HistoryItem(val id: String, val title: String, val detail: String, val canDelete: Boolean)

class HistoryAdapter(private val onActions: (HistoryItem, View) -> Unit) : ListAdapter<HistoryItem, HistoryAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.tvTitle.text = item.title
            binding.tvDetail.text = item.detail
            binding.btnRecordActions.visibility = if (item.canDelete) View.VISIBLE else View.GONE
            binding.btnRecordActions.contentDescription = binding.root.context.getString(com.farmeraccounts.R.string.record_actions_for_format, item.title)
            binding.btnRecordActions.setOnClickListener { onActions(item, it) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(old: HistoryItem, new: HistoryItem) = old.id == new.id
        override fun areContentsTheSame(old: HistoryItem, new: HistoryItem) = old == new
    }
}
