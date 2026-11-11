package com.farmeraccounts.core.theme

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `default theme mode is explicitly Light`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.DEFAULT)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.DEFAULT.nightMode)
    }

    @Test
    fun `fromStorageKey parses light and dark case-insensitively`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey("light"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageKey("dark"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageKey("DARK"))
    }

    @Test
    fun `fromStorageKey falls back to default Light on invalid or null key`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey(""))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey("auto"))
    }
}

