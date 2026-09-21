/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * «Якорь» — the things the user actually wants to keep hold of.
 *
 * This is memory *rehabilitation*, not cognitive training: the goal is that a specific
 * grandchild's name stays available, not that "memory" improves in the abstract. It is
 * the one practice whose value does not depend on any transfer effect ever occurring,
 * and it is where Munin — the raven Odin feared losing — actually lives in the product.
 */
public enum class AnchorKind(
    public val id: String,
    public val displayName: String,
    /** How the prompt reads, so the add-item screen can show an example. */
    public val example: String,
) {
    PERSON(
        id = "person",
        displayName = "Человек",
        example = "Старший внук, живёт в Казани — Митя",
    ),
    PLACE(
        id = "place",
        displayName = "Где лежит",
        example = "Запасные ключи — в синей вазе в прихожей",
    ),
    ROUTINE(
        id = "routine",
        displayName = "Распорядок",
        example = "Таблетка от давления — после завтрака",
    ),
    FACT(
        id = "fact",
        displayName = "Важное",
        example = "Телефон дочери — 8 916 000 00 00",
    ),
    ;

    public companion object {
        public fun fromId(id: String): AnchorKind? = entries.firstOrNull { it.id == id }
    }
}

/** One stored item, with its memory state. */
public data class AnchorItem(
    public val id: Long = 0L,
    public val kind: AnchorKind,
    public val prompt: String,
    public val answer: String,
    public val photoPath: String? = null,
    public val note: String? = null,
    public val stabilityDays: Double? = null,
    public val difficulty: Double? = null,
    public val reviewCount: Int = 0,
    public val lapseCount: Int = 0,
    public val cueLevel: Int = CueLevel.ANSWER_SHOWN,
    public val dueOn: LocalDate,
    public val createdAt: Instant,
) {
    public val isNew: Boolean get() = reviewCount == 0
}

/**
 * How much help the prompt gives.
 *
 * Errorless learning means the incorrect answer is never produced, so an item starts
 * with the answer visible and the cue is withdrawn only after repeated unaided success.
 * Going straight to an unaided test — the obvious design — is precisely the thing the
 * evidence says not to do.
 */
public object CueLevel {
    /** The correct answer is highlighted among the options. */
    public const val ANSWER_SHOWN: Int = 0

    /** The first letter is given. */
    public const val FIRST_LETTER: Int = 1

    /** The number of letters is given. */
    public const val LETTER_COUNT: Int = 2

    /** No cue. */
    public const val NONE: Int = 3

    public const val HIGHEST: Int = NONE

    /** Two consecutive unaided successes are required before a cue is withdrawn. */
    public const val SUCCESSES_TO_ADVANCE: Int = 2

    public fun describe(level: Int): String = when (level) {
        ANSWER_SHOWN -> "Ответ подсказан"
        FIRST_LETTER -> "Подсказана первая буква"
        LETTER_COUNT -> "Подсказано число букв"
        else -> "Без подсказки"
    }

    /** The hint text shown at a given level, or null when there is none. */
    public fun hintFor(answer: String, level: Int): String? = when (level) {
        FIRST_LETTER -> "Начинается на «${answer.firstOrNull()?.uppercase() ?: ""}»"
        LETTER_COUNT -> "${answer.trim().length} букв"
        else -> null
    }
}

/** One prompt as presented during a review. */
public data class AnchorPrompt(
    public val item: AnchorItem,
    /** Options in display order, one of which is [AnchorItem.answer]. */
    public val options: List<String>,
    public val cueLevel: Int,
) {
    public val hint: String? get() = CueLevel.hintFor(item.answer, cueLevel)

    /** True when the answer is highlighted, i.e. the user cannot get it wrong. */
    public val isFullyCued: Boolean get() = cueLevel == CueLevel.ANSWER_SHOWN
}

/** What happened in one review. */
public data class AnchorReview(
    public val itemId: Long,
    public val reviewedAt: Instant,
    public val cueLevel: Int,
    public val recalledUnaided: Boolean,
    public val latencyMillis: Long,
)
