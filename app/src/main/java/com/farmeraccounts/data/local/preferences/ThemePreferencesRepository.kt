package com.farmeraccounts.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.farmeraccounts.core.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "farmer_preferences")

/**
 * Repository managing UI preferences persisted in Jetpack DataStore.
 * Guarantees that the default theme is LIGHT mode on fresh install.
 */
class ThemePreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val key = preferences[PreferencesKeys.THEME_MODE]
            ThemeMode.fromStorageKey(key)
        }

    suspend fun getInitialThemeMode(): ThemeMode {
        return try {
            val preferences = context.dataStore.data.first()
            ThemeMode.fromStorageKey(preferences[PreferencesKeys.THEME_MODE])
        } catch (_: Exception) {
            ThemeMode.DEFAULT
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.storageKey
        }
    }
}

