/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.scheduling

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The speed-of-processing training schedule, derived from the ACTIVE trial.
 *
 * **Why this file matters more than it looks like it should.**
 *
 * In the 20-year analysis of ACTIVE, speed-of-processing training *with* at least one
 * booster block was associated with a hazard ratio of 0.75 for diagnosed dementia; the
 * same training *without* boosters gave 1.01 — no effect at all (Coe et al., 2026). The
 * schedule is therefore not a convenience feature wrapped around the exercise. As far as
 * the best available evidence goes, the schedule **is** the intervention, and an app that
 * lets someone train hard for six weeks and then drift away has faithfully reproduced the
 * arm that did nothing.
 *
 * The protocol is declared as data rather than expressed in control flow so that it can
 * be revised when better evidence arrives, inspected in a test, and shown to the user.
 * Nothing here is a black box: every planned session carries the reason it was planned.
 */
public object TrainingProtocol {

    public enum class Phase {
        /** The 10-session core block. ACTIVE: 10 sessions over 5–6 weeks. */
        CORE,

        /** Weekly sessions immediately after the core block. HugMun extrapolation. */
        MAINTENANCE_WEEKLY,

        /** Monthly sessions until the first booster window. HugMun extrapolation. */
        MAINTENANCE_MONTHLY,

        /** ACTIVE booster 1: 4 sessions at ~11 months. */
        BOOSTER_ONE,

        /** Monthly sessions until the second booster window. HugMun extrapolation. */
        MAINTENANCE_MONTHLY_TWO,

        /** ACTIVE booster 2: 4 sessions at ~35 months. */
        BOOSTER_TWO,

        /** Monthly sessions, indefinitely. HugMun extrapolation. */
        MAINTENANCE_ONGOING,
    }

    /** Whether a phase's parameters come from the trial or from our own reasoning. */
    public enum class Provenance {
        /** Taken directly from the ACTIVE protocol. */
        TRIAL,

        /** Our extrapolation, labelled as such wherever it is shown. */
        EXTRAPOLATION,
    }

    /** How a phase ends. */
    public sealed interface PhaseLength {
        /** After a fixed number of completed sessions. */
        public data class Sessions(public val count: Int) : PhaseLength

        /** When this many weeks have elapsed since enrolment. */
        public data class UntilWeek(public val week: Int) : PhaseLength

        /** Never. */
        public data object Indefinite : PhaseLength
    }

    public data class PhaseSpec(
        public val phase: Phase,
        public val length: PhaseLength,
        public val spacingDays: Int,
        public val provenance: Provenance,
        /** Shown to the user, in Russian, as the reason today's session exists. */
        public val rationale: String,
    )

    /**
     * The protocol, in order.
     *
     * Deviations from ACTIVE and the reasoning behind them are recorded in
     * `docs/research/02-intervention-specs.md` §1.5. The largest is session length:
     * ACTIVE sessions ran 60–75 minutes including group instruction, while HugMun runs
     * 12–15 minutes of pure adaptive trials. That is a deliberate, documented deviation,
     * not an oversight.
     */
    public val PHASES: List<PhaseSpec> = listOf(
        PhaseSpec(
            phase = Phase.CORE,
            length = PhaseLength.Sessions(CORE_SESSIONS),
            spacingDays = 3,
            provenance = Provenance.TRIAL,
            rationale = "Основной курс: десять занятий примерно за пять недель.",
        ),
        PhaseSpec(
            phase = Phase.MAINTENANCE_WEEKLY,
            length = PhaseLength.Sessions(WEEKLY_MAINTENANCE_SESSIONS),
            spacingDays = 7,
            provenance = Provenance.EXTRAPOLATION,
            rationale = "Закрепление: раз в неделю, чтобы навык не рассыпался сразу после курса.",
        ),
        PhaseSpec(
            phase = Phase.MAINTENANCE_MONTHLY,
            length = PhaseLength.UntilWeek(BOOSTER_ONE_WEEK),
            spacingDays = 30,
            provenance = Provenance.EXTRAPOLATION,
            rationale = "Поддержание: раз в месяц до повторного курса.",
        ),
        PhaseSpec(
            phase = Phase.BOOSTER_ONE,
            length = PhaseLength.Sessions(BOOSTER_SESSIONS),
            spacingDays = 10,
            provenance = Provenance.TRIAL,
            rationale = "Повторный курс — примерно через год. В исследовании именно он дал эффект.",
        ),
        PhaseSpec(
            phase = Phase.MAINTENANCE_MONTHLY_TWO,
            length = PhaseLength.UntilWeek(BOOSTER_TWO_WEEK),
            spacingDays = 30,
            provenance = Provenance.EXTRAPOLATION,
            rationale = "Поддержание: раз в месяц до следующего повторного курса.",
        ),
        PhaseSpec(
            phase = Phase.BOOSTER_TWO,
            length = PhaseLength.Sessions(BOOSTER_SESSIONS),
            spacingDays = 10,
            provenance = Provenance.TRIAL,
            rationale = "Второй повторный курс — примерно через три года.",
        ),
        PhaseSpec(
            phase = Phase.MAINTENANCE_ONGOING,
            length = PhaseLength.Indefinite,
            spacingDays = 30,
            provenance = Provenance.EXTRAPOLATION,
            rationale = "Поддержание: раз в месяц.",
        ),
    )

    /**
     * Persisted progress through the protocol.
     *
     * The phase is stored rather than re-derived from a session count, because phases
     * that end on elapsed time cannot be recovered from counts alone, and because a
     * schedule that silently re-interprets its own history is a schedule nobody can debug.
     */
    public data class State(
        public val enrolledOn: LocalDate,
        public val phase: Phase = Phase.CORE,
        public val sessionsCompletedInPhase: Int = 0,
        public val totalSessionsCompleted: Int = 0,
        public val lastSessionOn: LocalDate? = null,
    )

    /** What the user should do next, and why. */
    public data class Plan(
        public val phase: Phase,
        public val dueOn: LocalDate,
        public val sessionNumberInPhase: Int,
        public val sessionsRemainingInPhase: Int?,
        public val provenance: Provenance,
        public val rationale: String,
        /** True when [dueOn] is today or earlier. */
        public val isDue: Boolean,
    )

    public fun specFor(phase: Phase): PhaseSpec = PHASES.first { it.phase == phase }

    /**
     * Plans the next session.
     *
     * The due date is anchored on the **last completed session**, not on an idealised
     * calendar. Someone who misses three weeks is not handed three overdue sessions; they
     * are handed the next one. Guilt is not a scheduling primitive.
     */
    public fun plan(state: State, today: LocalDate): Plan {
        val resolved = advancePhaseIfElapsed(state, today)
        val spec = specFor(resolved.phase)

        val earliest = when (val last = resolved.lastSessionOn) {
            null -> resolved.enrolledOn
            else -> last.plus(spec.spacingDays, DateTimeUnit.DAY)
        }

        val windowStart = phaseWindowStart(resolved.phase, resolved.enrolledOn)
        val dueOn = maxOf(earliest, windowStart)

        val remaining = when (val length = spec.length) {
            is PhaseLength.Sessions -> (length.count - resolved.sessionsCompletedInPhase).coerceAtLeast(0)
            else -> null
        }

        return Plan(
            phase = resolved.phase,
            dueOn = dueOn,
            sessionNumberInPhase = resolved.sessionsCompletedInPhase + 1,
            sessionsRemainingInPhase = remaining,
            provenance = spec.provenance,
            rationale = spec.rationale,
            isDue = dueOn <= today,
        )
    }

    /** Records a completed session and advances the protocol. */
    public fun afterSession(state: State, completedOn: LocalDate): State {
        val resolved = advancePhaseIfElapsed(state, completedOn)
        val advanced = resolved.copy(
            sessionsCompletedInPhase = resolved.sessionsCompletedInPhase + 1,
            totalSessionsCompleted = resolved.totalSessionsCompleted + 1,
            lastSessionOn = completedOn,
        )
        return advancePhaseIfSessionsExhausted(advanced, completedOn)
    }

    /**
     * Whether the user has completed the block the ACTIVE booster result depends on.
     *
     * This drives one of the few genuinely encouraging messages in the app, because it is
     * one of the few we can honestly justify.
     */
    public fun hasCompletedABoosterBlock(state: State): Boolean = state.phase.ordinal > Phase.BOOSTER_ONE.ordinal ||
        (state.phase == Phase.BOOSTER_ONE && state.sessionsCompletedInPhase >= BOOSTER_SESSIONS)

    // --- Phase transitions ---------------------------------------------------------

    private fun advancePhaseIfSessionsExhausted(state: State, on: LocalDate): State {
        val spec = specFor(state.phase)
        val length = spec.length
        if (length !is PhaseLength.Sessions) return advancePhaseIfElapsed(state, on)
        if (state.sessionsCompletedInPhase < length.count) return state

        val next = nextPhase(state.phase) ?: return state
        return advancePhaseIfElapsed(
            state.copy(phase = next, sessionsCompletedInPhase = 0),
            on,
        )
    }

    private tailrec fun advancePhaseIfElapsed(state: State, today: LocalDate): State {
        val spec = specFor(state.phase)
        val length = spec.length
        if (length !is PhaseLength.UntilWeek) return state
        if (weeksSince(state.enrolledOn, today) < length.week) return state

        val next = nextPhase(state.phase) ?: return state
        return advancePhaseIfElapsed(
            state.copy(phase = next, sessionsCompletedInPhase = 0),
            today,
        )
    }

    private fun nextPhase(phase: Phase): Phase? {
        val index = PHASES.indexOfFirst { it.phase == phase }
        return PHASES.getOrNull(index + 1)?.phase
    }

    private fun phaseWindowStart(phase: Phase, enrolledOn: LocalDate): LocalDate = when (phase) {
        Phase.BOOSTER_ONE -> enrolledOn.plus(BOOSTER_ONE_WEEK * DAYS_PER_WEEK, DateTimeUnit.DAY)
        Phase.BOOSTER_TWO -> enrolledOn.plus(BOOSTER_TWO_WEEK * DAYS_PER_WEEK, DateTimeUnit.DAY)
        else -> enrolledOn
    }

    private fun weeksSince(from: LocalDate, to: LocalDate): Int {
        val days = to.toEpochDays() - from.toEpochDays()
        return (days / DAYS_PER_WEEK).toInt()
    }

    // --- Constants -----------------------------------------------------------------

    /** ACTIVE: 10 training sessions in the core block. */
    public const val CORE_SESSIONS: Int = 10

    /** ACTIVE: 4 sessions per booster block. */
    public const val BOOSTER_SESSIONS: Int = 4

    /** ACTIVE booster 1 fell at ~11 months. */
    public const val BOOSTER_ONE_WEEK: Int = 46

    /** ACTIVE booster 2 fell at ~35 months. */
    public const val BOOSTER_TWO_WEEK: Int = 148

    private const val WEEKLY_MAINTENANCE_SESSIONS = 8
    private const val DAYS_PER_WEEK = 7
}
