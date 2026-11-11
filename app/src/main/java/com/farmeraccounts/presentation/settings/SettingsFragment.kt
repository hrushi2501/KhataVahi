package com.farmeraccounts.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.core.theme.ThemeMode
import com.farmeraccounts.data.sync.SyncStatus
import com.farmeraccounts.databinding.DialogOwnerAuthBinding
import com.farmeraccounts.databinding.FragmentSettingsBinding
import com.farmeraccounts.domain.repository.AuthState
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_settings))

        binding.tvSyncStatus.setOnClickListener {
            val status = viewModel.syncStatus.value
            if (status is SyncStatus.Error && status.requiresAttention) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_attention)
                    .setMessage(R.string.backup_attention_details)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
        setupListeners()
        setupLanguageSelector()
        observeUiState()
    }

    private fun setupListeners() {
        binding.btnExpenseHistory.setOnClickListener { (activity as? Navigator)?.navigateToHistory(false) }
        binding.btnDeletedHistory.setOnClickListener { (activity as? Navigator)?.navigateToHistory(true) }
        // Toggle switch directly
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchDarkMode.isPressed) {
                viewModel.toggleDarkMode(isChecked)
            }
        }

        // Tapping anywhere on the theme card also toggles the switch
        binding.cardThemeToggle.setOnClickListener {
            val nextState = !binding.switchDarkMode.isChecked
            binding.switchDarkMode.isChecked = nextState
            viewModel.toggleDarkMode(nextState)
        }

        binding.btnOwnerAuth.setOnClickListener {
            val currentAuth = viewModel.authState.value
            if (currentAuth is AuthState.Authenticated || currentAuth is AuthState.Offline) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.btn_owner_logout)
                    .setMessage(getString(R.string.logout_confirmation))
                    .setPositiveButton(R.string.btn_owner_logout) { _, _ ->
                        viewModel.signOut()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            } else {
                showOwnerAuthDialog()
            }
        }

        binding.btnBack.setOnClickListener {
            (activity as? Navigator)?.navigateBack()
        }
    }

    private fun setupLanguageSelector() {
        val language = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: resources.configuration.locales[0].language
        binding.languageOptions.check(
            if (language == "en") R.id.languageEnglish else R.id.languageGujarati
        )
        binding.languageOptions.setOnCheckedChangeListener { _, checkedId ->
            val languageTag = if (checkedId == R.id.languageEnglish) "en" else "gu"
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (currentTag != languageTag) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            }
        }
    }

    private fun showOwnerAuthDialog() {
        val dialogBinding = DialogOwnerAuthBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelAuth.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitAuth.setOnClickListener {
            val email = dialogBinding.etEmail.text?.toString()?.trim().orEmpty()
            val password = dialogBinding.etPassword.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                dialogBinding.tvAuthError.visibility = View.VISIBLE
                dialogBinding.tvAuthError.text = getString(R.string.auth_fields_required)
                return@setOnClickListener
            }

            dialogBinding.progressAuth.visibility = View.VISIBLE
            dialogBinding.btnSubmitAuth.isEnabled = false
            dialogBinding.tvAuthError.visibility = View.GONE

            viewModel.signIn(email, password) { result ->
                dialogBinding.progressAuth.visibility = View.GONE
                dialogBinding.btnSubmitAuth.isEnabled = true
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), getString(R.string.auth_success), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    dialogBinding.tvAuthError.visibility = View.VISIBLE
                    dialogBinding.tvAuthError.text = getString(R.string.auth_failed)
                }
            }
        }

        dialog.show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (binding.switchDarkMode.isChecked != state.isDarkMode) {
                            binding.switchDarkMode.isChecked = state.isDarkMode
                        }

                        binding.tvThemeStatus.text = if (state.themeMode == ThemeMode.DARK) {
                            getString(R.string.theme_dark_status)
                        } else {
                            getString(R.string.theme_light_status)
                        }
                    }
                }

                launch {
                    viewModel.authState.collect { auth ->
                        when (auth) {
                            is AuthState.Authenticated -> {
                                binding.tvAccountStatus.text = getString(R.string.account_owner_format, auth.email)
                                binding.btnOwnerAuth.text = getString(R.string.btn_owner_logout)
                            }
                            is AuthState.Offline -> {
                                binding.tvAccountStatus.text = getString(R.string.account_offline_owner_format, auth.email)
                                binding.btnOwnerAuth.text = getString(R.string.btn_owner_logout)
                            }
                            is AuthState.Unauthenticated, is AuthState.Error -> {
                                binding.tvAccountStatus.text = getString(R.string.account_not_logged_in)
                                binding.btnOwnerAuth.text = getString(R.string.btn_owner_login)
                            }
                        }
                    }
                }

                launch {
                    combine(viewModel.authState, viewModel.syncStatus) { auth, sync ->
                        when {
                            auth is AuthState.Unauthenticated || auth is AuthState.Error ->
                                R.string.backup_login_needed
                            sync is SyncStatus.Error && sync.requiresAttention -> R.string.backup_attention
                            sync is SyncStatus.Syncing -> R.string.backup_pending
                            sync is SyncStatus.Idle && sync.pendingCount > 0 -> R.string.backup_pending
                            sync is SyncStatus.Offline && sync.pendingCount > 0 -> R.string.backup_pending
                            sync is SyncStatus.Error && sync.pendingCount > 0 -> R.string.backup_pending
                            sync is SyncStatus.Idle && sync.lastSyncTimestamp > 0 &&
                                auth is AuthState.Authenticated -> R.string.backup_saved_online
                            else -> R.string.backup_saved_phone
                        }
                    }.collect { statusText ->
                        binding.tvSyncStatus.setText(statusText)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}

