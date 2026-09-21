/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hugmun.core.domain.ProtocolRepository
import com.hugmun.core.domain.ReliableChange
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.core.domain.VigilanceRepository
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The trend view.
 *
 * Its job is mostly refusal. Most of the time the honest answer is "this is within your
 * usual variation", and the screen has to be willing to say that instead of drawing a
 * dramatic slope out of noise.
 */
public class InsightsViewModel(vigilanceRepository: VigilanceRepository, protocolRepository: ProtocolRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    public val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                vigilanceRepository.observeResults(limit = HISTORY_LIMIT),
                protocolRepository.observeState(),
            ) { sessions, protocol -> sessions to protocol }
                .collect { (sessions, protocol) ->
                    _state.value = build(sessions, protocol)
                }
        }
    }

    private fun build(sessions: List<StoredVigilanceSession>, protocol: TrainingProtocol.State?): InsightsUiState {
        // The repository returns newest first; a trend reads oldest first.
        val ordered = sessions.reversed()
        val measured = ordered.filter { it.thresholdMillis != null }

        // Sessions the app does not trust are shown, but never used to compute the
        // baseline or the judgement. Hiding them would be worse: the gap would be
        // unexplained.
        val trustworthy = measured.filter { it.isQualityAcceptable }
        val history = trustworthy.dropLast(1).mapNotNull { it.thresholdMillis }
        val latest = trustworthy.lastOrNull()?.thresholdMillis

        val change = latest?.let {
            ReliableChange.evaluate(history = history, latest = it, lowerIsBetter = true)
        }

        return InsightsUiState(
            isLoading = false,
            sessions = measured,
            change = change,
            totalSessions = protocol?.totalSessionsCompleted ?: 0,
            phase = protocol?.phase,
            hasCompletedABoosterBlock = protocol?.let(TrainingProtocol::hasCompletedABoosterBlock) ?: false,
            excludedForQuality = measured.size - trustworthy.size,
        )
    }

    public class Factory(
        private val vigilanceRepository: VigilanceRepository,
        private val protocolRepository: ProtocolRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InsightsViewModel(vigilanceRepository, protocolRepository) as T
    }

    private companion object {
        const val HISTORY_LIMIT = 60
    }
}

public data class InsightsUiState(
    public val isLoading: Boolean = true,
    public val sessions: List<StoredVigilanceSession> = emptyList(),
    public val change: ReliableChange.Result? = null,
    public val totalSessions: Int = 0,
    public val phase: TrainingProtocol.Phase? = null,
    public val hasCompletedABoosterBlock: Boolean = false,
    /** Sessions shown on the chart but excluded from the judgement. */
    public val excludedForQuality: Int = 0,
)
