package com.farmeraccounts.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncScheduleStore by preferencesDataStore(name = "sync_schedule")

internal object SyncPolicy {
    const val PULL_INTERVAL_MS = 30 * 60 * 1000L
    fun shouldPull(now: Long, lastAttempt: Long, unfinished: Boolean, foreground: Boolean = false): Boolean =
        unfinished || lastAttempt == 0L || now < lastAttempt || now - lastAttempt >= PULL_INTERVAL_MS ||
            (foreground && now - lastAttempt >= 10_000L)
}

internal data class SyncRunState(val attemptedAt: Long, val confirmedAt: Long, val unfinished: Boolean, val conflict: Boolean)

internal class SyncRunPreferences(context: Context) {
    private val store = context.applicationContext.syncScheduleStore
    suspend fun requestRestore(owner: String) {
        store.edit { it[booleanPreferencesKey("unfinished_$owner")] = true }
    }

    suspend fun bindOwner(owner: String): Boolean {
        val key = androidx.datastore.preferences.core.stringPreferencesKey("ledger_owner")
        var accepted = false
        store.edit { preferences ->
            val bound = preferences[key]
            if (bound == null || bound == owner) { preferences[key] = owner; accepted = true }
        }
        return accepted
    }

    suspend fun enrollLegacySeeds(database: AppDatabase) {
        val key = booleanPreferencesKey("seed_tractors_enrolled")
        if (store.data.first()[key] == true) return
        database.withTransaction {
            val pending = database.outboxDao().getUnconfirmed()
            for (id in listOf(AppDatabase.SEED_TRACTOR_BIG_ID, AppDatabase.SEED_TRACTOR_MINI_ID)) {
                if (database.tractorDao().getByIdIncludeDeleted(id) != null &&
                    pending.none { it.entityType == "TRACTOR" && it.entityId == id }) {
                    database.outboxDao().insert(OutboxEntity(entityType = "TRACTOR", entityId = id, operationType = "INSERT"))
                }
            }
        }
        store.edit { it[key] = true }
    }

    suspend fun read(owner: String): SyncRunState {
        val p = store.data.first()
        return SyncRunState(p[longPreferencesKey("attempt_$owner")] ?: 0L,
            p[longPreferencesKey("confirmed_$owner")] ?: 0L,
            p[booleanPreferencesKey("unfinished_$owner")] ?: true,
            p[booleanPreferencesKey("conflict_$owner")] ?: false)
    }
    suspend fun recordPull(owner: String, now: Long, unfinished: Boolean, conflict: Boolean) {
        store.edit {
            it[longPreferencesKey("attempt_$owner")] = now
            it[booleanPreferencesKey("unfinished_$owner")] = unfinished
            it[booleanPreferencesKey("conflict_$owner")] = conflict
            if (!unfinished && !conflict) it[longPreferencesKey("confirmed_$owner")] = now
        }
    }
}
