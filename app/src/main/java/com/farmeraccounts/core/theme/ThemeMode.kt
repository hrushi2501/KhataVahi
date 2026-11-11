package com.farmeraccounts.core.theme

import androidx.appcompat.app.AppCompatDelegate

/**
 * Supported UI themes for Farmer Accounts.
 * Per project rules, Light Mode is the strict default on first launch regardless
 * of system appearance to ensure optimal sunlight readability in the field.
 */
enum class ThemeMode(val storageKey: String, val nightMode: Int) {
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        val DEFAULT = LIGHT

        fun fromStorageKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DEFAULT
        }
    }
}

