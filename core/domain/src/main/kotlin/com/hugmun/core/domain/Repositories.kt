/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.domain

import com.hugmun.core.model.AdverseEvent
import com.hugmun.core.model.Practice
import com.hugmun.core.model.SessionOutcome
import com.hugmun.engine.psychophysics.BinomialTest
import com.hugmun.engine.psychophysics.CompletedTrial
import com.hugmun.engine.psychophysics.DisplayTiming
import com.hugmun.engine.psychophysics.ThresholdEstimate
import com.hugmun.engine.psychophysics.ThresholdValidity
import com.hugmun.engine.psychophysics.UfovSession
import com.hugmun.engine.psychophysics.UfovSessionResult
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Ports, in the hexagonal sense.
 *
 * Feature modules depend on these interfaces and never on an implementation, which is
 * what keeps the module boundaries meaningful in the absence of a DI framework
 * (see ADR 0002).
 */

/** The user's progress through the ACTIVE-derived training protocol. */
public interface ProtocolRepository {
    public fun observeState(): Flow<TrainingProtocol.State?>

    public suspend fun currentState(): TrainingProtocol.State?

    /** Called once, when the user starts training for the first time. */
    public suspend fun enrol(on: LocalDate): TrainingProtocol.State

    public suspend fun recordCompletedSession(on: LocalDate): TrainingProtocol.State
}

/**
 * «Зоркость» sessions.
 *
 * Trials are persisted individually rather than only as a summary. That is what makes
 * the export useful to a clinician, and what lets a threshold be re-derived offline if
 * the estimator ever changes — see `docs/research/05-measurement.md` §7.
 */
public interface VigilanceRepository {
    public fun observeResults(limit: Int = DEFAULT_HISTORY): Flow<List<StoredVigilanceSession>>

    public suspend fun latestResult(): StoredVigilanceSession?

    public suspend fun save(
        startedAt: Instant,
        endedAt: Instant,
        outcome: SessionOutcome,
        result: UfovSessionResult?,
        trials: List<CompletedTrial>,
        adverseEvent: AdverseEvent?,
    ): Long

    public companion object {
        public const val DEFAULT_HISTORY: Int = 60
    }
}

/** One stored «Зоркость» session, as the history and trend views see it. */
public data class StoredVigilanceSession(
    public val id: Long,
    public val startedAt: Instant,
    public val endedAt: Instant,
    public val outcome: SessionOutcome,
    public val thresholdMillis: Double?,
    public val refreshHz: Double,
    public val isFloorLimited: Boolean,
    public val isQualityAcceptable: Boolean,
    public val scoredTrials: Int,
    public val correctTrials: Int,
    public val discardedTrials: Int,
    public val reversalsUsed: Int,
    public val accuracy: Double,
) {
    /**
     * What this stored session is allowed to claim, recomputed from what was stored.
     *
     * Derived rather than persisted on purpose. A verdict written into the row at save
     * time would freeze whatever the rules were that day, and — worse — would grandfather
     * in every session recorded before the rules existed. Recomputing means a session
     * that should never have been trusted stops being trusted the moment we work that
     * out, including retroactively.
     */
    public val validity: ThresholdValidity
        get() {
            if (thresholdMillis == null) return ThresholdValidity.AT_CHANCE
            val timing = DisplayTiming(refreshHz)
            val config = UfovSession.defaultConfigFor(timing)
            val frames = thresholdMillis / timing.frameMillis
            val aboveChance = BinomialTest.upperTailProbability(
                successes = correctTrials,
                trials = scoredTrials,
                probability = UfovSessionResult.GUESS_RATE,
            ) < UfovSessionResult.CHANCE_ALPHA
            return when {
                !aboveChance -> ThresholdValidity.AT_CHANCE
                !isQualityAcceptable -> ThresholdValidity.DISPLAY_UNRELIABLE
                estimateOf(frames).isCeilingLimited(config) -> ThresholdValidity.CEILING_LIMITED
                else -> ThresholdValidity.USABLE
            }
        }

    private fun estimateOf(frames: Double): ThresholdEstimate = ThresholdEstimate(
        frames = frames,
        reversalsUsed = reversalsUsed,
        totalTrials = scoredTrials + discardedTrials,
        discardedTrials = discardedTrials,
    )
}

/** Safety state: screening answers, consents, and practice locks. */
public interface SafetyRepository {
    public fun observe(): Flow<SafetyState>

    public suspend fun current(): SafetyState

    public suspend fun recordPhotosensitivityScreening(passed: Boolean, at: Instant)

    public suspend fun setPhoticConsent(granted: Boolean, at: Instant)

    /** Locks a practice after an adverse event; only re-consent can clear it. */
    public suspend fun lockPractice(practice: Practice, reason: String, at: Instant)

    public suspend fun unlockPractice(practice: Practice, at: Instant)

    public suspend fun recordAdverseEvent(practice: Practice, event: AdverseEvent)
}

/**
 * Everything that gates a practice, in one place.
 *
 * Kept as a single value rather than scattered flags so that "may this run right now?"
 * is answerable in one expression, and so the answer can be tested exhaustively.
 */
public data class SafetyState(
    public val photosensitivityScreeningPassedAt: Instant? = null,
    public val photicConsentGrantedAt: Instant? = null,
    public val lockedPractices: Map<Practice, String> = emptyMap(),
    public val photicMinutesUsedToday: Int = 0,
) {
    public fun isLocked(practice: Practice): Boolean = practice in lockedPractices

    public fun lockReason(practice: Practice): String? = lockedPractices[practice]
}

/** User preferences that affect how practices behave, not merely how they look. */
public interface PreferencesRepository {
    public fun observe(): Flow<UserPreferences>

    public suspend fun current(): UserPreferences

    public suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

public data class UserPreferences(
    public val displayName: String? = null,
    public val theme: ThemeChoice = ThemeChoice.DAWN,
    /** Extra-large type for users who need more than the system maximum. */
    public val largeTypeBoost: Boolean = false,
    public val soundEnabled: Boolean = true,
    public val hapticsEnabled: Boolean = true,
    public val remindersEnabled: Boolean = true,
    public val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    public val hasCompletedOnboarding: Boolean = false,
) {
    public companion object {
        /** Mid-morning: after breakfast, before the day gets away. */
        public const val DEFAULT_REMINDER_HOUR: Int = 10
    }
}

public enum class ThemeChoice {
    DAWN,
    NIGHT,
    SYSTEM,
}
