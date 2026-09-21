/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.data

import com.hugmun.core.common.AppDispatchers
import com.hugmun.core.database.dao.ProtocolDao
import com.hugmun.core.domain.ProtocolRepository
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

public class ProtocolRepositoryImpl(private val dao: ProtocolDao, private val dispatchers: AppDispatchers) :
    ProtocolRepository {

    override fun observeState(): Flow<TrainingProtocol.State?> =
        dao.observe().map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun currentState(): TrainingProtocol.State? = withContext(dispatchers.io) {
        dao.get()?.toDomain()
    }

    override suspend fun enrol(on: LocalDate): TrainingProtocol.State = withContext(dispatchers.io) {
        // Enrolment is idempotent. Re-running it must never reset a protocol that is
        // three years into its second booster block.
        val existing = dao.get()?.toDomain()
        if (existing != null) return@withContext existing

        val state = TrainingProtocol.State(enrolledOn = on)
        dao.upsert(state.toEntity())
        state
    }

    override suspend fun recordCompletedSession(on: LocalDate): TrainingProtocol.State = withContext(dispatchers.io) {
        val current = dao.get()?.toDomain() ?: TrainingProtocol.State(enrolledOn = on)
        val advanced = TrainingProtocol.afterSession(current, on)
        dao.upsert(advanced.toEntity())
        advanced
    }
}
