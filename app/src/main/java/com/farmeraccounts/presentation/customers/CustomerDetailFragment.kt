package com.farmeraccounts.presentation.customers

import com.farmeraccounts.core.util.DisplayLanguage
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.core.util.LedgerShareFormatter
import com.farmeraccounts.databinding.FragmentCustomerDetailBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.RecordDeletionViewModel
import com.farmeraccounts.presentation.base.RecordKind
import com.farmeraccounts.presentation.base.DeleteRecordDialog
import com.farmeraccounts.presentation.base.showRecordActions
import kotlinx.coroutines.launch

class CustomerDetailFragment : Fragment() {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomerDetailViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private var customerId: String = ""
    private val deletions: RecordDeletionViewModel by viewModels {
        ViewModelFactory((requireActivity().application as FarmerAccountsApp).container)
    }

    private lateinit var ledgerAdapter: CustomerLedgerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customerId = arguments?.getString(ARG_CUSTOMER_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_customer_detail))

        setupRecyclerView()
        setupActions()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLedger(customerId)
    }

    private fun setupRecyclerView() {
        ledgerAdapter = CustomerLedgerAdapter { entry, anchor ->
            val kind = if (entry is com.farmeraccounts.domain.model.LedgerEntry.Work) RecordKind.WORK else RecordKind.PAYMENT
            showRecordActions(anchor, kind, entry.id, entry.description)
        }
        binding.rvLedger.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ledgerAdapter
        }
    }

    private fun setupActions() {
        binding.btnRetryLedger.setOnClickListener { viewModel.loadLedger(customerId) }
        binding.btnDeleteCustomer.setOnClickListener {
            val customer = viewModel.uiState.value.ledger?.customer ?: return@setOnClickListener
            DeleteRecordDialog.show(this, RecordKind.CUSTOMER, customer.id, customer.name)
        }
        val navigator = activity as? Navigator

        binding.btnEditCustomer.setOnClickListener {
            navigator?.navigateToEditCustomer(customerId)
        }

        binding.btnAddWorkForCustomer.setOnClickListener {
            navigator?.navigateToAddWork(customerId)
        }

        binding.btnAddPaymentForCustomer.setOnClickListener {
            navigator?.navigateToRecordPayment(customerId)
        }

        binding.btnShareWhatsApp.setOnClickListener {
            val ledger = viewModel.uiState.value.ledger
            if (ledger != null) {
                val shareText = LedgerShareFormatter.formatLedgerForSharing(ledger)
                val shareIntent = LedgerShareFormatter.createShareIntent(shareText)
                try {
                    startActivity(shareIntent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(requireContext(), R.string.no_share_app, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), R.string.ledger_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    deletions.state.collect { result ->
                        if (result.deletedId != null) {
                            (childFragmentManager.findFragmentByTag("delete_record") as? androidx.fragment.app.DialogFragment)?.dismiss()
                            Toast.makeText(requireContext(), R.string.record_removed, Toast.LENGTH_SHORT).show()
                            val removedCustomer = result.deletedKind == RecordKind.CUSTOMER
                            deletions.prepare()
                            if (removedCustomer) (activity as? Navigator)?.navigateBack() else viewModel.loadLedger(customerId)
                        }
                    }
                }
                viewModel.uiState.collect { state ->
                    val ready = !state.isLoading && state.ledger != null && state.errorMessage == null
                    binding.ledgerScroll.visibility = if (ready) View.VISIBLE else View.GONE
                    binding.layoutLedgerStatus.visibility = if (ready) View.GONE else View.VISIBLE
                    binding.skeletonLoading.root.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnRetryLedger.visibility = if (!state.isLoading && !ready) View.VISIBLE else View.GONE
                    binding.tvLedgerStatus.setText(when {
                        state.isLoading -> R.string.ledger_loading
                        state.errorMessage != null -> R.string.ledger_load_failed
                        else -> R.string.ledger_not_found
                    })
                    if (!ready) return@collect
                    val ledger = state.ledger ?: return@collect
                    val customer = ledger.customer
                    val balance = ledger.balance
                    val context = requireContext()

                    binding.tvCustomerName.text = customer.name

                    val infoParts = mutableListOf<String>()
                    if (!customer.nickname.isNullOrBlank()) infoParts.add("(${customer.nickname})")
                    if (!customer.fatherName.isNullOrBlank()) infoParts.add(DisplayLanguage.text("પિતા: ${customer.fatherName}", "Father: ${customer.fatherName}"))
                    if (!customer.village.isNullOrBlank()) infoParts.add(DisplayLanguage.text("ગામ: ${customer.village}", "Village: ${customer.village}"))
                    binding.tvCustomerInfo.text = infoParts.joinToString(" • ")

                    if (!customer.phone.isNullOrBlank()) {
                        binding.tvPhone.text = customer.phone
                        binding.btnCall.setOnClickListener {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                            try {
                                startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(requireContext(), R.string.no_phone_app, Toast.LENGTH_LONG).show()
                            }
                        }
                        binding.layoutPhone.visibility = View.VISIBLE
                    } else {
                        binding.layoutPhone.visibility = View.GONE
                    }

                    // Balance overview
                    binding.tvTotalWork.text = FinancialFormatters.formatPaiseToRupees(balance.totalWorkPaise)
                    binding.tvTotalPaid.text = FinancialFormatters.formatPaiseToRupees(balance.totalPaidPaise)

                    when {
                        balance.isDue -> {
                            binding.tvBalanceTitle.text = getString(R.string.status_due)
                            binding.tvNetBalance.text = FinancialFormatters.formatPaiseForLanguage(balance.outstandingPaise)
                            binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                        }
                        balance.isAdvance -> {
                            binding.tvBalanceTitle.text = getString(R.string.status_advance)
                            binding.tvNetBalance.text = FinancialFormatters.formatPaiseForLanguage(-balance.outstandingPaise)
                            binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, R.color.primary))
                        }
                        else -> {
                            binding.tvBalanceTitle.text = getString(R.string.status_settled)
                            binding.tvNetBalance.text = FinancialFormatters.formatPaiseForLanguage(0L)
                            binding.tvNetBalance.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        }
                    }

                    // Ledger items
                    ledgerAdapter.submitList(ledger.entries)
                    binding.tvEmptyLedger.visibility =
                        if (ledger.entries.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "arg_customer_id"

        fun newInstance(customerId: String) = CustomerDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CUSTOMER_ID, customerId)
            }
        }
    }
}

