package com.farmeraccounts.presentation.customers

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.databinding.FragmentAddEditCustomerBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import kotlinx.coroutines.launch

class AddEditCustomerFragment : Fragment() {

    private var _binding: FragmentAddEditCustomerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditCustomerViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private var customerId: String? = null
    private var populatedCustomer = false
    private var duplicateDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customerId = arguments?.getString(ARG_CUSTOMER_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populatedCustomer = savedInstanceState?.getBoolean("customer_populated", false) ?: false
        viewModel.loadCustomer(customerId)
        binding.etName.doAfterTextChanged { binding.tilName.error = null }

        binding.btnSaveCustomer.setOnClickListener { submitForm() }

        binding.btnRetryCustomer.setOnClickListener { viewModel.loadCustomer(customerId, retry = true) }
        observeUiState()
    }

    private fun submitForm(confirmDuplicate: Boolean = false) {
        if (viewModel.uiState.value.isSaving || viewModel.uiState.value.isSaved) return
        binding.tilName.error = null
        if (binding.etName.text.isNullOrBlank()) {
            binding.tilName.setError(getString(R.string.error_customer_name))
            binding.etName.requestFocus()
            return
        }
        binding.tvSaveError.visibility = View.GONE
        val name = binding.etName.text?.toString() ?: ""
        val nickname = binding.etNickname.text?.toString()
        val fatherName = binding.etFatherName.text?.toString()
        val village = binding.etVillage.text?.toString()
        val phone = binding.etPhone.text?.toString()

        viewModel.saveCustomer(name, nickname, fatherName, village, phone, confirmDuplicate = confirmDuplicate) {}
    }

    private fun showDuplicateWarning(state: AddEditCustomerUiState) {
        if (duplicateDialog?.isShowing == true || state.duplicateCandidates.isEmpty()) return
        val candidates = state.duplicateCandidates
        val details = candidates.mapIndexed { index, customer ->
            val info = listOfNotNull(customer.village?.takeIf { it.isNotBlank() }, customer.phone?.takeIf { it.isNotBlank() })
            "${index + 1}. ${customer.name}" + if (info.isEmpty()) "" else "\n${info.joinToString(" · ")}"
        }.joinToString("\n\n")
        duplicateDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.duplicate_customer_title)
            .setMessage(getString(if (state.isEditing) R.string.duplicate_customer_edit_message else R.string.duplicate_customer_message, details))
            .setPositiveButton(if (state.isEditing) R.string.duplicate_save_changes else R.string.duplicate_save_new) { _, _ ->
                duplicateDialog?.dismiss()
                duplicateDialog = null
                submitForm(confirmDuplicate = true)
            }
            .setNeutralButton(R.string.duplicate_review) { _, _ ->
                viewModel.cancelDuplicateWarning()
                duplicateDialog?.dismiss()
                duplicateDialog = null
                if (candidates.size == 1) {
                    (activity as? Navigator)?.navigateToCustomerDetail(candidates.first().id)
                } else {
                    showExistingAccountChooser(candidates)
                }
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ -> viewModel.cancelDuplicateWarning() }
            .setOnCancelListener { viewModel.cancelDuplicateWarning() }
            .create().also { dialog ->
                dialog.setOnDismissListener { if (duplicateDialog === dialog) duplicateDialog = null }
                dialog.show()
            }
    }

    private fun showExistingAccountChooser(customers: List<com.farmeraccounts.domain.model.Customer>) {
        val labels = customers.map { customer ->
            listOfNotNull(customer.name, customer.village?.takeIf { it.isNotBlank() },
                customer.phone?.takeIf { it.isNotBlank() }).joinToString(" · ")
        }.toTypedArray()
        duplicateDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.duplicate_choose_account)
            .setItems(labels) { _, position ->
                (activity as? Navigator)?.navigateToCustomerDetail(customers[position].id)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create().also { dialog ->
                dialog.setOnDismissListener { if (duplicateDialog === dialog) duplicateDialog = null }
                dialog.show()
            }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val navigator = activity as? Navigator
                    if (state.isEditing) {
                        val title = getString(R.string.title_edit_customer)
                        binding.tvHeaderTitle.text = title
                        navigator?.setToolbarTitle(title)

                        val c = state.existingCustomer
                        if (c != null && !populatedCustomer) {
                            populatedCustomer = true
                            binding.etName.setText(c.name)
                            binding.etNickname.setText(c.nickname ?: "")
                            binding.etFatherName.setText(c.fatherName ?: "")
                            binding.etVillage.setText(c.village ?: "")
                            binding.etPhone.setText(c.phone ?: "")
                        }
                    } else {
                        val title = getString(R.string.title_add_customer)
                        binding.tvHeaderTitle.text = title
                        navigator?.setToolbarTitle(title)
                    }

                    binding.btnSaveCustomer.isEnabled = !state.isSaving && !state.isLoading && !state.loadFailed
                    binding.btnSaveCustomer.setText(if (state.isSaving) R.string.saving_on_phone else R.string.btn_save_customer)
                    binding.tvSaveError.visibility = if (state.errorMessage != null || state.isLoading) View.VISIBLE else View.GONE
                    binding.tvSaveError.setText(when {
                        state.isLoading -> R.string.customer_loading
                        state.loadFailed -> R.string.customer_load_failed
                        else -> R.string.save_failed
                    })
                    binding.btnRetryCustomer.visibility = if (state.loadFailed) View.VISIBLE else View.GONE
                    showDuplicateWarning(state)
                    if (state.isSaved) {
                        Toast.makeText(requireContext(), R.string.saved_on_phone, Toast.LENGTH_SHORT).show()
                        (activity as? Navigator)?.navigateBack()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("customer_populated", populatedCustomer)
    }

    override fun onDestroyView() {
        duplicateDialog?.dismiss()
        duplicateDialog = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "arg_customer_id"

        fun newInstance(customerId: String? = null) = AddEditCustomerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CUSTOMER_ID, customerId)
            }
        }
    }
}

