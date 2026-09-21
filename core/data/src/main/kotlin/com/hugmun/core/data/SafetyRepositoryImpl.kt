/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.data

import com.hugmun.core.common.AppDispatchers
import com.hugmun.core.common.TimeSource
import com.hugmun.core.database.dao.SafetyDao
import com.hugmun.core.database.entity.AdverseEventEntity
import com.hugmun.core.database.entity.PracticeLockEntity
import com.hugmun.core.database.entity.SafetyStateEntity
import com.hugmun.core.domain.SafetyRepository
import com.hugmun.core.domain.SafetyState
import com.hugmun.core.model.AdverseEvent
import com.hugmun.core.model.Practice
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Safety state.
 *
 * Every write here is one-way in the direction of caution: an adverse event locks a
 * practice, and only an explicit re-consent unlocks it. There is deliberately no
 * "clear all locks" operation.
 */
public class SafetyRepositoryImpl(
    private val dao: SafetyDao,
    private val dispatchers: AppDispatchers,
    private val timeSource: TimeSource,
) : SafetyRepository {

    override fun observe(): Flow<SafetyState> = combine(dao.observeState(), dao.observeLocks()) { state, locks ->
        buildState(state, locks)
    }.flowOn(dispatchers.io)

    override suspend fun current(): SafetyState = withContext(dispatchers.io) {
        buildState(dao.state(), dao.locks())
    }

    override suspend fun recordPhotosensitivityScreening(passed: Boolean, at: Instant) {
        withContext(dispatchers.io) {
            val existing = dao.state() ?: emptyState()
            dao.upsertState(
                existing.copy(
                    photosensitivityScreeningPassedAtEpochMillis =
                    if (passed) at.toEpochMilliseconds() else null,
                    // Failing the screen also withdraws any consent already given.
                    photicConsentGrantedAtEpochMillis =
                    if (passed) existing.photicConsentGrantedAtEpochMillis else null,
                ),
            )
        }
    }

    override suspend fun setPhoticConsent(granted: Boolean, at: Instant) {
        withContext(dispatchers.io) {
            val existing = dao.state() ?: emptyState()
            dao.upsertState(
                existing.copy(
                    photicConsentGrantedAtEpochMillis = if (granted) at.toEpochMilliseconds() else null,
                ),
            )
        }
    }

    override suspend fun lockPractice(practice: Practice, reason: String, at: Instant) {
        withContext(dispatchers.io) {
            dao.lock(
                PracticeLockEntity(
                    practiceId = practice.id,
                    reason = reason,
                    lockedAtEpochMillis = at.toEpochMilliseconds(),
                ),
            )
        }
    }

    override suspend fun unlockPractice(practice: Practice, at: Instant) {
        withContext(dispatchers.io) { dao.unlockById(practice.id) }
    }

    override suspend fun recordAdverseEvent(practice: Practice, event: AdverseEvent) {
        withContext(dispatchers.io) {
            dao.recordAdverseEvent(
                AdverseEventEntity(
                    practiceId = practice.id,
                    kind = event.kind.name,
                    reportedAtEpochMillis = event.reportedAt.toEpochMilliseconds(),
                    note = event.note,
                ),
            )
            if (event.kind.locksPractice) {
                lockPractice(practice, event.kind.name, event.reportedAt)
            }
        }
    }

    /**
     * Screening expires after 90 days.
     *
     * Health changes, and a negative answer given a year ago is not evidence about today.
     * The expiry is applied on read rather than by a background job, so it cannot be
     * missed because the device was asleep.
     */
    private fun buildState(state: SafetyStateEntity?, locks: List<PracticeLockEntity>): SafetyState {
        val now = timeSource.now()
        val screenedAt = state?.photosensitivityScreeningPassedAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            ?.takeIf { now - it <= SCREENING_VALIDITY }

        val consentAt = state?.photicConsentGrantedAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            ?.takeIf { screenedAt != null }

        val today = timeSource.today().toEpochDays()
        val minutesToday = if (state?.photicUsageDayEpochDay == today) {
            state.photicMinutesUsedToday
        } else {
            0
        }

        return SafetyState(
            photosensitivityScreeningPassedAt = screenedAt,
            photicConsentGrantedAt = consentAt,
            lockedPractices = locks.mapNotNull { lock ->
                Practice.fromId(lock.practiceId)?.let { it to lock.reason }
            }.toMap(),
            photicMinutesUsedToday = minutesToday,
        )
    }

    private fun emptyState() = SafetyStateEntity(
        photosensitivityScreeningPassedAtEpochMillis = null,
        photicConsentGrantedAtEpochMillis = null,
        photicMinutesUsedToday = 0,
        photicUsageDayEpochDay = null,
    )

    private companion object {
        /** Photosensitivity screening must be re-confirmed every 90 days. */
        val SCREENING_VALIDITY = kotlin.time.Duration.parse("90d")
    }
}
