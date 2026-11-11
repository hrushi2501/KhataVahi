package com.farmeraccounts.data.sync

import android.content.Context
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.domain.repository.AuthRepository
import com.farmeraccounts.domain.repository.AuthState
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed class SyncStatus {
    data class Idle(val lastSyncTimestamp: Long, val pendingCount: Int) : SyncStatus()
    data class Syncing(val stage: String) : SyncStatus()
    data class Offline(val pendingCount: Int) : SyncStatus()
    data class Error(val message: String, val pendingCount: Int, val requiresAttention: Boolean = false) : SyncStatus()
}

data class SyncResult(
    val isSuccess: Boolean,
    val shouldRetry: Boolean,
    val summary: String,
    val continueImmediately: Boolean = false
)

class SyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
    private val outboxSyncProcessor: OutboxSyncProcessor?,
    private val cloudHydrationManager: CloudHydrationManager?,
    private val supabaseClient: SupabaseClient?,
    externalScope: CoroutineScope? = null
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle(0L, 0))
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var lastSyncTimestamp = 0L
    private val runPreferences = SyncRunPreferences(context)
    private val syncMutex = Mutex()
    private val syncScope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debouncedSyncJob: Job? = null

    init {
        startAutoSync()
    }

    private fun startAutoSync() {
        syncScope.launch {
            database.outboxDao().getUnconfirmedCountFlow().distinctUntilChanged().collect { count ->
                when (val current = _syncStatus.value) {
                    is SyncStatus.Idle -> _syncStatus.value = current.copy(pendingCount = count)
                    is SyncStatus.Offline -> _syncStatus.value = current.copy(pendingCount = count)
                    is SyncStatus.Error -> _syncStatus.value = current.copy(pendingCount = count)
                    is SyncStatus.Syncing -> Unit
                }
            }
        }
        // A count can stay unchanged when a write replaces an acknowledged row. Sequence
        // identity catches that write; status/retry invalidations cannot schedule a loop.
        syncScope.launch {
            var observed = 0L
            database.outboxDao().getLatestSequenceFlow().distinctUntilChanged().collect { sequence ->
                if (sequence > observed) {
                    observed = sequence
                    if (database.outboxDao().getPendingCount() > 0) scheduleDebouncedAutoSync()
                }
            }
        }
        syncScope.launch {
            authRepository.authState.collect { auth ->
                if (auth is AuthState.Authenticated || auth is AuthState.Offline) {
                    val owner = if (auth is AuthState.Authenticated) auth.userId else (auth as AuthState.Offline).cachedUserId
                    runPreferences.requestRestore(owner)
                    scheduleDebouncedAutoSync(500)
                } else {
                    lastSyncTimestamp = 0L
                    _syncStatus.value = SyncStatus.Idle(0L, database.outboxDao().getUnconfirmedCount())
                }
            }
        }
        // WorkManager observes connectivity even after this process exits. No polling,
        // persistent network listener, or duplicated reconnect scheduler is necessary.
    }

    fun scheduleDebouncedAutoSync(delayMs: Long = 1500) {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = syncScope.launch {
            delay(delayMs)
            triggerOneTimeSync()
        }
    }

    suspend fun performSync(forcePull: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(isSuccess = false, shouldRetry = true, "Sync already in progress")
        }

        try {
            val pendingCount = database.outboxDao().getUnconfirmedCount()
            if (supabaseClient == null || outboxSyncProcessor == null || cloudHydrationManager == null) {
                _syncStatus.value = SyncStatus.Error("Cloud sync is not configured; local data remains on this device", pendingCount)
                return@withContext SyncResult(isSuccess = false, shouldRetry = false, "Cloud sync is not configured")
            }

            val ownerId = authRepository.getOwnerId()
            if (ownerId.isNullOrBlank()) {
                _syncStatus.value = SyncStatus.Offline(pendingCount)
                return@withContext SyncResult(isSuccess = false, shouldRetry = false, "User unauthenticated")
            }

            if (authRepository.authState.value is AuthState.Offline) {
                val refresh = authRepository.refreshSession()
                if (refresh.isFailure) {
                    _syncStatus.value = SyncStatus.Offline(pendingCount)
                    return@withContext SyncResult(false, true, "Session refresh unavailable")
                }
            }
            if (!runPreferences.bindOwner(ownerId)) {
                _syncStatus.value = SyncStatus.Error("Sign in with this ledger's owner",pendingCount,true)
                return@withContext SyncResult(false,false,"Owner mismatch")
            }
            runPreferences.enrollLegacySeeds(database)
            val savedRun = runPreferences.read(ownerId)
            lastSyncTimestamp = savedRun.confirmedAt

            // Stage 1: Push pending local mutations to cloud
            _syncStatus.value = SyncStatus.Syncing("upload")
            val pushResult = outboxSyncProcessor.processPending(ownerId)

            if (pushResult.failedCount > 0) {
                val errorMsg = pushResult.errors.firstOrNull() ?: "Upload error"
                val remaining = database.outboxDao().getUnconfirmedCount()
                if (!pushResult.hasRetryableWork) {
                    // A rejected stale upload must not prevent a server tombstone arriving.
                    try {
                        val recovered = cloudHydrationManager.hydrateFromCloud(ownerId)
                        val now = System.currentTimeMillis()
                        runPreferences.recordPull(ownerId,now,recovered.hasMore,recovered.skippedConflictCount>0)
                        if (recovered.hasMore) return@withContext SyncResult(false,false,"Continue cloud restore",true)
                        if (database.outboxDao().getFailedCount()==0 && recovered.skippedConflictCount==0) {
                            lastSyncTimestamp=now
                            val count=database.outboxDao().getUnconfirmedCount()
                            _syncStatus.value=SyncStatus.Idle(now,count)
                            return@withContext SyncResult(true,false,"Cloud state reconciled",count>0)
                        }
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Keep the original failure visible; no success claimed. */ }
                }
                _syncStatus.value = SyncStatus.Error(errorMsg, remaining, requiresAttention = !pushResult.hasRetryableWork)
                return@withContext SyncResult(isSuccess = false, shouldRetry = pushResult.hasRetryableWork, "${pushResult.processedCount} uploaded; ${pushResult.failedCount} retained for retry: $errorMsg")
            }

            // Empty polls are throttled per owner across process restarts. Uploading a
            // new local entry never forces a full ledger download.
            val now = System.currentTimeMillis()
            if (SyncPolicy.shouldPull(now, savedRun.attemptedAt, savedRun.unfinished, forcePull)) {
                _syncStatus.value = SyncStatus.Syncing("download")
                val pull = cloudHydrationManager.hydrateFromCloud(ownerId)
                val conflict = pull.skippedConflictCount > 0
                runPreferences.recordPull(ownerId, now, pull.hasMore && !conflict, conflict)
                if (conflict) {
                    _syncStatus.value = SyncStatus.Error("Cloud changes need review", database.outboxDao().getUnconfirmedCount(), requiresAttention = true)
                    return@withContext SyncResult(false, false, "Cloud changes need review")
                }
                if (pull.hasMore) {
                    return@withContext SyncResult(false, false, "More cloud pages remain",true)
                }
                lastSyncTimestamp = now
            } else if (savedRun.conflict) {
                _syncStatus.value = SyncStatus.Error("Cloud changes need review", database.outboxDao().getUnconfirmedCount(), requiresAttention = true)
                return@withContext SyncResult(false, false, "Cloud changes need review")
            }
            val remaining = database.outboxDao().getUnconfirmedCount()
            _syncStatus.value = SyncStatus.Idle(lastSyncTimestamp, remaining)
            SyncResult(true, false, "Automatic backup checked", database.outboxDao().getPendingCount() > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Network synchronization failed"
            val remaining = database.outboxDao().getUnconfirmedCount()
            _syncStatus.value = SyncStatus.Error(msg, remaining)
            SyncResult(isSuccess = false, shouldRetry = true, msg)
        } finally {
            syncMutex.unlock()
        }
    }

    fun triggerOneTimeSync(refreshCloud: Boolean = false) {
        try {
            SyncWorker.enqueueOneTime(context, refreshCloud)
        } catch (_: Exception) {
            // Graceful fallback for test environments or before WorkManager is initialized
        }
    }
}
