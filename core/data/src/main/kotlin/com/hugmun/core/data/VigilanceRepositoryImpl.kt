/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.data

import com.hugmun.core.common.AppDispatchers
import com.hugmun.core.database.dao.VigilanceDao
import com.hugmun.core.database.entity.VigilanceSessionEntity
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.core.domain.VigilanceRepository
import com.hugmun.core.model.AdverseEvent
import com.hugmun.core.model.SessionOutcome
import com.hugmun.engine.psychophysics.CompletedTrial
import com.hugmun.engine.psychophysics.UfovLevel
import com.hugmun.engine.psychophysics.UfovSessionResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

public class VigilanceRepositoryImpl(private val dao: VigilanceDao, private val dispatchers: AppDispatchers) :
    VigilanceRepository {

    override fun observeResults(limit: Int): Flow<List<StoredVigilanceSession>> =
        dao.observeSessions(limit).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun latestResult(): StoredVigilanceSession? = withContext(dispatchers.io) {
        dao.latestSession()?.toDomain()
    }

    override suspend fun save(
        startedAt: Instant,
        endedAt: Instant,
        outcome: SessionOutcome,
        result: UfovSessionResult?,
        trials: List<CompletedTrial>,
        adverseEvent: AdverseEvent?,
    ): Long = withContext(dispatchers.io) {
        val session = VigilanceSessionEntity(
            startedAtEpochMillis = startedAt.toEpochMilliseconds(),
            endedAtEpochMillis = endedAt.toEpochMilliseconds(),
            outcome = outcome.name,
            level = (result?.level ?: UfovLevel.PRIMARY).id,
            refreshHz = result?.timing?.refreshHz ?: UNKNOWN_REFRESH_HZ,
            // Null rather than a placeholder. A session that never converged has no
            // threshold, and inventing one would corrupt the trend it feeds.
            thresholdMillis = result?.thresholdMillis,
            thresholdFrames = result?.thresholdFrames,
            isFloorLimited = result?.isFloorLimited ?: false,
            isQualityAcceptable = result?.isQualityAcceptable ?: false,
            reversalsUsed = result?.reversalsUsed ?: 0,
            scoredTrials = result?.scoredTrials ?: trials.count { it.outcome.name != DISCARDED },
            correctTrials = result?.correctTrials ?: 0,
            discardedTrials = result?.discardedTrials ?: trials.count { it.outcome.name == DISCARDED },
            stopReason = outcome.name,
            adverseEventKind = adverseEvent?.kind?.name,
            adverseEventNote = adverseEvent?.note,
        )

        dao.insertSessionWithTrials(session) { id -> trials.map { it.toEntity(id) } }
    }

    /**
     * Thresholds eligible for the change detector, oldest first.
     *
     * Eligibility is decided by [StoredVigilanceSession.validity] in Kotlin rather than
     * by a `WHERE` clause, because the test for above-chance performance is a binomial
     * tail that SQL has no business computing, and because two definitions of "a session
     * we trust" would inevitably drift apart. The SQL still narrows to rows that have a
     * threshold at all, so the amount read back stays bounded.
     *
     * Oldest-first because [com.hugmun.core.domain.ReliableChange] treats the list as a
     * history and the caller should not have to remember to reverse it.
     */
    public suspend fun thresholdHistory(
        level: UfovLevel = UfovLevel.PRIMARY,
        limit: Int = HISTORY_LIMIT,
    ): List<Double> = withContext(dispatchers.io) {
        dao.recentMeasuredSessions(level.id, limit)
            .map { it.toDomain() }
            .filter { it.validity.isTrendEligible }
            .mapNotNull { it.thresholdMillis }
            .reversed()
    }

    private companion object {
        const val DISCARDED = "DISCARDED"
        const val UNKNOWN_REFRESH_HZ = 0.0
        const val HISTORY_LIMIT = 30
    }
}
