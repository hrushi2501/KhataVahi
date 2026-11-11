package com.farmeraccounts.data.local.db

import androidx.room.InvalidationTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Financial screens observe committed Room changes, including remote tombstones. */
fun AppDatabase.ledgerInvalidations(): Flow<Unit> = callbackFlow {
    val observer = object : InvalidationTracker.Observer("customers", "work_entries", "payments") {
        override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
    }
    invalidationTracker.addObserver(observer)
    trySend(Unit)
    awaitClose { invalidationTracker.removeObserver(observer) }
}.conflate()
