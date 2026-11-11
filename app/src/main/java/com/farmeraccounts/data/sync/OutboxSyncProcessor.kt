package com.farmeraccounts.data.sync

import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.local.db.entity.UploadAttemptEntity
import com.farmeraccounts.data.local.db.entity.SyncVersionEntity
import com.farmeraccounts.data.remote.mapper.TimestampIsoConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import com.farmeraccounts.data.remote.mapper.toDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OutboxSyncResult(
    val processedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
    val hasRetryableWork: Boolean = false
)

class OutboxSyncProcessor private constructor(
    private val database: AppDatabase,
    private val supabaseClient: SupabaseClient?,
    private val uploadOverride: (suspend (OutboxEntity, String) -> Unit)?,
    private val receiptOverride: (suspend (JsonObject) -> String)?
) {
    constructor(database: AppDatabase, supabaseClient: SupabaseClient) : this(database, supabaseClient, null, null)

    internal constructor(database: AppDatabase, upload: suspend (OutboxEntity, String) -> Unit) :
        this(database, null, upload, null)
    internal constructor(database: AppDatabase, submit: suspend (JsonObject) -> String) :
        this(database, null, null, submit)
    private val outboxDao = database.outboxDao()
    private val customerDao = database.customerDao()
    private val tractorDao = database.tractorDao()
    private val workEntryDao = database.workEntryDao()
    private val paymentDao = database.paymentDao()
    private val expenseDao = database.expenseDao()
    private val auditDao = database.auditLogDao()

    suspend fun processPending(ownerId: String): OutboxSyncResult = withContext(Dispatchers.IO) {
        // The caller serializes complete sync runs. Recover claims left by cancellation/process death.
        database.withTransaction {
            outboxDao.recoverInProgress()
            val unconfirmed = outboxDao.getUnconfirmed()
            for (attempt in database.uploadAttemptDao().getAll()) {
                val ids = json.decodeFromString<List<Long>>(attempt.sequenceIdsJson)
                val rejected = unconfirmed.filter { it.sequenceId in ids }
                val corrected = unconfirmed.any { it.entityType.equals(attempt.entityType,true) &&
                    it.entityId == attempt.entityId && it.syncStatus == "PENDING" && it.sequenceId > (ids.maxOrNull() ?: 0L) }
                if (rejected.isNotEmpty() && rejected.all { it.syncStatus == "FAILED" } && corrected) {
                    // Only conclusively rejected requests may be replaced by a correction.
                    // Uncertain/transient requests always replay their frozen envelope.
                    database.uploadAttemptDao().delete(attempt.requestId)
                }
            }
            // A rejected FK request has not persisted. If its parent is still queued,
            // release only that rejected envelope and rebuild it in dependency order.
            // Never release an uncertain request (timeout / lost response).
            val queued = outboxDao.getUnconfirmed()
            for (failed in queued.filter { it.syncStatus == "FAILED" &&
                it.lastError?.contains("foreign key constraint", ignoreCase = true) == true }) {
                val parents = when (failed.entityType.uppercase()) {
                    "WORK_ENTRY" -> workEntryDao.getByIdIncludeDeleted(failed.entityId)?.let {
                        setOf(it.customerId, it.tractorId)
                    }.orEmpty()
                    "PAYMENT" -> database.paymentDao().getByIdIncludeDeleted(failed.entityId)?.let {
                        setOfNotNull(it.customerId, it.workEntryId)
                    }.orEmpty()
                    else -> emptySet()
                }
                if (queued.any { it.entityId in parents && it.syncStatus == "PENDING" }) {
                    database.uploadAttemptDao().getAll().filter {
                        it.entityType.equals(failed.entityType, true) && it.entityId == failed.entityId
                    }.forEach { database.uploadAttemptDao().delete(it.requestId) }
                    outboxDao.updateStatusAndError(failed.sequenceId, "PENDING", failed.retryCount, null)
                }
            }
            outboxDao.requeueCorrectedFailures()
        }
        val blockedCount = outboxDao.getFailedCount()
        if (blockedCount > 0) {
            return@withContext OutboxSyncResult(0, blockedCount, listOf("Unconfirmed mutations need repair"))
        }

        if (uploadOverride == null) {
            // Replay the exact persisted payload/request before capturing newer edits.
            for (attempt in database.uploadAttemptDao().getAll()) {
                try { confirmAttempt(attempt, ownerId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { return@withContext failAttempt(attempt, error) }
            }
        }
        val captured = outboxDao.getPendingBatch(MAX_MUTATIONS_PER_PASS)
        val groups = captured.groupBy { it.entityType.uppercase() to it.entityId }.values
        var processed = 0
        for (items in groups) {
            val latest = items.maxBy { it.sequenceId }
            database.withTransaction {
                items.forEach { outboxDao.updateStatus(it.sequenceId, "IN_PROGRESS", it.retryCount) }
            }
            try {
                if (uploadOverride != null) uploadOverride.invoke(latest, ownerId)
                else {
                    val attempt = freezeAttempt(latest, items, ownerId)
                    database.uploadAttemptDao().insert(attempt)
                    confirmAttempt(attempt, ownerId)
                }
                // Only the captured IDs are acknowledged. Edits created during the request stay pending.
                // Without immutable payloads/receipts, a lost response can still cause a repeated upsert.
                database.withTransaction { outboxDao.acknowledge(items.map { it.sequenceId }) }
                processed += items.size
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val retryable = isRetryable(error)
                // RestException.message includes request headers; never persist it (authentication data).
                val detail = if (error is RestException) error.error else error.message
                val message = (detail ?: "Unknown sync error").take(500)
                database.withTransaction {
                    items.forEach {
                        outboxDao.updateStatusAndError(
                            it.sequenceId, if (retryable) "PENDING" else "FAILED",
                            (it.retryCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), message
                        )
                    }
                }
                // Stop before any children if a parent was not confirmed.
                return@withContext OutboxSyncResult(
                    processed, items.size,
                    listOf("Entity ${latest.entityType}:${latest.entityId} failed: $message"),
                    retryable && (error !is RestException || error.statusCode != 401)
                )
            }
        }
        OutboxSyncResult(processed, 0, emptyList(), outboxDao.getPendingCount() > 0)
    }

    private fun isRetryable(error: Exception): Boolean = when (error) {
        is RestException -> error.statusCode == 401 || error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
        is IllegalStateException, is IllegalArgumentException -> false
        else -> true
    }

    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class MutationReceipt(val request_id: String, val version: Long)

    private suspend fun freezeAttempt(item: OutboxEntity, items: List<OutboxEntity>, ownerId: String): UploadAttemptEntity {
        val type = item.entityType.uppercase()
        val payload = when (type) {
            "CUSTOMER" -> json.encodeToJsonElement(customerDao.getByIdIncludeDeleted(item.entityId)?.toDto(ownerId) ?: error("Customer missing"))
            "TRACTOR" -> json.encodeToJsonElement(tractorDao.getByIdIncludeDeleted(item.entityId)?.toDto(ownerId) ?: error("Tractor missing"))
            "WORK_ENTRY" -> json.encodeToJsonElement(workEntryDao.getByIdIncludeDeleted(item.entityId)?.toDto(ownerId) ?: error("Work missing"))
            "PAYMENT" -> json.encodeToJsonElement(paymentDao.getByIdIncludeDeleted(item.entityId)?.toDto(ownerId) ?: error("Payment missing"))
            "EXPENSE" -> json.encodeToJsonElement(expenseDao.getByIdIncludeDeleted(item.entityId)?.toDto(ownerId) ?: error("Expense missing"))
            "AUDIT_LOG" -> json.encodeToJsonElement(auditDao.getById(item.entityId)?.toDto(ownerId) ?: error("Audit missing"))
            "DELETED_LOG" -> {
                val log = database.deletedLogDao().getById(item.entityId) ?: error("Deleted history missing")
                buildJsonObject {
                    put("id",log.id); put("owner_id",ownerId); put("entity_type",log.entityType)
                    put("entity_id",log.entityId); put("snapshot_json",log.snapshotJson)
                    put("reason",log.reason); put("timestamp",TimestampIsoConverter.toIsoString(log.timestamp))
                }
            }
            else -> error("Unsupported mutation type")
        }.jsonObject
        val requestId = UUID.randomUUID().toString()
        val expected = database.syncVersionDao().get(type,item.entityId)
        val log = if (payload["is_deleted"]?.jsonPrimitive?.booleanOrNull == true)
            database.deletedLogDao().getLatestForEntity(type,item.entityId) else null
        val params = buildJsonObject {
            put("p_request_id",requestId); put("p_entity_type",type); put("p_entity_id",item.entityId)
            put("p_expected_version", expected?.let(::JsonPrimitive) ?: JsonNull)
            put("p_payload",payload)
            put("p_deletion_log_id",log?.id?.let(::JsonPrimitive) ?: JsonNull)
            put("p_deletion_reason",log?.reason?.let(::JsonPrimitive) ?: JsonNull)
            put("p_deletion_snapshot",log?.snapshotJson?.let(::JsonPrimitive) ?: JsonNull)
        }
        return UploadAttemptEntity(requestId,type,item.entityId,params.toString(),expected,
            payload["version"]?.jsonPrimitive?.longOrNull ?: 1L,json.encodeToString(items.map { it.sequenceId }))
    }

    private suspend fun confirmAttempt(attempt: UploadAttemptEntity, ownerId: String) {
        val params = json.parseToJsonElement(attempt.payloadJson).jsonObject
        check(params.getValue("p_payload").jsonObject["owner_id"]?.jsonPrimitive?.content == ownerId) {
            "This offline ledger belongs to a different owner"
        }
        val data = receiptOverride?.invoke(params)
            ?: requireNotNull(supabaseClient).postgrest.rpc("apply_ledger_mutation",params).data
        val receipt = json.decodeFromString<MutationReceipt>(data)
        check(receipt.request_id == attempt.requestId) { "Server confirmation mismatch" }
        database.withTransaction {
            database.syncVersionDao().set(SyncVersionEntity(attempt.entityType,attempt.entityId,receipt.version))
            outboxDao.acknowledge(json.decodeFromString<List<Long>>(attempt.sequenceIdsJson))
            database.uploadAttemptDao().delete(attempt.requestId)
        }
    }

    private suspend fun failAttempt(attempt: UploadAttemptEntity, error: Exception): OutboxSyncResult {
        val retryable = isRetryable(error)
        val message = if (error is RestException) error.error.take(500) else "Backup needs review"
        database.withTransaction {
            for (id in json.decodeFromString<List<Long>>(attempt.sequenceIdsJson)) {
                outboxDao.updateStatusAndError(id,if(retryable) "PENDING" else "FAILED",1,message)
            }
        }
        return OutboxSyncResult(0,1,listOf(message),retryable && (error !is RestException || error.statusCode != 401))
    }

    companion object {
        internal const val MAX_MUTATIONS_PER_PASS = 200
    }
}
