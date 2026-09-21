/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.model

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The practices HugMun offers.
 *
 * Deliberately a closed set. A cognitive-training app grows exercises the way a garden
 * grows weeds; each one here had to survive `docs/research/01-evidence-base.md`.
 */
public enum class Practice(
    public val id: String,
    /** The Russian name shown everywhere in the interface. */
    public val displayName: String,
    /** One line under the name; never a claim, always a description. */
    public val subtitle: String,
    public val tier: EvidenceTier,
) {
    VIGILANCE(
        id = "vigilance",
        displayName = "Зоркость",
        subtitle = "Скорость обработки и распределение внимания",
        tier = EvidenceTier.A,
    ),
    ANCHOR(
        id = "anchor",
        displayName = "Якорь",
        subtitle = "Важные имена, места и привычки — чтобы держались",
        tier = EvidenceTier.B,
    ),
    BREATHING(
        id = "breathing",
        displayName = "Дыхание",
        subtitle = "Медленное дыхание в вашем собственном ритме",
        tier = EvidenceTier.B,
    ),
    MOVEMENT(
        id = "movement",
        displayName = "Движение",
        subtitle = "Короткий комплекс: равновесие, сила, подвижность",
        tier = EvidenceTier.B,
    ),
    RHYTHM(
        id = "rhythm",
        displayName = "Ритм",
        subtitle = "Экспериментальная сенсорная стимуляция 40 Гц",
        tier = EvidenceTier.C,
    ),
    ASSESSMENT(
        id = "assessment",
        displayName = "Замер",
        subtitle = "Короткие пробы, чтобы видеть свою динамику",
        tier = EvidenceTier.A,
    ),
    ;

    public companion object {
        public fun fromId(id: String): Practice? = entries.firstOrNull { it.id == id }
    }
}

/** Why a practice appeared in today's plan. */
public enum class PlanReason {
    /** The protocol says it is due. */
    SCHEDULED,

    /** Due and overdue; the schedule has been re-anchored around the miss. */
    RESCHEDULED,

    /** The user asked for it outside the schedule. */
    SELF_INITIATED,
}

/** One entry in the day's plan. */
public data class PlannedPractice(
    public val practice: Practice,
    public val dueOn: LocalDate,
    public val estimated: Duration,
    public val reason: PlanReason,
    /** Plain-Russian explanation of why this is here today. Never empty. */
    public val rationale: String,
    public val isDue: Boolean,
) {
    init {
        require(rationale.isNotBlank()) { "a planned practice must be able to explain itself" }
    }
}

/** How a session ended. Distinguishing these matters for adherence analysis. */
public enum class SessionOutcome {
    COMPLETED,

    /** The user stopped early of their own accord. */
    STOPPED_BY_USER,

    /** The app stopped it: a safety rule, a device problem, or a quality failure. */
    STOPPED_BY_APP,

    /** Interrupted by something outside the app, e.g. a phone call. */
    INTERRUPTED,
}

/** Common envelope stored for every session of every practice. */
public data class SessionRecord(
    public val id: Long = 0L,
    public val practice: Practice,
    public val startedAt: Instant,
    public val endedAt: Instant,
    public val outcome: SessionOutcome,
    /** Free-form, practice-specific payload, serialised by the data layer. */
    public val detailJson: String,
    /** Set when the user reported feeling unwell. Drives the safety rules. */
    public val adverseEvent: AdverseEvent? = null,
) {
    public val duration: Duration get() = endedAt - startedAt
}

/**
 * A user-reported adverse event.
 *
 * Every practice screen carries a one-tap way to report one. Certain kinds disable their
 * module until the user re-consents — see `docs/research/03-safety-and-regulatory.md` §7.
 */
public data class AdverseEvent(
    public val kind: Kind,
    public val reportedAt: Instant,
    public val note: String? = null,
) {
    public enum class Kind {
        VISUAL_DISCOMFORT,
        HEADACHE_OR_AURA,
        DIZZINESS,
        NAUSEA,
        CHEST_PAIN_OR_BREATHLESSNESS,
        FALL,
        ANXIETY,
        OTHER,
        ;

        /** Whether reporting this must immediately lock the practice that produced it. */
        public val locksPractice: Boolean
            get() = this != ANXIETY && this != OTHER
    }
}
