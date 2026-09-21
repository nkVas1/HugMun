/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hugmun.core.common.TimeSource
import com.hugmun.core.domain.DayPlan
import com.hugmun.core.domain.PlanTodayUseCase
import com.hugmun.core.domain.PreferencesRepository
import com.hugmun.core.domain.ProtocolRepository
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.core.domain.VigilanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

public class HomeViewModel(
    private val planToday: PlanTodayUseCase,
    private val protocolRepository: ProtocolRepository,
    private val vigilanceRepository: VigilanceRepository,
    private val preferencesRepository: PreferencesRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    public val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observe()
        refresh()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                protocolRepository.observeState(),
                vigilanceRepository.observeResults(limit = HISTORY_FOR_HOME),
                preferencesRepository.observe(),
            ) { _, history, preferences ->
                history to preferences
            }.collect { (history, preferences) ->
                _state.value = _state.value.copy(
                    lastSession = history.firstOrNull(),
                    displayName = preferences.displayName,
                )
                refresh()
            }
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            val plan = planToday()
            _state.value = _state.value.copy(
                plan = plan,
                partOfDay = partOfDay(),
                isLoading = false,
            )
        }
    }

    /** Creates the protocol state the first time the user chooses to begin. */
    public fun enrolIfNeeded(onReady: () -> Unit) {
        viewModelScope.launch {
            protocolRepository.enrol(timeSource.today())
            refresh()
            onReady()
        }
    }

    private fun partOfDay(): PartOfDay {
        val local = timeSource.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        return when {
            local < LocalTime(hour = MORNING_END, minute = 0) -> PartOfDay.MORNING
            local < LocalTime(hour = DAY_END, minute = 0) -> PartOfDay.DAY
            local < LocalTime(hour = EVENING_END, minute = 0) -> PartOfDay.EVENING
            else -> PartOfDay.NIGHT
        }
    }

    public class Factory(
        private val planToday: PlanTodayUseCase,
        private val protocolRepository: ProtocolRepository,
        private val vigilanceRepository: VigilanceRepository,
        private val preferencesRepository: PreferencesRepository,
        private val timeSource: TimeSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
            planToday = planToday,
            protocolRepository = protocolRepository,
            vigilanceRepository = vigilanceRepository,
            preferencesRepository = preferencesRepository,
            timeSource = timeSource,
        ) as T
    }

    private companion object {
        const val HISTORY_FOR_HOME = 10
        const val MORNING_END = 12
        const val DAY_END = 18
        const val EVENING_END = 23
    }
}

public data class HomeUiState(
    public val isLoading: Boolean = true,
    public val plan: DayPlan? = null,
    public val lastSession: StoredVigilanceSession? = null,
    public val displayName: String? = null,
    public val partOfDay: PartOfDay = PartOfDay.DAY,
)

/**
 * Time of day, used for the greeting and for the very slight shift in the page's warmth.
 *
 * The light moving with the day is the whole atmosphere of the design, achieved without
 * a single illustration — see `docs/design/design-language.md` §3.6.
 */
public enum class PartOfDay {
    MORNING,
    DAY,
    EVENING,
    NIGHT,
}
