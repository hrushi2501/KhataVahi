package com.farmeraccounts.presentation.base

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.databinding.DialogDeleteRecordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** A reason is required; failures keep the dialog and reason available for retry. */
class DeleteRecordDialog : DialogFragment() {
    private lateinit var binding: DialogDeleteRecordBinding
    private lateinit var model: RecordDeletionViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val app = requireActivity().application as FarmerAccountsApp
        model = ViewModelProvider(requireParentFragment(), ViewModelFactory(app.container))[RecordDeletionViewModel::class.java]
        binding = DialogDeleteRecordBinding.inflate(layoutInflater)
        val kind = RecordKind.valueOf(requireArguments().getString("kind")!!)
        val warning = when (kind) {
            RecordKind.CUSTOMER -> R.string.delete_customer_warning
            RecordKind.WORK -> R.string.delete_work_warning
            RecordKind.PAYMENT -> R.string.delete_payment_warning
            else -> R.string.delete_record_warning
        }
        binding.tvDeleteWarning.text = getString(warning, requireArguments().getString("label"))
        binding.tvDeleteError.setText(if (kind == RecordKind.CUSTOMER) R.string.delete_customer_failed else R.string.delete_failed)
        savedInstanceState?.getString("reason")?.let { binding.etReason.setText(it) }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_record_title)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete_record, null)
            .create()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = !state.isDeleting
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).isEnabled = !state.isDeleting
                    isCancelable = !state.isDeleting
                    binding.etReason.isEnabled = !state.isDeleting
                    binding.tvDeleteError.visibility = if (state.hasError) View.VISIBLE else View.GONE
                    if (state.deletedId != null) dismiss()
                }
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as androidx.appcompat.app.AlertDialog
        val confirm = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        confirm.minHeight = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).minHeight = confirm.minHeight
        confirm.setOnClickListener {
            val reason = binding.etReason.text?.toString().orEmpty()
            binding.tilReason.error = if (reason.isBlank()) getString(R.string.delete_reason_required) else null
            if (reason.isBlank()) {
                binding.etReason.requestFocus()
            } else {
                model.remove(RecordKind.valueOf(requireArguments().getString("kind")!!), requireArguments().getString("id")!!, reason)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("reason", binding.etReason.text?.toString())
        super.onSaveInstanceState(outState)
    }

    companion object {
        fun show(parent: androidx.fragment.app.Fragment, kind: RecordKind, id: String, label: String) {
            if (parent.childFragmentManager.findFragmentByTag("delete_record") != null) return
            val app = parent.requireActivity().application as FarmerAccountsApp
            ViewModelProvider(parent, ViewModelFactory(app.container))[RecordDeletionViewModel::class.java].prepare()
            DeleteRecordDialog().apply {
                arguments = Bundle().apply {
                    putString("kind", kind.name)
                    putString("id", id)
                    putString("label", label)
                }
            }.show(parent.childFragmentManager, "delete_record")
        }
    }
}
