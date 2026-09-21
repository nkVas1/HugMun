/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.data

import com.hugmun.core.common.AppDispatchers
import com.hugmun.core.database.dao.AnchorDao
import com.hugmun.core.database.entity.AnchorItemEntity
import com.hugmun.core.database.entity.AnchorReviewEntity
import com.hugmun.core.domain.AnchorRepository
import com.hugmun.core.model.AnchorItem
import com.hugmun.core.model.AnchorKind
import com.hugmun.core.model.AnchorReview
import com.hugmun.engine.scheduling.Fsrs
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

public class AnchorRepositoryImpl(private val dao: AnchorDao, private val dispatchers: AppDispatchers) :
    AnchorRepository {

    override fun observeItems(): Flow<List<AnchorItem>> =
        dao.observeItems().map { rows -> rows.mapNotNull { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeDueCount(on: LocalDate): Flow<Int> =
        dao.observeDueCount(on.toEpochDays()).flowOn(dispatchers.io)

    override suspend fun dueItems(on: LocalDate, limit: Int): List<AnchorItem> = withContext(dispatchers.io) {
        dao.dueItems(on.toEpochDays(), limit).mapNotNull { it.toDomain() }
    }

    override suspend fun item(id: Long): AnchorItem? = withContext(dispatchers.io) {
        dao.item(id)?.toDomain()
    }

    override suspend fun add(item: AnchorItem): Long = withContext(dispatchers.io) {
        dao.insert(item.toEntity())
    }

    override suspend fun archive(id: Long) {
        // Archived, never deleted. An item the user removes today may be one a family
        // member added and will want back, and there is no cost to keeping the row.
        withContext(dispatchers.io) { dao.archive(id) }
    }

    override suspend fun distractors(item: AnchorItem, count: Int): List<String> = withContext(dispatchers.io) {
        dao.distractorAnswers(item.kind.id, item.id, count)
    }

    override suspend fun recordReview(
        updated: AnchorItem,
        review: AnchorReview,
        grade: Fsrs.Grade,
        intervalDays: Long,
    ) {
        withContext(dispatchers.io) {
            dao.recordReview(
                item = updated.toEntity(),
                review = AnchorReviewEntity(
                    itemId = review.itemId,
                    reviewedAtEpochMillis = review.reviewedAt.toEpochMilliseconds(),
                    cueLevel = review.cueLevel,
                    recalledUnaided = review.recalledUnaided,
                    latencyMillis = review.latencyMillis,
                    grade = grade.value,
                    stabilityAfterDays = updated.stabilityDays ?: 0.0,
                    difficultyAfter = updated.difficulty ?: 0.0,
                    intervalDays = intervalDays,
                ),
            )
        }
    }
}

private fun AnchorItemEntity.toDomain(): AnchorItem? {
    val resolvedKind = AnchorKind.fromId(kind) ?: return null
    return AnchorItem(
        id = id,
        kind = resolvedKind,
        prompt = prompt,
        answer = answer,
        photoPath = photoPath,
        note = note,
        stabilityDays = stabilityDays,
        difficulty = difficulty,
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        cueLevel = cueLevel,
        dueOn = LocalDate.fromEpochDays(dueOnEpochDay),
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    )
}

private fun AnchorItem.toEntity(): AnchorItemEntity = AnchorItemEntity(
    id = id,
    kind = kind.id,
    prompt = prompt,
    answer = answer,
    photoPath = photoPath,
    note = note,
    stabilityDays = stabilityDays,
    difficulty = difficulty,
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    cueLevel = cueLevel,
    dueOnEpochDay = dueOn.toEpochDays(),
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
)
