package com.farmeraccounts

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.farmeraccounts.core.di.AppContainer
import com.farmeraccounts.core.di.DefaultAppContainer
import com.farmeraccounts.core.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application class for Farmer Accounts.
 * Configures manual dependency injection and guarantees Light Mode default on first launch.
 */
class FarmerAccountsApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize Manual DI container
        container = DefaultAppContainer(this)

        // AppCompat restores the saved language during activity attachment.

        // Schedule periodic background synchronization via WorkManager and start auto-sync
        try {
            com.farmeraccounts.data.sync.SyncWorker.schedulePeriodic(this)
        } catch (_: Exception) {
            // Safe fallback if WorkManager initialization deferred
        }

        // Strict requirement: Light mode must be default on first launch, regardless of system appearance
        AppCompatDelegate.setDefaultNightMode(ThemeMode.DEFAULT.nightMode)

        // Apply saved theme preference if explicitly modified by the user in Settings
        applicationScope.launch(Dispatchers.IO) {
            try {
                val savedTheme = container.themePreferencesRepository.themeModeFlow.first()
                launch(Dispatchers.Main) {
                    AppCompatDelegate.setDefaultNightMode(savedTheme.nightMode)
                }
            } catch (_: Exception) {
                // Keep default Light mode
            }
        }
    }
}

