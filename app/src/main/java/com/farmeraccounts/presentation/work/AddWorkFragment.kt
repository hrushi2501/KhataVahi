package com.farmeraccounts.presentation.work

import com.farmeraccounts.core.util.DisplayLanguage
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.farmeraccounts.databinding.FragmentAddWorkBinding
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.model.WorkType
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.FormValidation
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddWorkFragment : Fragment() {

    private var _binding: FragmentAddWorkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddWorkViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private var preselectedCustomerId: String? = null
    private var restoredTractorId: String? = null
    private var selectedCustomer: Customer? = null
    private var selectedTractor: Tractor? = null
    private var selectedWorkType: WorkType = WorkType.PLOWING
    private var selectedUnit: QuantityUnit = QuantityUnit.BIGHA
    private var selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH
    private var selectedDateTimestamp: Long = System.currentTimeMillis()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preselectedCustomerId = savedInstanceState?.getString("draft_customer") ?: arguments?.getString(ARG_CUSTOMER_ID)
        restoredTractorId = savedInstanceState?.getString("draft_tractor")
        selectedWorkType = savedInstanceState?.getString("draft_work_type")?.let { WorkType.valueOf(it) } ?: selectedWorkType
        selectedUnit = savedInstanceState?.getString("draft_unit")?.let { runCatching { QuantityUnit.valueOf(it) }.getOrDefault(QuantityUnit.BIGHA) } ?: selectedUnit
        selectedPaymentMethod = savedInstanceState?.getString("draft_payment_method")?.let { PaymentMethod.valueOf(it) } ?: selectedPaymentMethod
        selectedDateTimestamp = savedInstanceState?.getLong("draft_date", selectedDateTimestamp) ?: selectedDateTimestamp
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddWorkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_add_work))

        setupStaticDropdowns()
        setupDatePicker()
        setupCalculationWatchers()
        setupSaveButton()
        binding.etQuantity.doAfterTextChanged { binding.tilQuantity.error = null }
        binding.etRate.doAfterTextChanged { binding.tilRate.error = null }
        binding.etInitialPayment.doAfterTextChanged { binding.tilInitialPayment.error = null }
        observeFormData()
    }

    private fun setupStaticDropdowns() {
        // Work Types
        val workTypes = WorkType.entries.toTypedArray()
        val workTypeLabels = workTypes.map { it.bilingualTitle() }
        val workAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, workTypeLabels)
        binding.actvWorkType.setAdapter(workAdapter)
        binding.actvWorkType.setText(selectedWorkType.bilingualTitle(), false)
        binding.actvWorkType.setOnItemClickListener { _, _, position, _ ->
            selectedWorkType = workTypes[position]
        }

        // Units
        val units = QuantityUnit.entries.toTypedArray()
        val unitLabels = units.map { it.bilingualTitle() }
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, unitLabels)
        binding.actvUnit.setAdapter(unitAdapter)
        binding.actvUnit.setText(selectedUnit.bilingualTitle(), false)
        binding.actvUnit.setOnItemClickListener { _, _, position, _ ->
            selectedUnit = units[position]
            updateCalculations()
        }

        // Payment Methods
        val methods = PaymentMethod.entries.toTypedArray()
        val methodLabels = methods.map { it.bilingualTitle() }
        val methodAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methodLabels)
        binding.actvPaymentMethod.setAdapter(methodAdapter)
        binding.actvPaymentMethod.setText(selectedPaymentMethod.bilingualTitle(), false)
        binding.actvPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            selectedPaymentMethod = methods[position]
        }
    }

    private fun setupDatePicker() {
        binding.etDate.setText(dateFormat.format(Date(selectedDateTimestamp)))

        binding.etDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDateTimestamp = calendar.timeInMillis
                    binding.etDate.setText(dateFormat.format(Date(selectedDateTimestamp)))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupCalculationWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCalculations()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etQuantity.addTextChangedListener(watcher)
        binding.etRate.addTextChangedListener(watcher)
        binding.etInitialPayment.addTextChangedListener(watcher)
    }

    private fun updateCalculations() {
        val qtyStr = binding.etQuantity.text?.toString()
        val rateStr = binding.etRate.text?.toString()
        val initialPayStr = binding.etInitialPayment.text?.toString()

        val totalPaise = runCatching { viewModel.calculateTotalPaise(qtyStr, rateStr) }.getOrDefault(0L)
        binding.tvCalculatedTotal.text = FinancialFormatters.formatPaiseToRupees(totalPaise)

        val remainingDue = runCatching { viewModel.calculateRemainingDue(totalPaise, initialPayStr) }.getOrDefault(totalPaise)
        val remainingStr = FinancialFormatters.formatPaiseToRupees(remainingDue)
        binding.tvRemainingDue.text = getString(R.string.remaining_due_format, remainingStr)
    }

    private fun setupSaveButton() {
        binding.actvCustomer.doAfterTextChanged { text ->
            val selected = selectedCustomer
            if (selected != null && text.toString() != selected.getDisplayName() &&
                text.toString() != com.farmeraccounts.presentation.customers.CustomerSearchAdapter.label(selected)) {
                selectedCustomer = null
                preselectedCustomerId = null
            }
        }
        binding.btnSaveWork.setOnClickListener {
            if (viewModel.isSaving.value || viewModel.saveCompleted.value) return@setOnClickListener
            if (!validateForm()) return@setOnClickListener
            binding.tvSaveError.visibility = View.GONE
            val customerId = selectedCustomer?.id ?: ""
            val tractorId = selectedTractor?.id ?: ""
            val qtyStr = binding.etQuantity.text?.toString() ?: ""
            val rateStr = binding.etRate.text?.toString() ?: ""
            val initialPayStr = binding.etInitialPayment.text?.toString()
            val notes = binding.etNotes.text?.toString()

            viewModel.saveWork(
                customerId = customerId,
                tractorId = tractorId,
                workType = selectedWorkType.name,
                date = selectedDateTimestamp,
                quantityStr = qtyStr,
                unit = selectedUnit,
                rateStr = rateStr,
                initialPaymentStr = initialPayStr,
                paymentMethod = if (!initialPayStr.isNullOrBlank()) selectedPaymentMethod else null,
                notes = notes
            ) {}
        }
    }

    private fun observeFormData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.btnSaveWork.isEnabled = !saving
                        binding.btnSaveWork.setText(if (saving) R.string.saving_on_phone else R.string.btn_save_work)
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
                    viewModel.formData.collect { data ->
                        // Customers dropdown
                        val customerAdapter = com.farmeraccounts.presentation.customers.CustomerSearchAdapter(requireContext(), data.customers)
                        binding.actvCustomer.setAdapter(customerAdapter)

                        // Preselect if requested
                        if (selectedCustomer == null && preselectedCustomerId != null) {
                            val match = data.customers.find { it.id == preselectedCustomerId }
                            if (match != null) {
                                selectedCustomer = match
                                binding.actvCustomer.setText(match.getDisplayName(), false)
                            }
                        }

                        binding.actvCustomer.setOnItemClickListener { _, _, position, _ ->
                            selectedCustomer = customerAdapter.getItem(position)
                            binding.tilCustomer.error = null
                        }

                        // Tractors dropdown
                        val tractorLabels = data.tractors.map { it.localizedDisplayName() }
                        val tractorAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tractorLabels)
                        binding.actvTractor.setAdapter(tractorAdapter)

                        if (selectedTractor == null && data.tractors.isNotEmpty()) {
                            selectedTractor = data.tractors.find { it.id == restoredTractorId }
                                ?: data.tractors.firstOrNull { it.type == com.farmeraccounts.domain.model.TractorType.BIG }
                                ?: data.tractors.first()
                            binding.actvTractor.setText(selectedTractor?.localizedDisplayName(), false)
                        }

                        binding.actvTractor.setOnItemClickListener { _, _, position, _ ->
                            selectedTractor = data.tractors[position]
                            binding.tilTractor.error = null
                        }
                    }
                }

                launch {
                    viewModel.saveState.collect { error ->
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
        binding.tilTractor.error = if (selectedTractor == null) {
            if (firstInvalid == null) firstInvalid = binding.actvTractor
            getString(R.string.error_choose_tractor)
        } else null
        binding.tilQuantity.error = if (!FormValidation.positiveQuantity(binding.etQuantity.text?.toString())) {
            if (firstInvalid == null) firstInvalid = binding.etQuantity
            getString(R.string.error_quantity)
        } else null
        binding.tilRate.error = if (!FormValidation.positiveAmount(binding.etRate.text?.toString())) {
            if (firstInvalid == null) firstInvalid = binding.etRate
            getString(R.string.error_amount)
        } else null
        binding.tilInitialPayment.error = if (!FormValidation.optionalPayment(binding.etInitialPayment.text?.toString())) {
            if (firstInvalid == null) firstInvalid = binding.etInitialPayment
            getString(R.string.error_initial_payment)
        } else null
        if (firstInvalid == null && runCatching {
                viewModel.calculateTotalPaise(binding.etQuantity.text?.toString(), binding.etRate.text?.toString())
            }.getOrNull()?.let { it > 0L } != true) {
            binding.tilRate.error = getString(R.string.error_total_range)
            firstInvalid = binding.etRate
        }
        firstInvalid?.requestFocus()
        return firstInvalid == null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draft_customer", selectedCustomer?.id ?: preselectedCustomerId)
        outState.putString("draft_tractor", selectedTractor?.id ?: restoredTractorId)
        outState.putString("draft_work_type", selectedWorkType.name)
        outState.putString("draft_unit", selectedUnit.name)
        outState.putString("draft_payment_method", selectedPaymentMethod.name)
        outState.putLong("draft_date", selectedDateTimestamp)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "arg_customer_id"

        fun newInstance(preselectedCustomerId: String? = null) = AddWorkFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CUSTOMER_ID, preselectedCustomerId)
            }
        }
    }
}

