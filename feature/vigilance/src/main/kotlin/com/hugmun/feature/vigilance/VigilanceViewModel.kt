/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.vigilance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hugmun.core.common.TimeSource
import com.hugmun.core.domain.ProtocolRepository
import com.hugmun.core.domain.SafetyRepository
import com.hugmun.core.domain.VigilanceRepository
import com.hugmun.core.model.AdverseEvent
import com.hugmun.core.model.Practice
import com.hugmun.core.model.SessionOutcome
import com.hugmun.engine.psychophysics.CentralFigure
import com.hugmun.engine.psychophysics.Direction
import com.hugmun.engine.psychophysics.DisplayTiming
import com.hugmun.engine.psychophysics.UfovResponse
import com.hugmun.engine.psychophysics.UfovSession
import com.hugmun.engine.psychophysics.UfovSessionResult
import com.hugmun.engine.psychophysics.UfovTrialSpec
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one «Зоркость» session.
 *
 * The division of labour is deliberate. [UfovSession] owns the *procedure* — which trial
 * comes next, how the staircase moves, when to stop — and knows nothing about Android.
 * This class owns session lifecycle and persistence. The composable owns *presentation*,
 * because only it can count display frames.
 *
 * Keeping those three apart is what makes the measurement testable: the procedure is
 * exercised against a simulated observer in `UfovSessionTest` with no emulator involved.
 */
public class VigilanceViewModel(
    private val vigilanceRepository: VigilanceRepository,
    private val protocolRepository: ProtocolRepository,
    private val safetyRepository: SafetyRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(VigilanceUiState())
    public val state: StateFlow<VigilanceUiState> = _state.asStateFlow()

    private var session: UfovSession? = null
    private var startedAt: Instant? = null
    private var pendingFigure: CentralFigure? = null
    private var presentationAccurate: Boolean = true
    private var responseStartedAtMillis: Long = 0L

    /**
     * Begins a session on a specific display.
     *
     * The timing is passed in rather than read here, because a ViewModel has no business
     * knowing about windows, and because the session record must contain the refresh rate
     * that was actually in effect.
     */
    public fun start(timing: DisplayTiming) {
        if (session != null) return

        val seed = timeSource.now().toEpochMilliseconds()
        session = UfovSession(timing = timing, seed = seed)
        startedAt = timeSource.now()

        _state.value = VigilanceUiState(
            phase = VigilancePhase.FIXATION,
            timing = timing,
            trial = session?.nextTrial(),
            trialNumber = 1,
        )
    }

    /** Called by the presenter as it moves through the fixed parts of a trial. */
    public fun setPhase(phase: VigilancePhase) {
        _state.value = _state.value.copy(phase = phase)
    }

    /**
     * Reports what the display actually did.
     *
     * A trial whose stimulus dropped a frame is carried forward as inaccurate and will be
     * discarded at scoring time rather than silently accepted — see ADR 0004. The user is
     * not told, because there is nothing for them to do about it and interrupting the
     * rhythm of a session would cost more than it gains.
     */
    public fun onPresented(accurate: Boolean) {
        presentationAccurate = accurate
        responseStartedAtMillis = timeSource.now().toEpochMilliseconds()
        pendingFigure = null
        _state.value = _state.value.copy(phase = VigilancePhase.AWAIT_FIGURE)
    }

    public fun onFigureChosen(figure: CentralFigure) {
        if (session == null) return
        pendingFigure = figure

        val spec = _state.value.trial ?: return
        if (spec.target == null) {
            // Level 1 has no peripheral subtask, so the trial ends with this answer.
            completeTrial(figure, direction = null)
        } else {
            _state.value = _state.value.copy(phase = VigilancePhase.AWAIT_DIRECTION)
        }
    }

    public fun onDirectionChosen(direction: Direction) {
        completeTrial(pendingFigure, direction)
    }

    private fun completeTrial(figure: CentralFigure?, direction: Direction?) {
        val current = session ?: return
        val latency = timeSource.now().toEpochMilliseconds() - responseStartedAtMillis

        val completed = current.record(
            UfovResponse(
                figure = figure,
                direction = direction,
                presentationAccurate = presentationAccurate,
                latencyMillis = latency,
            ),
        )

        // Feedback is one of the defining elements of the trained paradigm, so it stays —
        // but it is neutral and brief. No praise, no sound, no animation.
        _state.value = _state.value.copy(
            phase = VigilancePhase.FEEDBACK,
            lastOutcomeWasCorrect = completed.outcome.name == CORRECT,
        )
    }

    /** Moves to the next trial, or ends the session. */
    public fun advance() {
        val current = session ?: return
        if (current.isFinished) {
            finish(SessionOutcome.COMPLETED)
            return
        }

        val next = current.nextTrial()
        if (next == null) {
            finish(SessionOutcome.COMPLETED)
            return
        }

        presentationAccurate = true
        _state.value = _state.value.copy(
            phase = VigilancePhase.FIXATION,
            trial = next,
            trialNumber = _state.value.trialNumber + 1,
            lastOutcomeWasCorrect = null,
        )
    }

    public fun stopEarly() {
        finish(SessionOutcome.STOPPED_BY_USER)
    }

    /**
     * Records that the user felt unwell.
     *
     * Ends the session immediately and stores the report. For «Зоркость» the event does
     * not lock the practice — nothing here carries the risk that the photic channel does —
     * but it is kept permanently and appears in any export a clinician sees.
     */
    public fun reportAdverseEvent(kind: AdverseEvent.Kind, note: String? = null) {
        val event = AdverseEvent(kind = kind, reportedAt = timeSource.now(), note = note)
        viewModelScope.launch {
            safetyRepository.recordAdverseEvent(Practice.VIGILANCE, event)
        }
        finish(SessionOutcome.STOPPED_BY_APP, event)
    }

    private fun finish(outcome: SessionOutcome, adverseEvent: AdverseEvent? = null) {
        val current = session ?: return
        val began = startedAt ?: timeSource.now()
        val ended = timeSource.now()
        val result = current.result()

        session = null

        _state.value = _state.value.copy(
            phase = VigilancePhase.FINISHED,
            result = result,
            isSaving = true,
        )

        viewModelScope.launch {
            val id = vigilanceRepository.save(
                startedAt = began,
                endedAt = ended,
                outcome = outcome,
                result = result,
                trials = current.trials,
                adverseEvent = adverseEvent,
            )

            // A session only counts toward the protocol if it was actually completed.
            // Letting a session the user abandoned after two trials advance the schedule
            // would quietly corrupt the dose, which is the one thing the evidence says
            // must not happen.
            if (outcome == SessionOutcome.COMPLETED && result != null) {
                protocolRepository.recordCompletedSession(timeSource.today())
            }

            _state.value = _state.value.copy(savedSessionId = id, isSaving = false)
        }
    }

    public class Factory(
        private val vigilanceRepository: VigilanceRepository,
        private val protocolRepository: ProtocolRepository,
        private val safetyRepository: SafetyRepository,
        private val timeSource: TimeSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VigilanceViewModel(
            vigilanceRepository = vigilanceRepository,
            protocolRepository = protocolRepository,
            safetyRepository = safetyRepository,
            timeSource = timeSource,
        ) as T
    }

    private companion object {
        const val CORRECT = "CORRECT"
    }
}

public enum class VigilancePhase {
    IDLE,
    FIXATION,
    STIMULUS,
    MASK,
    AWAIT_FIGURE,
    AWAIT_DIRECTION,
    FEEDBACK,
    FINISHED,
}

public data class VigilanceUiState(
    public val phase: VigilancePhase = VigilancePhase.IDLE,
    public val timing: DisplayTiming? = null,
    public val trial: UfovTrialSpec? = null,
    public val trialNumber: Int = 0,
    public val lastOutcomeWasCorrect: Boolean? = null,
    public val result: UfovSessionResult? = null,
    public val savedSessionId: Long? = null,
    public val isSaving: Boolean = false,
)
