package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outbox: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE sync_status = 'PENDING' ORDER BY sequence_id ASC")
    fun getPendingFlow(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE sync_status = 'PENDING' ORDER BY sequence_id ASC")
    suspend fun getPending(): List<OutboxEntity>

    // Rank before LIMIT so a bounded batch cannot select children ahead of pending parents.
    @Query("""SELECT * FROM outbox WHERE sync_status = 'PENDING'
        ORDER BY CASE
            WHEN EXISTS (SELECT 1 FROM outbox deletion
                WHERE deletion.entity_type = outbox.entity_type AND deletion.entity_id = outbox.entity_id
                AND deletion.operation_type = 'SOFT_DELETE') AND EXISTS (
                SELECT 1 FROM sync_versions v WHERE v.entity_type = UPPER(outbox.entity_type)
                AND v.entity_id = outbox.entity_id
            ) THEN CASE UPPER(entity_type)
                WHEN 'PAYMENT' THEN 6 WHEN 'WORK_ENTRY' THEN 7 WHEN 'EXPENSE' THEN 8
                WHEN 'CUSTOMER' THEN 9 WHEN 'TRACTOR' THEN 10 ELSE 99 END
            ELSE CASE UPPER(entity_type)
                WHEN 'CUSTOMER' THEN 1 WHEN 'TRACTOR' THEN 2 WHEN 'WORK_ENTRY' THEN 3
                WHEN 'PAYMENT' THEN 4 WHEN 'EXPENSE' THEN 5 WHEN 'AUDIT_LOG' THEN 11 ELSE 99 END END,
            sequence_id ASC LIMIT :limit""")
    suspend fun getPendingBatch(limit: Int): List<OutboxEntity>

    @Query("UPDATE outbox SET sync_status = 'PENDING' WHERE sync_status = 'IN_PROGRESS'")
    suspend fun recoverInProgress()

    // Only immutable deletion-log IDs can be acknowledged by entity identity after server confirmation.
    @Query("DELETE FROM outbox WHERE UPPER(entity_type) = 'DELETED_LOG' AND entity_id = :logId")
    suspend fun acknowledgeDeletedLog(logId: String)

    @Query("DELETE FROM outbox WHERE sequence_id IN (:sequenceIds)")
    suspend fun acknowledge(sequenceIds: List<Long>)

    // A user correction re-enrolls this entity, without automatically retrying unchanged failures.
    @Query("""UPDATE outbox SET sync_status = 'PENDING', retry_count = 0, last_error = NULL
        WHERE sync_status = 'FAILED' AND EXISTS (
            SELECT 1 FROM outbox AS newer WHERE newer.sync_status = 'PENDING'
            AND UPPER(newer.entity_type) = UPPER(outbox.entity_type)
            AND newer.entity_id = outbox.entity_id AND newer.sequence_id > outbox.sequence_id
        )""")
    suspend fun requeueCorrectedFailures()

    @Query("SELECT COUNT(*) FROM outbox WHERE sync_status = 'FAILED'")
    suspend fun getFailedCount(): Int

    @Query("SELECT * FROM outbox WHERE sync_status = 'FAILED' ORDER BY sequence_id ASC")
    suspend fun getFailed(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE sync_status IN ('PENDING', 'FAILED', 'IN_PROGRESS') ORDER BY sequence_id ASC")
    suspend fun getUnconfirmed(): List<OutboxEntity>

    @Query("""UPDATE outbox SET sync_status = 'FAILED', last_error = :safeError
        WHERE UPPER(entity_type) = UPPER(:entityType) AND entity_id = :entityId
        AND sync_status IN ('PENDING', 'IN_PROGRESS')""")
    suspend fun failForEntity(entityType: String, entityId: String, safeError: String)

    @Query("UPDATE outbox SET sync_status = :status, retry_count = :retryCount, last_error = :lastError WHERE sequence_id = :sequenceId")
    suspend fun updateStatusAndError(sequenceId: Long, status: String, retryCount: Int, lastError: String?)

    @Query("UPDATE outbox SET sync_status = :status, retry_count = :retryCount WHERE sequence_id = :sequenceId")
    suspend fun updateStatus(sequenceId: Long, status: String, retryCount: Int)

    @Query("DELETE FROM outbox WHERE sequence_id = :sequenceId")
    suspend fun delete(sequenceId: Long)

    @Query("DELETE FROM outbox WHERE sync_status = 'CONFIRMED'")
    suspend fun deleteConfirmed()

    @Query("SELECT COALESCE(MAX(sequence_id), 0) FROM outbox")
    fun getLatestSequenceFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM outbox WHERE sync_status IN ('PENDING', 'FAILED', 'IN_PROGRESS')")
    suspend fun getUnconfirmedCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE sync_status IN ('PENDING', 'FAILED', 'IN_PROGRESS')")
    fun getUnconfirmedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE sync_status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE sync_status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>
}

