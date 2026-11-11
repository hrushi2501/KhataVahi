package com.farmeraccounts.presentation.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.core.util.DisplayLanguage
import com.farmeraccounts.databinding.FragmentHistoryBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.RecordDeletionViewModel
import com.farmeraccounts.presentation.base.RecordKind
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.showRecordActions
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val factory get() = ViewModelFactory((requireActivity().application as FarmerAccountsApp).container)
    private val model: HistoryViewModel by viewModels { factory }
    private val deletions: RecordDeletionViewModel by viewModels { factory }
    private val deleted get() = arguments?.getBoolean("deleted") == true
    private lateinit var adapter: HistoryAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? Navigator)?.setToolbarTitle(getString(if (deleted) R.string.deleted_history else R.string.title_expenses))
        binding.tvHistoryNote.visibility = if (deleted) View.VISIBLE else View.GONE
        binding.btnAddExpense.visibility = if (deleted) View.GONE else View.VISIBLE
        binding.btnAddExpense.setOnClickListener { (activity as? Navigator)?.navigateToRecordExpense() }
        binding.btnRetry.setOnClickListener { model.load(deleted) }
        adapter = HistoryAdapter { item, anchor -> showRecordActions(anchor, RecordKind.EXPENSE, item.id, item.title) }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
        model.load(deleted)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    deletions.state.collect { result ->
                        if (result.deletedId != null) {
                            (childFragmentManager.findFragmentByTag("delete_record") as? DialogFragment)?.dismiss()
                            Toast.makeText(requireContext(), R.string.record_removed, Toast.LENGTH_SHORT).show()
                            deletions.prepare()
                        }
                    }
                }
                launch {
                    model.state.collect { state ->
                        binding.skeletonLoading.root.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        binding.btnRetry.visibility = if (state.hasError) View.VISIBLE else View.GONE
                        val items = if (!deleted) state.expenses.map { expense ->
                            HistoryItem(expense.id, "${expense.category.bilingualTitle()} · ${FinancialFormatters.formatPaiseForLanguage(expense.amountPaise)}",
                                listOfNotNull(dateFormat.format(Date(expense.expenseDate)), expense.notes).joinToString("\n"), true)
                        } else state.deletedLogs.map { log ->
                            val snapshot = runCatching { JSONObject(log.snapshotJson) }.getOrNull()
                            val type = when (log.entityType.lowercase(Locale.ROOT)) {
                                "customer", "customers" -> getString(R.string.record_customer)
                                "work_entry", "work_entries", "work" -> getString(R.string.record_work)
                                "payment", "payments" -> getString(R.string.record_payment)
                                "tractor", "tractors" -> getString(R.string.record_tractor)
                                else -> getString(R.string.record_expense)
                            }
                            val rawName = snapshot?.optString("name").orEmpty().ifBlank {
                                snapshot?.optString("displayName").orEmpty().ifBlank { snapshot?.optString("display_name").orEmpty() }
                            }
                            val name = when (rawName) {
                                "મોટો ટ્રેક્ટર (Big Tractor)" -> com.farmeraccounts.domain.model.TractorType.BIG.bilingualTitle()
                                "નાનો ટ્રેક્ટર (Mini Tractor)" -> com.farmeraccounts.domain.model.TractorType.MINI.bilingualTitle()
                                else -> rawName
                            }.takeIf { it.isNotBlank() }
                            val work = (snapshot?.optString("workType").orEmpty().ifBlank { snapshot?.optString("work_type").orEmpty() }).takeIf { it.isNotBlank() }?.let(DisplayLanguage::workType)
                            val category = snapshot?.optString("category").orEmpty().takeIf { it.isNotBlank() }?.let { com.farmeraccounts.domain.model.ExpenseCategory.fromString(it).bilingualTitle() }
                            val amount = snapshot?.let { json ->
                                when { json.has("amountPaise") -> json.optLong("amountPaise"); json.has("totalPaise") -> json.optLong("totalPaise"); json.has("amount_paise") -> json.optLong("amount_paise"); json.has("total_paise") -> json.optLong("total_paise"); else -> null }
                            }?.let(FinancialFormatters::formatPaiseForLanguage)
                            HistoryItem(log.id, listOfNotNull(type, name ?: work ?: category, amount).joinToString(" · "),
                                getString(R.string.deleted_detail_format, dateFormat.format(Date(log.timestamp)), log.reason), false)
                        }
                        adapter.submitList(items)
                        binding.tvStatus.visibility = if (!state.isLoading && (state.hasError || items.isEmpty())) View.VISIBLE else View.GONE
                        binding.tvStatus.setText(if (state.hasError) R.string.history_load_failed else if (deleted) R.string.empty_deleted_history else R.string.empty_expenses)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(deleted: Boolean) = HistoryFragment().apply { arguments = Bundle().apply { putBoolean("deleted", deleted) } }
    }
}
