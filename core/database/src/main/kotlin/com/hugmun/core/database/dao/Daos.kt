/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.hugmun.core.database.entity.AdverseEventEntity
import com.hugmun.core.database.entity.PracticeLockEntity
import com.hugmun.core.database.entity.ProtocolStateEntity
import com.hugmun.core.database.entity.SafetyStateEntity
import com.hugmun.core.database.entity.VigilanceSessionEntity
import com.hugmun.core.database.entity.VigilanceTrialEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProtocolDao {
    @Query("SELECT * FROM protocol_state WHERE id = :id")
    public fun observe(id: Int = ProtocolStateEntity.SINGLETON_ID): Flow<ProtocolStateEntity?>

    @Query("SELECT * FROM protocol_state WHERE id = :id")
    public suspend fun get(id: Int = ProtocolStateEntity.SINGLETON_ID): ProtocolStateEntity?

    @Upsert
    public suspend fun upsert(state: ProtocolStateEntity)

    @Query("DELETE FROM protocol_state")
    public suspend fun clear()
}

@Dao
public interface VigilanceDao {
    @Query("SELECT * FROM vigilance_session ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    public fun observeSessions(limit: Int): Flow<List<VigilanceSessionEntity>>

    @Query("SELECT * FROM vigilance_session ORDER BY startedAtEpochMillis DESC LIMIT 1")
    public suspend fun latestSession(): VigilanceSessionEntity?

    /**
     * Candidate sessions for the trend, newest first.
     *
     * Only the cheap structural filter lives here — a row with no threshold can never
     * contribute. Whether a threshold is *trustworthy* is decided upstream, because that
     * test includes a binomial tail against the guess rate and belongs with the rest of
     * the measurement logic rather than split across a `WHERE` clause.
     */
    @Query(
        """
        SELECT * FROM vigilance_session
        WHERE thresholdMillis IS NOT NULL
          AND level = :level
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    public suspend fun recentMeasuredSessions(level: Int, limit: Int): List<VigilanceSessionEntity>

    @Query("SELECT * FROM vigilance_trial WHERE sessionId = :sessionId ORDER BY trialIndex")
    public suspend fun trialsFor(sessionId: Long): List<VigilanceTrialEntity>

    @Insert
    public suspend fun insertSession(session: VigilanceSessionEntity): Long

    @Insert
    public suspend fun insertTrials(trials: List<VigilanceTrialEntity>)

    /**
     * A session and its trials are written together or not at all.
     *
     * A session row without its trials would be a threshold nobody can re-derive, which
     * defeats the point of storing trials.
     */
    @Transaction
    public suspend fun insertSessionWithTrials(
        session: VigilanceSessionEntity,
        trials: (Long) -> List<VigilanceTrialEntity>,
    ): Long {
        val id = insertSession(session)
        insertTrials(trials(id))
        return id
    }

    @Query("DELETE FROM vigilance_session")
    public suspend fun clearSessions()

    @Query("DELETE FROM vigilance_trial")
    public suspend fun clearTrials()
}

@Dao
public interface SafetyDao {
    @Query("SELECT * FROM safety_state WHERE id = :id")
    public fun observeState(id: Int = SafetyStateEntity.SINGLETON_ID): Flow<SafetyStateEntity?>

    @Query("SELECT * FROM safety_state WHERE id = :id")
    public suspend fun state(id: Int = SafetyStateEntity.SINGLETON_ID): SafetyStateEntity?

    @Upsert
    public suspend fun upsertState(state: SafetyStateEntity)

    @Query("SELECT * FROM practice_lock")
    public fun observeLocks(): Flow<List<PracticeLockEntity>>

    @Query("SELECT * FROM practice_lock")
    public suspend fun locks(): List<PracticeLockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun lock(lock: PracticeLockEntity)

    @Delete
    public suspend fun unlock(lock: PracticeLockEntity)

    @Query("DELETE FROM practice_lock WHERE practiceId = :practiceId")
    public suspend fun unlockById(practiceId: String)

    @Insert
    public suspend fun recordAdverseEvent(event: AdverseEventEntity)

    @Query("SELECT * FROM adverse_event ORDER BY reportedAtEpochMillis DESC")
    public fun observeAdverseEvents(): Flow<List<AdverseEventEntity>>
}
