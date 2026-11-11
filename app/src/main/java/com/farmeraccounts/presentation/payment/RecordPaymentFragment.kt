package com.farmeraccounts.presentation.payment

import com.farmeraccounts.core.util.DisplayLanguage
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.databinding.FragmentRecordPaymentBinding
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.FormValidation
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordPaymentFragment : Fragment() {

    private var _binding: FragmentRecordPaymentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordPaymentViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private var preselectedCustomerId: String? = null
    private var preselectedWorkEntryId: String? = null

    private var selectedCustomer: Customer? = null
    private var selectedWorkEntry: WorkEntry? = null
    private var selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH
    private var selectedPaymentDate: Long = System.currentTimeMillis()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preselectedCustomerId = savedInstanceState?.getString("draft_customer") ?: arguments?.getString(ARG_CUSTOMER_ID)
        preselectedWorkEntryId = if (savedInstanceState != null) savedInstanceState.getString("draft_work") else arguments?.getString(ARG_WORK_ENTRY_ID)
        selectedPaymentMethod = savedInstanceState?.getString("draft_payment_method")?.let { PaymentMethod.valueOf(it) } ?: selectedPaymentMethod
        selectedPaymentDate = savedInstanceState?.getLong("draft_date", selectedPaymentDate) ?: selectedPaymentDate
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_add_payment))

        setupPaymentMethods()
        setupDatePicker()
        setupSaveButton()
        binding.actvCustomer.doAfterTextChanged { text ->
            val selected = selectedCustomer
            if (selected != null && text.toString() != selected.getDisplayName() &&
                text.toString() != com.farmeraccounts.presentation.customers.CustomerSearchAdapter.label(selected)) {
                selectedCustomer = null
                preselectedCustomerId = null
                selectedWorkEntry = null
                preselectedWorkEntryId = null
                binding.actvWorkEntry.setText("", false)
                viewModel.loadWorkForCustomer("")
            }
        }
        binding.etAmount.doAfterTextChanged { binding.tilAmount.error = null }
        observeData()
    }

    private fun setupPaymentMethods() {
        val methods = PaymentMethod.entries.toTypedArray()
        val labels = methods.map { it.bilingualTitle() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvPaymentMethod.setAdapter(adapter)
        binding.actvPaymentMethod.setText(selectedPaymentMethod.bilingualTitle(), false)
        binding.actvPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            selectedPaymentMethod = methods[position]
        }
    }

    private fun setupDatePicker() {
        binding.etPaymentDate.setText(dateFormat.format(Date(selectedPaymentDate)))

        binding.etPaymentDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedPaymentDate }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedPaymentDate = calendar.timeInMillis
                    binding.etPaymentDate.setText(dateFormat.format(Date(selectedPaymentDate)))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnSavePayment.setOnClickListener {
            if (viewModel.isSaving.value || viewModel.saveCompleted.value) return@setOnClickListener
            if (!validateForm()) return@setOnClickListener
            binding.tvSaveError.visibility = View.GONE
            val customerId = selectedCustomer?.id ?: ""
            val amountStr = binding.etAmount.text?.toString() ?: ""
            val notes = binding.etNotes.text?.toString()

            viewModel.savePayment(
                customerId = customerId,
                workEntryId = selectedWorkEntry?.id,
                amountStr = amountStr,
                paymentDate = selectedPaymentDate,
                paymentMethod = selectedPaymentMethod,
                notes = notes
            ) {}
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.btnSavePayment.isEnabled = !saving
                        binding.btnSavePayment.setText(if (saving) R.string.saving_on_phone else R.string.btn_save_payment)
                    }
                }
                launch {
                    viewModel.saveCompleted.collect { completed ->
                        if (completed) {
                            Toast.makeText(requireContext(), R.string.saved_on_phone, Toast.LENGTH_SHORT).show()
                            (activity as? Navigator)?.navigateBack()
                        }
                    }
                }

                launch {
                    viewModel.customers.collect { customerList ->
                        val adapter = com.farmeraccounts.presentation.customers.CustomerSearchAdapter(requireContext(), customerList)
                        binding.actvCustomer.setAdapter(adapter)

                        if (selectedCustomer == null && preselectedCustomerId != null) {
                            val match = customerList.find { it.id == preselectedCustomerId }
                            if (match != null) {
                                selectedCustomer = match
                                binding.actvCustomer.setText(match.getDisplayName(), false)
                                viewModel.loadWorkForCustomer(match.id)
                            }
                        }

                        binding.actvCustomer.setOnItemClickListener { _, _, position, _ ->
                            val c = adapter.getItem(position) ?: return@setOnItemClickListener
                            selectedCustomer = c
                            binding.tilCustomer.error = null
                            selectedWorkEntry = null
                            preselectedWorkEntryId = null
                            binding.actvWorkEntry.setText("", false)
                            viewModel.loadWorkForCustomer(c.id)
                        }
                    }
                }

                launch {
                    viewModel.customerWorkEntries.collect { entries ->
                        val options = mutableListOf<String>()
                        options.add(DisplayLanguage.text("સામાન્ય ખાતા જમા", "General Customer-Level Payment"))

                        entries.forEach { w ->
                            val amt = FinancialFormatters.formatPaiseToRupees(w.totalPaise)
                            options.add("${com.farmeraccounts.core.util.DisplayLanguage.workType(w.workType)} ($amt)")
                        }

                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
                        binding.actvWorkEntry.setAdapter(adapter)
                        selectedWorkEntry = entries.find { it.id == (selectedWorkEntry?.id ?: preselectedWorkEntryId) }
                        val selectedIndex = entries.indexOfFirst { it.id == selectedWorkEntry?.id } + 1
                        binding.actvWorkEntry.setText(options[selectedIndex], false)

                        binding.actvWorkEntry.setOnItemClickListener { _, _, position, _ ->
                            preselectedWorkEntryId = null
                            selectedWorkEntry = if (position > 0) entries[position - 1] else null
                        }
                    }
                }

                launch {
                    viewModel.saveError.collect { error ->
                        error?.let {
                            binding.tvSaveError.setText(R.string.save_failed)
                            binding.tvSaveError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun validateForm(): Boolean {
        var firstInvalid: View? = null
        binding.tilCustomer.error = if (selectedCustomer == null) {
            if (firstInvalid == null) firstInvalid = binding.actvCustomer
            getString(R.string.error_choose_customer)
        } else null
        binding.tilAmount.error = if (!FormValidation.positiveAmount(binding.etAmount.text?.toString())) {
            if (firstInvalid == null) firstInvalid = binding.etAmount
            getString(R.string.error_amount)
        } else null
        firstInvalid?.requestFocus()
        return firstInvalid == null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draft_customer", selectedCustomer?.id ?: preselectedCustomerId)
        outState.putString("draft_work", selectedWorkEntry?.id)
        outState.putString("draft_payment_method", selectedPaymentMethod.name)
        outState.putLong("draft_date", selectedPaymentDate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "arg_customer_id"
        private const val ARG_WORK_ENTRY_ID = "arg_work_entry_id"

        fun newInstance(customerId: String? = null, workEntryId: String? = null) = RecordPaymentFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CUSTOMER_ID, customerId)
                putString(ARG_WORK_ENTRY_ID, workEntryId)
            }
        }
    }
}

