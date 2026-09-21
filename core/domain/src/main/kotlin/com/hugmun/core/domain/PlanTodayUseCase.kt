/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.domain

import com.hugmun.core.common.TimeSource
import com.hugmun.core.model.PlanReason
import com.hugmun.core.model.PlannedPractice
import com.hugmun.core.model.Practice
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlin.time.Duration.Companion.minutes

/**
 * Builds the day's plan.
 *
 * Three rules, all of them product decisions rather than technical ones:
 *
 * 1. **A missed day never becomes a backlog.** The protocol re-anchors on the last
 *    completed session, so someone who disappears for three weeks is handed one session,
 *    not fifteen. Guilt is not a scheduling primitive.
 * 2. **Every entry explains itself.** `rationale` is non-blank by construction. The user
 *    can always see why the app is asking for something today.
 * 3. **An empty plan is a valid, stated outcome.** Rest days are part of the protocol,
 *    and the app says so rather than inventing filler to keep the screen busy.
 */
public class PlanTodayUseCase(
    private val protocolRepository: ProtocolRepository,
    private val safetyRepository: SafetyRepository,
    private val timeSource: TimeSource,
) {
    public suspend operator fun invoke(): DayPlan {
        val today = timeSource.today()
        val safety = safetyRepository.current()
        val state = protocolRepository.currentState()

        val vigilance = state?.let { planVigilance(it, today) }

        val entries = listOfNotNull(vigilance)
            .filterNot { safety.isLocked(it.practice) }

        return DayPlan(
            date = today,
            entries = entries,
            isEnrolled = state != null,
            hasCompletedABoosterBlock = state?.let(TrainingProtocol::hasCompletedABoosterBlock) ?: false,
        )
    }

    private fun planVigilance(state: TrainingProtocol.State, today: kotlinx.datetime.LocalDate): PlannedPractice {
        val plan = TrainingProtocol.plan(state, today)
        val overdue = plan.isDue && state.lastSessionOn != null && plan.dueOn < today

        return PlannedPractice(
            practice = Practice.VIGILANCE,
            dueOn = plan.dueOn,
            estimated = VIGILANCE_MINUTES.minutes,
            reason = if (overdue) PlanReason.RESCHEDULED else PlanReason.SCHEDULED,
            rationale = plan.rationale,
            isDue = plan.isDue,
        )
    }

    private companion object {
        /** 12 minutes of trials plus instructions and the result screen. */
        const val VIGILANCE_MINUTES = 15
    }
}

/** What the home screen renders. */
public data class DayPlan(
    public val date: kotlinx.datetime.LocalDate,
    public val entries: List<PlannedPractice>,
    public val isEnrolled: Boolean,
    /**
     * True once the user has completed a full booster block.
     *
     * This drives one of the very few congratulatory messages in the app, because it is
     * one of the few we can honestly justify: in ACTIVE it was completing a booster block
     * that separated HR 0.75 from HR 1.01.
     */
    public val hasCompletedABoosterBlock: Boolean,
) {
    public val due: List<PlannedPractice> get() = entries.filter { it.isDue }

    public val hasAnythingDue: Boolean get() = due.isNotEmpty()
}
