package com.farmeraccounts.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import com.farmeraccounts.data.local.db.entity.ExpenseEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.data.remote.dto.AuditLogDto
import com.farmeraccounts.data.remote.dto.CustomerDto
import com.farmeraccounts.data.remote.dto.DeletedLogDto
import com.farmeraccounts.data.remote.dto.ExpenseDto
import com.farmeraccounts.data.remote.dto.PaymentDto
import com.farmeraccounts.data.remote.dto.SyncChangeDto
import com.farmeraccounts.data.remote.dto.TractorDto
import com.farmeraccounts.data.remote.dto.WorkEntryDto
import com.farmeraccounts.data.remote.mapper.toEntity
import com.farmeraccounts.data.remote.mapper.toDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Result of one bounded incremental pull. Conflicts leave the cursor before the first blocked event. */
data class HydrationResult(
    val downloadedCount: Int,
    val appliedCount: Int,
    val skippedConflictCount: Int,
    val nextCursor: Long,
    val hasMore: Boolean,
    val unresolvedConflictIds: List<String> = emptyList()
)

/** Fetches only changed records and applies a bounded page in dependency order. */
class CloudHydrationManager(
    private val database: AppDatabase,
    private val supabaseClient: SupabaseClient,
    context: Context,
    private val cursorPreferences: SyncCursorPreferences = SyncCursorPreferences(context.applicationContext)
) {
    private val outboxDao = database.outboxDao()
    private val customerDao = database.customerDao()
    private val tractorDao = database.tractorDao()
    private val workEntryDao = database.workEntryDao()
    private val paymentDao = database.paymentDao()
    private val expenseDao = database.expenseDao()
    private val auditDao = database.auditLogDao()
    private val deletedLogDao = database.deletedLogDao()

    suspend fun hydrateFromCloud(ownerId: String): HydrationResult = withContext(Dispatchers.IO) {
        require(ownerId.isNotBlank()) { "Authenticated owner is required for hydration" }
        check(supabaseClient.auth.currentUserOrNull()?.id == ownerId) {
            "Hydration owner does not match the authenticated session"
        }

        val cursor = cursorPreferences.get(ownerId)
        val changes = supabaseClient.postgrest["sync_changes"].select {
            filter {
                eq("owner_id", ownerId)
                gt("sequence_id", cursor)
            }
            order("sequence_id", Order.ASCENDING)
            limit(PAGE_SIZE.toLong())
        }.decodeList<SyncChangeDto>()

        check(changes.all { it.ownerId == ownerId && it.sequenceId > cursor }) {
            "Cloud change feed returned a row outside the requested owner/cursor"
        }
        if (changes.isEmpty()) {
            return@withContext HydrationResult(0, 0, 0, cursor, hasMore = false)
        }

        val changesByTable = changes.groupBy { it.tableName }
        val customerDtos = fetchByIds<CustomerDto>("customers", changesByTable["customers"].orEmpty().map { it.recordId }, ownerId)
        val tractorDtos = fetchByIds<TractorDto>("tractors", changesByTable["tractors"].orEmpty().map { it.recordId }, ownerId)
        val workDtos = fetchByIds<WorkEntryDto>("work_entries", changesByTable["work_entries"].orEmpty().map { it.recordId }, ownerId)
        val paymentDtos = fetchByIds<PaymentDto>("payments", changesByTable["payments"].orEmpty().map { it.recordId }, ownerId)
        val expenseDtos = fetchByIds<ExpenseDto>("expenses", changesByTable["expenses"].orEmpty().map { it.recordId }, ownerId)
        val auditDtos = fetchByIds<AuditLogDto>("audit_logs", changesByTable["audit_logs"].orEmpty().map { it.recordId }, ownerId)
        val deletedLogDtos = fetchByIds<DeletedLogDto>("deleted_logs", changesByTable["deleted_logs"].orEmpty().map { it.recordId }, ownerId)

        check(customerDtos.all { it.ownerId == ownerId } && tractorDtos.all { it.ownerId == ownerId } &&
            workDtos.all { it.ownerId == ownerId } && paymentDtos.all { it.ownerId == ownerId } &&
            expenseDtos.all { it.ownerId == ownerId } && auditDtos.all { it.ownerId == ownerId } &&
            deletedLogDtos.all { it.ownerId == ownerId }) {
            "Cloud record owner mismatch"
        }

        val customers = customerDtos.map { it.toEntity() }
        val tractors = tractorDtos.map { it.toEntity() }
        val work = workDtos.map { it.toEntity() }
        val payments = paymentDtos.map { it.toEntity() }
        val expenses = expenseDtos.map { it.toEntity() }
        val audits = auditDtos.map { it.toEntity() }
        val deletedLogs = deletedLogDtos.map { it.toEntity() }

        // A later remote edit can make an old feed event point at a parent whose
        // own event is outside this page. Close those references before writing.
        val firstSequenceByRow = changes.groupBy { it.tableName to it.recordId }
            .mapValues { (_, events) -> events.minOf { it.sequenceId } }
        val dependencyReferences = mutableMapOf<Pair<String, String>, Long>()
        fun requireParent(table: String, id: String?, childTable: String, childId: String) {
            if (id == null || (table to id) in firstSequenceByRow) return
            val childSequence = firstSequenceByRow[childTable to childId] ?: return
            val key = table to id
            dependencyReferences[key] = minOf(dependencyReferences[key] ?: Long.MAX_VALUE, childSequence)
        }
        work.forEach {
            requireParent("customers", it.customerId, "work_entries", it.id)
            requireParent("tractors", it.tractorId, "work_entries", it.id)
        }
        payments.forEach {
            requireParent("customers", it.customerId, "payments", it.id)
            requireParent("work_entries", it.workEntryId, "payments", it.id)
        }
        expenses.forEach { requireParent("tractors", it.tractorId, "expenses", it.id) }

        val referencedWorkIds = dependencyReferences.keys.filter { it.first == "work_entries" }.map { it.second }
        val cachedWork = referencedWorkIds.mapNotNull { workEntryDao.getByIdIncludeDeleted(it) }
        val missingWork = referencedWorkIds.filterNot { id -> cachedWork.any { it.id == id } }
        val dependencyWorkDtos = fetchByIds<WorkEntryDto>("work_entries", missingWork, ownerId)
        check(dependencyWorkDtos.all { it.ownerId == ownerId }) { "Cloud dependency owner mismatch" }
        val dependencyWork = cachedWork + dependencyWorkDtos.map { it.toEntity() }
        dependencyWork.forEach { parentWork ->
            val sequence = dependencyReferences["work_entries" to parentWork.id]
            if (sequence != null) {
                for ((table, id) in listOf("customers" to parentWork.customerId, "tractors" to parentWork.tractorId)) {
                    if ((table to id) !in firstSequenceByRow) {
                        val key = table to id
                        dependencyReferences[key] = minOf(dependencyReferences[key] ?: Long.MAX_VALUE, sequence)
                    }
                }
            }
        }

        // Known parents need no network fetch. Their snapshots are rechecked under
        // the Room transaction below so concurrent local edits remain protected.
        val neededCustomerIds = dependencyReferences.keys.filter { it.first == "customers" }.map { it.second }
        val neededTractorIds = dependencyReferences.keys.filter { it.first == "tractors" }.map { it.second }
        val cachedCustomers = neededCustomerIds.mapNotNull { customerDao.getByIdIncludeDeleted(it) }
        val cachedTractors = neededTractorIds.mapNotNull { tractorDao.getByIdIncludeDeleted(it) }
        val dependencyCustomerDtos = fetchByIds<CustomerDto>("customers", neededCustomerIds.filterNot { id -> cachedCustomers.any { it.id == id } }, ownerId)
        val dependencyTractorDtos = fetchByIds<TractorDto>("tractors", neededTractorIds.filterNot { id -> cachedTractors.any { it.id == id } }, ownerId)
        check(dependencyCustomerDtos.all { it.ownerId == ownerId } && dependencyTractorDtos.all { it.ownerId == ownerId }) {
            "Cloud dependency owner mismatch"
        }
        val dependencyCustomers = cachedCustomers + dependencyCustomerDtos.map { it.toEntity() }
        val dependencyTractors = cachedTractors + dependencyTractorDtos.map { it.toEntity() }
        val allCustomers = (customers + dependencyCustomers).distinctBy { it.id }
        val allTractors = (tractors + dependencyTractors).distinctBy { it.id }
        val allWork = (work + dependencyWork).distinctBy { it.id }

        val latest = changes.last().sequenceId
        var applied = 0
        var conflictId: String? = null
        var conflictSequence: Long? = null
        val downloaded = allCustomers.size + allTractors.size + allWork.size + payments.size + expenses.size + audits.size + deletedLogs.size

        database.withTransaction {
            // Read under the same Room transaction as reconciliation so a local
            // edit committed before this transaction is never overwritten.
            // Fresh installs create provisional default tractors. An existing owner's
            // confirmed tractors replace only untouched system seeds, not user edits.
            val beforeSeedReconcile = outboxDao.getUnconfirmed()
            for (remote in allTractors) {
                val local = tractorDao.getByIdIncludeDeleted(remote.id) ?: continue
                val seed = local.id in setOf(AppDatabase.SEED_TRACTOR_BIG_ID, AppDatabase.SEED_TRACTOR_MINI_ID) &&
                    local.version == 1L && !local.isDeleted && local.displayName in setOf("મોટો ટ્રેક્ટર (Big Tractor)", "નાનો ટ્રેક્ટર (Mini Tractor)")
                val mutations = beforeSeedReconcile.filter { it.entityType == "TRACTOR" && it.entityId == local.id }
                if (seed && mutations.isNotEmpty() && mutations.all { it.operationType == "INSERT" }) {
                    tractorDao.upsert(remote)
                    database.syncVersionDao().set(com.farmeraccounts.data.local.db.entity.SyncVersionEntity("TRACTOR",remote.id,remote.version))
                    outboxDao.acknowledge(mutations.map { it.sequenceId })
                    database.uploadAttemptDao().getAll().filter { it.entityType == "TRACTOR" && it.entityId == local.id }
                        .forEach { database.uploadAttemptDao().delete(it.requestId) }
                }
            }
            val pendingIds = outboxDao.getUnconfirmed().map { it.entityId }.toSet()
            // Find the first blocked event before applying anything after it. A page may
            // contain later versions of the same row; replaying those after resolution is safe.
            for ((parentKey, referencedAt) in dependencyReferences) {
                val (table, id) = parentKey
                val parent = rowsFor(table, allCustomers, allTractors, allWork, payments, expenses, audits, deletedLogs)
                    .firstOrNull { rowId(it) == id }
                val localState = when (table) {
                    "customers" -> customerDao.getByIdIncludeDeleted(id)?.let { it.version to (it == parent as? CustomerEntity) }
                    "tractors" -> tractorDao.getByIdIncludeDeleted(id)?.let { it.version to (it == parent as? TractorEntity) }
                    "work_entries" -> workEntryDao.getByIdIncludeDeleted(id)?.let { it.version to (it == parent as? WorkEntryEntity) }
                    else -> error("Unsupported dependency table $table")
                }
                val dependencyTombstone = parent.isRemoteTombstone()
                val blocked = parent == null || (!dependencyTombstone && id in pendingIds) ||
                    (localState != null && (localState.first > versionOf(parent) ||
                        (localState.first == versionOf(parent) && !localState.second)) && !dependencyTombstone)
                if (blocked && (conflictSequence == null || referencedAt < conflictSequence!!)) {
                    conflictId = id
                    conflictSequence = referencedAt
                }
            }

            for (payment in payments) {
                val linked = payment.workEntryId?.let { id -> allWork.firstOrNull { it.id == id } }
                if (linked != null && linked.customerId != payment.customerId) {
                    val sequence = firstSequenceByRow["payments" to payment.id] ?: continue
                    if (conflictSequence == null || sequence < conflictSequence!!) {
                        conflictId = payment.id
                        conflictSequence = sequence
                    }
                }
            }

            for (change in changes) {
                val tableRows = rowsFor(change.tableName, allCustomers, allTractors, allWork, payments, expenses, audits, deletedLogs)
                val row = tableRows.firstOrNull { rowId(it) == change.recordId }
                val localVersion = when (change.tableName) {
                    "customers" -> customerDao.getByIdIncludeDeleted(change.recordId)?.let { it.version to (it == row as? CustomerEntity) }
                    "tractors" -> tractorDao.getByIdIncludeDeleted(change.recordId)?.let { it.version to (it == row as? TractorEntity) }
                    "work_entries" -> workEntryDao.getByIdIncludeDeleted(change.recordId)?.let { it.version to (it == row as? WorkEntryEntity) }
                    "payments" -> paymentDao.getByIdIncludeDeleted(change.recordId)?.let { it.version to (it == row as? PaymentEntity) }
                    "expenses" -> expenseDao.getByIdIncludeDeleted(change.recordId)?.let { it.version to (it == row as? ExpenseEntity) }
                    "audit_logs" -> auditDao.getById(change.recordId)?.let {
                        1L to (it == row as? AuditLogEntity)
                    }
                    "deleted_logs" -> deletedLogDao.getById(change.recordId)?.let {
                        1L to (it == row as? DeletedLogEntity)
                    }
                    else -> error("Unsupported sync table ${change.tableName}")
                }
                val remoteDelete = row.isRemoteTombstone()
                val serverReceipt = row is DeletedLogEntity
                val entityType = change.tableName.toEntityType()
                val serverCanonical = entityType != null && row != null &&
                    database.syncVersionDao().get(entityType, change.recordId) == versionOf(row)
                val blocked = row == null || (!remoteDelete && !serverReceipt && (
                    change.recordId in pendingIds ||
                        (localVersion != null && (localVersion.first > versionOf(row) ||
                            (localVersion.first == versionOf(row) && !localVersion.second && !serverCanonical)))
                    ))
                if (blocked && (conflictSequence == null || change.sequenceId < conflictSequence!!)) {
                    conflictId = change.recordId
                    conflictSequence = change.sequenceId
                }
            }

            val cutoff = conflictSequence?.minus(1L) ?: latest
            val eligibleIds = changes.filter { it.sequenceId <= cutoff }
                .groupBy { it.tableName }
                .mapValues { (_, rows) -> rows.map { it.recordId }.toSet() }

            val eligibleDependencyIds = dependencyReferences.filterValues { it <= cutoff }
            val dependencyTractorIds = dependencyTractors.map { it.id }.toSet()
            val dependencyWorkIds = dependencyWork.map { it.id }.toSet()
            for (row in dependencyCustomers.filter { ("customers" to it.id) in eligibleDependencyIds }) {
                val local = customerDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "CUSTOMER", row.id, local.toDto(ownerId), row.version)
                }
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && row.id !in pendingIds && database.syncVersionDao().get("CUSTOMER", row.id) == row.version)) { customerDao.upsert(row); applied++ }
                if (row.isDeleted) outboxDao.failForEntity("CUSTOMER", row.id, "REMOTE_DELETED_CONFLICT")
                saveServerVersion("CUSTOMER", row.id, row.version)
            }
            for (row in dependencyTractors.filter { ("tractors" to it.id) in eligibleDependencyIds }) {
                val local = tractorDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "TRACTOR", row.id, local.toDto(ownerId), row.version)
                }
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && row.id !in pendingIds && database.syncVersionDao().get("TRACTOR", row.id) == row.version)) { tractorDao.upsert(row); applied++ }
                if (row.isDeleted) outboxDao.failForEntity("TRACTOR", row.id, "REMOTE_DELETED_CONFLICT")
                saveServerVersion("TRACTOR", row.id, row.version)
            }
            for (row in dependencyWork.filter { ("work_entries" to it.id) in eligibleDependencyIds }) {
                val local = workEntryDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "WORK_ENTRY", row.id, local.toDto(ownerId), row.version)
                }
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && row.id !in pendingIds && database.syncVersionDao().get("WORK_ENTRY", row.id) == row.version)) { workEntryDao.upsert(row); applied++ }
                if (row.isDeleted) outboxDao.failForEntity("WORK_ENTRY", row.id, "REMOTE_DELETED_CONFLICT")
                saveServerVersion("WORK_ENTRY", row.id, row.version)
            }

            for (row in customers.filter { it.id in eligibleIds["customers"].orEmpty() }) {
                val local = customerDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "CUSTOMER", row.id, local.toDto(ownerId), row.version)
                }
                val canonical = database.syncVersionDao().get("CUSTOMER", row.id) == row.version && row.id !in pendingIds
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && canonical)) { customerDao.upsert(row); applied++ }
                saveServerVersion("CUSTOMER", row.id, row.version)
                if (row.isDeleted) outboxDao.failForEntity("CUSTOMER", row.id, "REMOTE_DELETED_CONFLICT")
            }
            for (row in tractors.filter { it.id in eligibleIds["tractors"].orEmpty() && it.id !in dependencyTractorIds }) {
                val local = tractorDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "TRACTOR", row.id, local.toDto(ownerId), row.version)
                }
                val canonical = database.syncVersionDao().get("TRACTOR", row.id) == row.version && row.id !in pendingIds
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && canonical)) { tractorDao.upsert(row); applied++ }
                saveServerVersion("TRACTOR", row.id, row.version)
                if (row.isDeleted) outboxDao.failForEntity("TRACTOR", row.id, "REMOTE_DELETED_CONFLICT")
            }
            for (row in work.filter { it.id in eligibleIds["work_entries"].orEmpty() && it.id !in dependencyWorkIds }) {
                val local = workEntryDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "WORK_ENTRY", row.id, local.toDto(ownerId), row.version)
                }
                val canonical = database.syncVersionDao().get("WORK_ENTRY", row.id) == row.version && row.id !in pendingIds
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && canonical)) { workEntryDao.upsert(row); applied++ }
                saveServerVersion("WORK_ENTRY", row.id, row.version)
                if (row.isDeleted) outboxDao.failForEntity("WORK_ENTRY", row.id, "REMOTE_DELETED_CONFLICT")
            }
            for (row in payments.filter { it.id in eligibleIds["payments"].orEmpty() }) {
                val local = paymentDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "PAYMENT", row.id, local.toDto(ownerId), row.version)
                }
                val canonical = database.syncVersionDao().get("PAYMENT", row.id) == row.version && row.id !in pendingIds
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && canonical)) { paymentDao.upsert(row); applied++ }
                saveServerVersion("PAYMENT", row.id, row.version)
                if (row.isDeleted) outboxDao.failForEntity("PAYMENT", row.id, "REMOTE_DELETED_CONFLICT")
            }
            for (row in expenses.filter { it.id in eligibleIds["expenses"].orEmpty() }) {
                val local = expenseDao.getByIdIncludeDeleted(row.id)
                if (row.isDeleted && local != null && (row.id in pendingIds || local.version >= row.version && local != row)) {
                    recordRemoteDeleteConflict(ownerId, "EXPENSE", row.id, local.toDto(ownerId), row.version)
                }
                val canonical = database.syncVersionDao().get("EXPENSE", row.id) == row.version && row.id !in pendingIds
                if (row.isDeleted || local == null || row.version > local.version || (row.version == local.version && canonical)) { expenseDao.upsert(row); applied++ }
                saveServerVersion("EXPENSE", row.id, row.version)
                if (row.isDeleted) outboxDao.failForEntity("EXPENSE", row.id, "REMOTE_DELETED_CONFLICT")
            }
            for (row in audits.filter { it.id in eligibleIds["audit_logs"].orEmpty() }) {
                if (auditDao.getById(row.id) == null) { auditDao.insert(row); applied++ }
            }
            for (row in deletedLogs.filter { it.id in eligibleIds["deleted_logs"].orEmpty() }) {
                deletedLogDao.upsertConfirmed(row)
                outboxDao.acknowledgeDeletedLog(row.id)
                applied++
            }
        }

        val nextCursor = SyncCursorPolicy.safeNext(cursor, changes, conflictSequence)
        if (nextCursor > cursor) cursorPreferences.set(ownerId, nextCursor)
        HydrationResult(
            downloadedCount = downloaded,
            appliedCount = applied,
            skippedConflictCount = if (conflictId == null) 0 else 1,
            nextCursor = nextCursor,
            hasMore = conflictId == null && changes.size == PAGE_SIZE,
            unresolvedConflictIds = listOfNotNull(conflictId)
        )
    }

    private suspend inline fun <reified T : Any> fetchByIds(table: String, ids: List<String>, ownerId: String): List<T> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(ID_BATCH_SIZE).flatMap { chunk ->
            supabaseClient.postgrest[table].select {
                filter { eq("owner_id", ownerId); isIn("id", chunk) }
            }.decodeList<T>()
        }
    }

    private suspend fun recordRemoteDeleteConflict(
        ownerId: String,
        entityType: String,
        entityId: String,
        localSnapshot: Any,
        remoteVersion: Long
    ) {
        val snapshot = when (localSnapshot) {
            is com.farmeraccounts.data.remote.dto.CustomerDto -> Json.encodeToString(localSnapshot)
            is com.farmeraccounts.data.remote.dto.TractorDto -> Json.encodeToString(localSnapshot)
            is com.farmeraccounts.data.remote.dto.WorkEntryDto -> Json.encodeToString(localSnapshot)
            is com.farmeraccounts.data.remote.dto.PaymentDto -> Json.encodeToString(localSnapshot)
            is com.farmeraccounts.data.remote.dto.ExpenseDto -> Json.encodeToString(localSnapshot)
            else -> error("Unsupported local conflict snapshot")
        }
        val auditId = UUID.nameUUIDFromBytes("$ownerId:$entityType:$entityId:$remoteVersion".toByteArray(Charsets.UTF_8)).toString()
        if (auditDao.getById(auditId) == null) {
            auditDao.insert(AuditLogEntity(auditId, entityType, entityId, "REMOTE_DELETE_CONFLICT", snapshot))
        }
    }

    private suspend fun saveServerVersion(entityType: String, entityId: String, version: Long) {
        database.syncVersionDao().set(
            com.farmeraccounts.data.local.db.entity.SyncVersionEntity(entityType, entityId, version)
        )
    }

    private fun Any?.isRemoteTombstone(): Boolean = when (this) {
        is CustomerEntity -> isDeleted
        is TractorEntity -> isDeleted
        is WorkEntryEntity -> isDeleted
        is PaymentEntity -> isDeleted
        is ExpenseEntity -> isDeleted
        else -> false
    }

    private fun String.toEntityType(): String? = when (this) {
        "customers" -> "CUSTOMER"
        "tractors" -> "TRACTOR"
        "work_entries" -> "WORK_ENTRY"
        "payments" -> "PAYMENT"
        "expenses" -> "EXPENSE"
        else -> null
    }

    private fun rowsFor(
        table: String,
        customers: List<CustomerEntity>, tractors: List<TractorEntity>, work: List<WorkEntryEntity>,
        payments: List<PaymentEntity>, expenses: List<ExpenseEntity>, audits: List<AuditLogEntity>
        , deletedLogs: List<DeletedLogEntity>
    ): List<Any> = when (table) {
        "customers" -> customers
        "tractors" -> tractors
        "work_entries" -> work
        "payments" -> payments
        "expenses" -> expenses
        "audit_logs" -> audits
        "deleted_logs" -> deletedLogs
        else -> error("Unsupported sync table $table")
    }

    private fun versionOf(row: Any): Long = when (row) {
        is CustomerEntity -> row.version
        is TractorEntity -> row.version
        is WorkEntryEntity -> row.version
        is PaymentEntity -> row.version
        is ExpenseEntity -> row.version
        is AuditLogEntity -> 1L
        is DeletedLogEntity -> 1L
        else -> error("Unsupported synchronized row")
    }

    private fun rowId(row: Any): String = when (row) {
        is CustomerEntity -> row.id
        is TractorEntity -> row.id
        is WorkEntryEntity -> row.id
        is PaymentEntity -> row.id
        is ExpenseEntity -> row.id
        is AuditLogEntity -> row.id
        is DeletedLogEntity -> row.id
        else -> error("Unsupported synchronized row")
    }

    private companion object {
        const val PAGE_SIZE = 250
        const val ID_BATCH_SIZE = 100
    }
}
