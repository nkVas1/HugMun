/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Progress through the ACTIVE-derived training protocol.
 *
 * A single row. The phase is stored rather than re-derived from a session count, because
 * phases that end on elapsed time cannot be recovered from counts alone, and because a
 * schedule that silently re-interprets its own history is one nobody can debug.
 */
@Entity(tableName = "protocol_state")
public data class ProtocolStateEntity(
    @PrimaryKey public val id: Int = SINGLETON_ID,
    /** Days since the Unix epoch. */
    public val enrolledOnEpochDay: Long,
    public val phase: String,
    public val sessionsCompletedInPhase: Int,
    public val totalSessionsCompleted: Int,
    public val lastSessionOnEpochDay: Long?,
) {
    public companion object {
        public const val SINGLETON_ID: Int = 1
    }
}

/**
 * One «Зоркость» session.
 *
 * The threshold is nullable on purpose: a session that was stopped early, or that never
 * collected enough reversals, is still a real event worth recording — it just has no
 * number attached. Storing a fabricated value would corrupt the trend.
 */
@Entity(
    tableName = "vigilance_session",
    indices = [Index("startedAtEpochMillis")],
)
public data class VigilanceSessionEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0L,
    public val startedAtEpochMillis: Long,
    public val endedAtEpochMillis: Long,
    public val outcome: String,
    public val level: Int,
    public val refreshHz: Double,
    public val thresholdMillis: Double?,
    public val thresholdFrames: Double?,
    public val isFloorLimited: Boolean,
    public val isQualityAcceptable: Boolean,
    public val reversalsUsed: Int,
    public val scoredTrials: Int,
    public val correctTrials: Int,
    public val discardedTrials: Int,
    public val stopReason: String,
    public val adverseEventKind: String?,
    public val adverseEventNote: String?,
)

/**
 * One trial, stored individually.
 *
 * This is what makes the export useful to a clinician and lets a threshold be
 * re-derived offline if the estimator ever changes. Summary-only storage would make the
 * app's own numbers unfalsifiable.
 */
@Entity(
    tableName = "vigilance_trial",
    indices = [Index("sessionId")],
)
public data class VigilanceTrialEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0L,
    public val sessionId: Long,
    public val trialIndex: Int,
    public val centralFigure: String,
    public val targetDirection: Int?,
    public val targetEccentricity: String?,
    public val distractorCount: Int,
    public val stimulusFrames: Int,
    public val presentedMillis: Double,
    public val fixationMillis: Int,
    public val maskMillis: Int,
    public val responseFigure: String?,
    public val responseDirection: Int?,
    public val presentationAccurate: Boolean,
    public val latencyMillis: Long,
    public val outcome: String,
    public val isReversal: Boolean,
)

/** Screening answers, consents and practice locks. */
@Entity(tableName = "safety_state")
public data class SafetyStateEntity(
    @PrimaryKey public val id: Int = SINGLETON_ID,
    public val photosensitivityScreeningPassedAtEpochMillis: Long?,
    public val photicConsentGrantedAtEpochMillis: Long?,
    public val photicMinutesUsedToday: Int,
    public val photicUsageDayEpochDay: Long?,
) {
    public companion object {
        public const val SINGLETON_ID: Int = 1
    }
}

/**
 * A practice locked after an adverse event.
 *
 * Presence of a row is the lock. Only explicit re-consent removes it, which is why this
 * is a table rather than a boolean somewhere.
 */
@Entity(tableName = "practice_lock")
public data class PracticeLockEntity(
    @PrimaryKey public val practiceId: String,
    public val reason: String,
    public val lockedAtEpochMillis: Long,
)

/** An adverse event, kept permanently and included in every export. */
@Entity(
    tableName = "adverse_event",
    indices = [Index("reportedAtEpochMillis")],
)
public data class AdverseEventEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0L,
    public val practiceId: String,
    public val kind: String,
    public val reportedAtEpochMillis: Long,
    public val note: String?,
)
