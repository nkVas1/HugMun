/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.domain

import com.hugmun.core.common.TimeSource
import com.hugmun.core.model.AnchorItem
import com.hugmun.core.model.AnchorKind
import com.hugmun.core.model.AnchorPrompt
import com.hugmun.core.model.CueLevel
import com.hugmun.engine.scheduling.Fsrs
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

public interface AnchorRepository {
    public fun observeItems(): Flow<List<AnchorItem>>

    public fun observeDueCount(on: LocalDate): Flow<Int>

    public suspend fun dueItems(on: LocalDate, limit: Int = DEFAULT_SESSION_SIZE): List<AnchorItem>

    public suspend fun item(id: Long): AnchorItem?

    public suspend fun add(item: AnchorItem): Long

    public suspend fun archive(id: Long)

    /** Other answers of the same kind, for multiple-choice distractors. */
    public suspend fun distractors(item: AnchorItem, count: Int): List<String>

    /** Persists the new memory state and the review that produced it. */
    public suspend fun recordReview(
        updated: AnchorItem,
        review: com.hugmun.core.model.AnchorReview,
        grade: Fsrs.Grade,
        intervalDays: Long,
    )

    public companion object {
        /**
         * Items per session.
         *
         * Small on purpose. A long review queue is how spaced repetition turns into a
         * chore, and this user has a fifteen-minute daily budget that «Зоркость» already
         * claims most of.
         */
        public const val DEFAULT_SESSION_SIZE: Int = 8
    }
}

/**
 * Applies one review: derives the grade, advances the memory state, decides the cue.
 *
 * Kept separate from the repository so it is testable without a database, and separate
 * from the ViewModel so the rules can be read in one place. Every clamp here is
 * deliberate and documented in `docs/research/02-intervention-specs.md` §4.3.
 */
public class ReviewAnchorItemUseCase(
    private val repository: AnchorRepository,
    private val timeSource: TimeSource,
    private val policy: Fsrs.Policy = Fsrs.Policy(),
) {
    public suspend operator fun invoke(item: AnchorItem, recalledUnaided: Boolean, latencyMillis: Long): AnchorItem {
        val now = timeSource.now()
        val today = timeSource.today()

        val grade = Fsrs.gradeFor(
            recalledUnaided = recalledUnaided,
            cueLevel = item.cueLevel,
            latencyMillis = latencyMillis,
        )

        val state = advanceMemory(item, grade, today)
        val interval = Fsrs.intervalDays(state.stabilityDays, policy)
        val updated = item.copy(
            stabilityDays = state.stabilityDays,
            difficulty = state.difficulty,
            reviewCount = state.reviewCount,
            lapseCount = state.lapseCount,
            cueLevel = nextCueLevel(item.cueLevel, recalledUnaided),
            dueOn = today.plus(interval.toInt(), DateTimeUnit.DAY),
        )

        repository.recordReview(
            updated = updated,
            review = com.hugmun.core.model.AnchorReview(
                itemId = item.id,
                reviewedAt = now,
                cueLevel = item.cueLevel,
                recalledUnaided = recalledUnaided,
                latencyMillis = latencyMillis,
            ),
            grade = grade,
            intervalDays = interval,
        )

        return updated
    }

    /**
     * Advances the memory state, or creates it on the first ever review.
     *
     * Elapsed time is measured from the *due* date rather than from the last review,
     * because FSRS's retrievability term is about how long the memory has been decaying
     * relative to its scheduled interval.
     */
    private fun advanceMemory(item: AnchorItem, grade: Fsrs.Grade, today: LocalDate): Fsrs.MemoryState {
        val stability = item.stabilityDays
        val difficulty = item.difficulty
        if (stability == null || difficulty == null) return Fsrs.initialState(grade)

        val elapsedDays = (today.toEpochDays() - item.dueOn.toEpochDays())
            .toDouble()
            .coerceAtLeast(0.0)

        return Fsrs.nextState(
            current = Fsrs.MemoryState(
                stabilityDays = stability,
                difficulty = difficulty,
                reviewCount = item.reviewCount,
                lapseCount = item.lapseCount,
            ),
            grade = grade,
            elapsedDays = elapsedDays,
        )
    }

    /**
     * Cue withdrawal.
     *
     * Forward one level on an unaided success, back one on a failure. Never further
     * than one step in either direction: jumping straight to no-cue after a good day
     * invites an error, and dropping straight to fully-cued after one slip is
     * demoralising and, per the spacing literature, unnecessary.
     */
    private fun nextCueLevel(current: Int, recalledUnaided: Boolean): Int = when {
        recalledUnaided -> (current + 1).coerceAtMost(CueLevel.HIGHEST)
        else -> (current - 1).coerceAtLeast(CueLevel.ANSWER_SHOWN)
    }
}

/**
 * Builds the multiple-choice prompt for an item.
 *
 * Errorless learning is the constraint: at the lowest cue level the correct option is
 * marked, so a wrong answer cannot be produced at all. As the cue is withdrawn, the
 * marking goes and then the hints go, in that order.
 */
public class BuildAnchorPromptUseCase(
    private val repository: AnchorRepository,
    private val random: Random = Random.Default,
) {
    public suspend operator fun invoke(item: AnchorItem): AnchorPrompt {
        val distractors = repository.distractors(item, OPTION_COUNT - 1)
        val options = (distractors + item.answer).distinct().shuffled(random)

        return AnchorPrompt(
            item = item,
            // With too few stored items of the same kind there may be fewer than four
            // options. That is fine and is better than padding with nonsense: an
            // obviously absurd distractor makes the choice easier, not harder.
            options = options,
            cueLevel = item.cueLevel,
        )
    }

    private companion object {
        const val OPTION_COUNT = 4
    }
}

/** Distinct kinds that currently have items, for the add-item screen's suggestions. */
public fun List<AnchorItem>.kindsInUse(): Set<AnchorKind> = map { it.kind }.toSet()
