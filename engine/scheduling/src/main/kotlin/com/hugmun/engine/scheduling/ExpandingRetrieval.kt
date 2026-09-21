/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.scheduling

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Within-session expanding retrieval — the Camp spaced-retrieval protocol.
 *
 * This is a different mechanism from [Fsrs] and operates on a different timescale. FSRS
 * schedules across days; expanding retrieval schedules across *seconds and minutes*
 * inside a single sitting, and it is the part with direct evidence in mild-to-moderate
 * cognitive impairment (Camp et al., 1996; see `docs/research/01-evidence-base.md` §8).
 *
 * The rule is simple and is deliberately not adaptive beyond it: on a success, the next
 * probe moves one rung up the ladder; on a failure, it drops one rung back. An item that
 * survives the top rung graduates to the across-days scheduler.
 *
 * Note that "failure" here is bounded by errorless learning: the user is never allowed to
 * produce a wrong answer. A failure means the answer had to be revealed before they
 * could produce it.
 */
public object ExpandingRetrieval {

    /**
     * The ladder, in seconds.
     *
     * Roughly doubling, which is the classic Camp spacing. The top rung at six minutes is
     * about as long as a single HugMun session can accommodate while still probing an
     * item more than once.
     */
    public val LADDER_SECONDS: List<Int> = listOf(20, 45, 90, 180, 360)

    /** Per-item position on the ladder within the current session. */
    public data class Rung(public val index: Int = 0, public val successesAtTop: Int = 0) {
        init {
            require(index >= 0) { "index must not be negative" }
        }

        /** True once the item has survived the longest interval and can leave the session. */
        public val hasGraduated: Boolean
            get() = index >= LADDER_SECONDS.size

        /** Delay until this item should be probed again, or null once it has graduated. */
        public val delay: Duration?
            get() = LADDER_SECONDS.getOrNull(index)?.seconds
    }

    /** Advances one rung. */
    public fun onSuccess(rung: Rung): Rung = Rung(
        index = rung.index + 1,
        successesAtTop = if (rung.index >= LADDER_SECONDS.lastIndex) rung.successesAtTop + 1 else rung.successesAtTop,
    )

    /**
     * Drops back one rung.
     *
     * Back one, never to zero: restarting from the beginning after a single slip is
     * demoralising and, per the spacing literature, unnecessary.
     */
    public fun onFailure(rung: Rung): Rung = Rung(
        index = (rung.index - 1).coerceAtLeast(0),
        successesAtTop = 0,
    )

    /**
     * Converts a graduated within-session item into a starting point for the across-days
     * scheduler.
     *
     * An item that cleared the whole ladder unaided starts as [Fsrs.Grade.GOOD]; one that
     * needed help along the way starts as [Fsrs.Grade.HARD], so its first cross-day
     * interval is shorter.
     */
    public fun graduationGrade(failuresDuringSession: Int): Fsrs.Grade = when {
        failuresDuringSession == 0 -> Fsrs.Grade.GOOD
        failuresDuringSession <= TOLERATED_FAILURES -> Fsrs.Grade.HARD
        else -> Fsrs.Grade.AGAIN
    }

    private const val TOLERATED_FAILURES = 2
}
