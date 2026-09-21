/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.hugmun.core.database.entity.AnchorItemEntity
import com.hugmun.core.database.entity.AnchorReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface AnchorDao {

    @Query("SELECT * FROM anchor_item WHERE isArchived = 0 ORDER BY createdAtEpochMillis DESC")
    public fun observeItems(): Flow<List<AnchorItemEntity>>

    /**
     * Items due on or before [epochDay], oldest due first.
     *
     * Overdue items come first so a returning user practises what has waited longest,
     * which is also what is most at risk of being lost.
     */
    @Query(
        """
        SELECT * FROM anchor_item
        WHERE isArchived = 0 AND dueOnEpochDay <= :epochDay
        ORDER BY dueOnEpochDay ASC, createdAtEpochMillis ASC
        LIMIT :limit
        """,
    )
    public suspend fun dueItems(epochDay: Long, limit: Int): List<AnchorItemEntity>

    @Query("SELECT COUNT(*) FROM anchor_item WHERE isArchived = 0 AND dueOnEpochDay <= :epochDay")
    public fun observeDueCount(epochDay: Long): Flow<Int>

    @Query("SELECT * FROM anchor_item WHERE id = :id")
    public suspend fun item(id: Long): AnchorItemEntity?

    /**
     * Other answers, used as distractors in a multiple-choice prompt.
     *
     * Drawn from items of the *same* kind so that the choice is a genuine memory test
     * rather than a category guess — offering a street name among three first names
     * would let the user answer without remembering anything.
     */
    @Query(
        """
        SELECT answer FROM anchor_item
        WHERE isArchived = 0 AND kind = :kind AND id != :excludeId
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    public suspend fun distractorAnswers(kind: String, excludeId: Long, limit: Int): List<String>

    @Insert
    public suspend fun insert(item: AnchorItemEntity): Long

    @Update
    public suspend fun update(item: AnchorItemEntity)

    @Query("UPDATE anchor_item SET isArchived = 1 WHERE id = :id")
    public suspend fun archive(id: Long)

    @Insert
    public suspend fun insertReview(review: AnchorReviewEntity)

    /** An item's new state and the review that produced it are written together. */
    @Transaction
    public suspend fun recordReview(item: AnchorItemEntity, review: AnchorReviewEntity) {
        update(item)
        insertReview(review)
    }

    @Query("SELECT * FROM anchor_review WHERE itemId = :itemId ORDER BY reviewedAtEpochMillis")
    public suspend fun reviewsFor(itemId: Long): List<AnchorReviewEntity>
}
