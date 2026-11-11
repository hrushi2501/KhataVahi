package com.farmeraccounts.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncCursorDataStore by preferencesDataStore(name = "sync_cursors")

/** Durable sequence cursor partitioned by authenticated Supabase owner. */
class SyncCursorPreferences(
    context: Context,
    private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> = context.syncCursorDataStore
) {
    suspend fun get(ownerId: String): Long {
        require(ownerId.isNotBlank())
        return store.data.first()[key(ownerId)] ?: 0L
    }

    suspend fun set(ownerId: String, sequence: Long) {
        require(ownerId.isNotBlank())
        require(sequence >= 0L)
        store.edit { preferences -> preferences[key(ownerId)] = sequence }
    }

    private fun key(ownerId: String) = longPreferencesKey("sequence_$ownerId")
}
