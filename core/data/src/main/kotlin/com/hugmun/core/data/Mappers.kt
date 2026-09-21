/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.data

import com.hugmun.core.database.entity.ProtocolStateEntity
import com.hugmun.core.database.entity.VigilanceSessionEntity
import com.hugmun.core.database.entity.VigilanceTrialEntity
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.core.model.SessionOutcome
import com.hugmun.engine.psychophysics.CompletedTrial
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Entity ↔ domain mapping.
 *
 * Kept in one file and written by hand. The boundary is small, and a mapping library
 * would hide exactly the kind of silent field mismatch that would corrupt stored
 * measurements without failing a build.
 */

internal fun ProtocolStateEntity.toDomain(): TrainingProtocol.State = TrainingProtocol.State(
    enrolledOn = LocalDate.fromEpochDays(enrolledOnEpochDay),
    phase = TrainingProtocol.Phase.valueOf(phase),
    sessionsCompletedInPhase = sessionsCompletedInPhase,
    totalSessionsCompleted = totalSessionsCompleted,
    lastSessionOn = lastSessionOnEpochDay?.let(LocalDate::fromEpochDays),
)

internal fun TrainingProtocol.State.toEntity(): ProtocolStateEntity = ProtocolStateEntity(
    enrolledOnEpochDay = enrolledOn.toEpochDays(),
    phase = phase.name,
    sessionsCompletedInPhase = sessionsCompletedInPhase,
    totalSessionsCompleted = totalSessionsCompleted,
    lastSessionOnEpochDay = lastSessionOn?.toEpochDays(),
)

internal fun VigilanceSessionEntity.toDomain(): StoredVigilanceSession = StoredVigilanceSession(
    id = id,
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMillis),
    endedAt = Instant.fromEpochMilliseconds(endedAtEpochMillis),
    outcome = SessionOutcome.valueOf(outcome),
    thresholdMillis = thresholdMillis,
    refreshHz = refreshHz,
    isFloorLimited = isFloorLimited,
    isQualityAcceptable = isQualityAcceptable,
    scoredTrials = scoredTrials,
    correctTrials = correctTrials,
    discardedTrials = discardedTrials,
    reversalsUsed = reversalsUsed,
    accuracy = if (scoredTrials == 0) 0.0 else correctTrials.toDouble() / scoredTrials,
)

internal fun CompletedTrial.toEntity(sessionId: Long): VigilanceTrialEntity = VigilanceTrialEntity(
    sessionId = sessionId,
    trialIndex = spec.index,
    centralFigure = spec.centralFigure.name,
    targetDirection = spec.target?.direction?.index,
    targetEccentricity = spec.target?.eccentricity?.name,
    distractorCount = spec.distractors.size,
    stimulusFrames = spec.stimulusFrames,
    presentedMillis = presentedMillis,
    fixationMillis = spec.fixationMillis,
    maskMillis = spec.maskMillis,
    responseFigure = response.figure?.name,
    responseDirection = response.direction?.index,
    presentationAccurate = response.presentationAccurate,
    latencyMillis = response.latencyMillis,
    outcome = outcome.name,
    isReversal = isReversal,
)
