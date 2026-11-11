package com.farmeraccounts.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.domain.repository.AuthRepository
import com.farmeraccounts.domain.repository.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncManagerAutoSyncTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `sync status initializes with current outbox pending count`() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                entityType = "CUSTOMER",
                entityId = "c1",
                operationType = "INSERT"
            )
        )

        val syncManager = SyncManager(
            context = context,
            database = db,
            authRepository = fakeAuthRepo,
            outboxSyncProcessor = null,
            cloudHydrationManager = null,
            supabaseClient = null,
            externalScope = backgroundScope
        )

        // Wait for the flow emission reflecting the initial count
        val status = syncManager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 1 }.first()
        assertTrue(status is SyncStatus.Idle)
        assertEquals(1, (status as SyncStatus.Idle).pendingCount)
    }

    @Test
    fun `adding outbox entries automatically updates pending count in syncStatus`() = runTest {
        val syncManager = SyncManager(
            context = context,
            database = db,
            authRepository = fakeAuthRepo,
            outboxSyncProcessor = null,
            cloudHydrationManager = null,
            supabaseClient = null,
            externalScope = backgroundScope
        )

        // Wait for initial zero state
        syncManager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 0 }.first()

        db.outboxDao().insert(
            OutboxEntity(
                entityType = "WORK_ENTRY",
                entityId = "w1",
                operationType = "INSERT"
            )
        )

        // Verify count automatically updates to 1
        val status1 = syncManager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 1 }.first()
        assertEquals(1, (status1 as SyncStatus.Idle).pendingCount)

        db.outboxDao().insert(
            OutboxEntity(
                entityType = "PAYMENT",
                entityId = "p1",
                operationType = "INSERT"
            )
        )

        // Verify count automatically updates to 2
        val status2 = syncManager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 2 }.first()
        assertEquals(2, (status2 as SyncStatus.Idle).pendingCount)
    }

    @Test
    fun `performSync returns error when cloud sync is not configured but retains local outbox`() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                entityType = "EXPENSE",
                entityId = "e1",
                operationType = "INSERT"
            )
        )

        val syncManager = SyncManager(
            context = context,
            database = db,
            authRepository = fakeAuthRepo,
            outboxSyncProcessor = null,
            cloudHydrationManager = null,
            supabaseClient = null,
            externalScope = backgroundScope
        )

        syncManager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 1 }.first()

        val result = syncManager.performSync()
        assertFalse(result.isSuccess)
        assertFalse(result.shouldRetry)
        assertEquals(1, db.outboxDao().getPendingCount())
    }

    @Test
    fun `performSync returns unauthenticated when owner is not logged in`() = runTest {
        fakeAuthRepo.currentOwnerId = null

        val syncManager = SyncManager(
            context = context,
            database = db,
            authRepository = fakeAuthRepo,
            outboxSyncProcessor = null,
            cloudHydrationManager = null,
            supabaseClient = null,
            externalScope = backgroundScope
        )

        val result = syncManager.performSync()
        assertFalse(result.isSuccess)
        assertEquals("Cloud sync is not configured", result.summary)
    }

    @Test
    fun `a local save enqueues durable sync without pressing a button`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        try {
            val manager = SyncManager(context, db, fakeAuthRepo, null, null, null, scope)
            db.outboxDao().insert(OutboxEntity(entityType = "CUSTOMER", entityId = "auto", operationType = "INSERT"))
            manager.syncStatus.filter { it is SyncStatus.Idle && it.pendingCount == 1 }.first()
            kotlinx.coroutines.withTimeout(8000L) {
                while (androidx.work.WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(SyncWorker.ONE_TIME_WORK_NAME).get().isEmpty()) {
                    kotlinx.coroutines.delay(50L)
                }
            }
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    private class FakeAuthRepository : AuthRepository {
        var currentOwnerId: String? = "test-owner-id"
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()

        override suspend fun getOwnerId(): String? = currentOwnerId

        override suspend fun signIn(email: String, password: String): Result<String> {
            _authState.value = AuthState.Authenticated("test-owner-id", email)
            return Result.success("test-owner-id")
        }

        override suspend fun signUpOwner(email: String, password: String): Result<String> {
            _authState.value = AuthState.Authenticated("test-owner-id", email)
            return Result.success("test-owner-id")
        }

        override suspend fun signOut(): Result<Unit> {
            currentOwnerId = null
            _authState.value = AuthState.Unauthenticated
            return Result.success(Unit)
        }

        override suspend fun refreshSession(): Result<Unit> = Result.success(Unit)
    }
}
