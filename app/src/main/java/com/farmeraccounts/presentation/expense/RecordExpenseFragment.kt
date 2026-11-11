package com.farmeraccounts.presentation.expense

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
import com.farmeraccounts.databinding.FragmentRecordExpenseBinding
import com.farmeraccounts.domain.model.ExpenseCategory
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.FormValidation
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordExpenseFragment : Fragment() {

    private var _binding: FragmentRecordExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordExpenseViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private var selectedCategory: ExpenseCategory = ExpenseCategory.DIESEL
    private var restoredTractorId: String? = null
    private var selectedTractor: Tractor? = null
    private var selectedExpenseDate: Long = System.currentTimeMillis()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredTractorId = savedInstanceState?.getString("draft_tractor")
        selectedCategory = savedInstanceState?.getString("draft_category")?.let { ExpenseCategory.valueOf(it) } ?: selectedCategory
        selectedExpenseDate = savedInstanceState?.getLong("draft_date", selectedExpenseDate) ?: selectedExpenseDate
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_add_expense))

        setupCategoryDropdown()
        setupDatePicker()
        setupSaveButton()
        binding.etAmount.doAfterTextChanged { binding.tilAmount.error = null }
        observeTractors()
    }

    private fun setupCategoryDropdown() {
        val categories = ExpenseCategory.entries.toTypedArray()
        val labels = categories.map { it.bilingualTitle() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvCategory.setAdapter(adapter)
        binding.actvCategory.setText(selectedCategory.bilingualTitle(), false)
        binding.actvCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categories[position]
        }
    }

    private fun setupDatePicker() {
        binding.etExpenseDate.setText(dateFormat.format(Date(selectedExpenseDate)))

        binding.etExpenseDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedExpenseDate }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedExpenseDate = calendar.timeInMillis
                    binding.etExpenseDate.setText(dateFormat.format(Date(selectedExpenseDate)))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveExpense.setOnClickListener {
            if (viewModel.isSaving.value || viewModel.saveCompleted.value) return@setOnClickListener
            if (!validateForm()) return@setOnClickListener
            binding.tvSaveError.visibility = View.GONE
            val amountStr = binding.etAmount.text?.toString() ?: ""
            val notes = binding.etNotes.text?.toString()

            viewModel.saveExpense(
                category = selectedCategory,
                tractorId = selectedTractor?.id,
                amountStr = amountStr,
                expenseDate = selectedExpenseDate,
                notes = notes
            ) {}
        }
    }

    private fun observeTractors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.btnSaveExpense.isEnabled = !saving
                        binding.btnSaveExpense.setText(if (saving) R.string.saving_on_phone else R.string.btn_save_expense)
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
                    viewModel.tractors.collect { tractorList ->
                        val options = mutableListOf<String>()
                        options.add(getString(R.string.all_tractors))
                        tractorList.forEach { t -> options.add(t.localizedDisplayName()) }

                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
                        binding.actvTractor.setAdapter(adapter)
                        if (selectedTractor == null && restoredTractorId != null) {
                            selectedTractor = tractorList.find { it.id == restoredTractorId }
                        }
                        binding.actvTractor.setText(selectedTractor?.localizedDisplayName() ?: options[0], false)

                        binding.actvTractor.setOnItemClickListener { _, _, position, _ ->
                            restoredTractorId = null
                            selectedTractor = if (position > 0) tractorList[position - 1] else null
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
        binding.tilAmount.error = if (!FormValidation.positiveAmount(binding.etAmount.text?.toString())) {
            if (firstInvalid == null) firstInvalid = binding.etAmount
            getString(R.string.error_amount)
        } else null
        firstInvalid?.requestFocus()
        return firstInvalid == null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draft_tractor", selectedTractor?.id ?: restoredTractorId)
        outState.putString("draft_category", selectedCategory.name)
        outState.putLong("draft_date", selectedExpenseDate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = RecordExpenseFragment()
    }
}

