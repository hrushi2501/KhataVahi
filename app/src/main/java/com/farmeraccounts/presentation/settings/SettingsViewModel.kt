package com.farmeraccounts.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.core.theme.ThemeMode
import com.farmeraccounts.data.local.preferences.ThemePreferencesRepository
import com.farmeraccounts.data.sync.SyncManager
import com.farmeraccounts.data.sync.SyncStatus
import com.farmeraccounts.domain.repository.AuthRepository
import com.farmeraccounts.domain.repository.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val isDarkMode: Boolean = false
)

class SettingsViewModel(
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = themePreferencesRepository.themeModeFlow
        .map { mode ->
            SettingsUiState(
                themeMode = mode,
                isDarkMode = mode == ThemeMode.DARK
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(ThemeMode.LIGHT, isDarkMode = false)
        )

    val authState: StateFlow<AuthState> = authRepository.authState
    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    fun toggleDarkMode(enabled: Boolean) {
        val newMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
        viewModelScope.launch {
            // Apply immediately per project rules
            AppCompatDelegate.setDefaultNightMode(newMode.nightMode)
            // Persist to DataStore
            themePreferencesRepository.setThemeMode(newMode)
        }
    }

    fun signIn(email: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            onResult(result)
            if (result.isSuccess) {
                syncManager.triggerOneTimeSync()
            }
        }
    }

    fun signUp(email: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signUpOwner(email, password)
            onResult(result)
            if (result.isSuccess) {
                syncManager.triggerOneTimeSync()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun triggerSync() {
        syncManager.triggerOneTimeSync()
    }
}
