/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One thing the user wants to keep hold of.
 *
 * The memory state (stability, difficulty) is stored alongside the content rather than
 * in a separate table: it belongs to the item, it is written on every review, and a
 * join on every scheduling query would buy nothing.
 */
@Entity(
    tableName = "anchor_item",
    indices = [Index("dueOnEpochDay"), Index("kind")],
)
public data class AnchorItemEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0L,

    /** What sort of thing this is: a name, a place, a routine, a fact. */
    public val kind: String,

    /** The question, as the user will be asked it. */
    public val prompt: String,

    /** The answer, as the user must recall it. */
    public val answer: String,

    /**
     * A photograph, if the item has one.
     *
     * Stored as a path inside the app's own private storage. Photographs of a person's
     * family never leave the device and are never written anywhere a backup could reach
     * — see the data-extraction rules in the manifest.
     */
    public val photoPath: String? = null,

    /** Optional note shown after the answer, e.g. "младший сын Ани". */
    public val note: String? = null,

    // --- Memory state (FSRS) -------------------------------------------------------

    public val stabilityDays: Double? = null,
    public val difficulty: Double? = null,
    public val reviewCount: Int = 0,
    public val lapseCount: Int = 0,

    /**
     * Current cue level, 0 (answer shown) to 3 (no cue).
     *
     * Errorless learning means the item starts fully cued and the cue is withdrawn only
     * after repeated unaided success. See docs/research/02-intervention-specs.md §4.2.
     */
    public val cueLevel: Int = 0,

    public val dueOnEpochDay: Long,
    public val createdAtEpochMillis: Long,
    public val isArchived: Boolean = false,
)

/**
 * One review of one item.
 *
 * Kept individually, like the psychophysics trials, so the schedule can be re-derived
 * offline and so a clinician can see what was actually practised rather than only the
 * app's summary.
 */
@Entity(
    tableName = "anchor_review",
    indices = [Index("itemId"), Index("reviewedAtEpochMillis")],
)
public data class AnchorReviewEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0L,
    public val itemId: Long,
    public val reviewedAtEpochMillis: Long,
    public val cueLevel: Int,
    public val recalledUnaided: Boolean,
    public val latencyMillis: Long,
    /** The FSRS grade that was derived from the above, stored so it can be checked. */
    public val grade: Int,
    public val stabilityAfterDays: Double,
    public val difficultyAfter: Double,
    public val intervalDays: Long,
)
