/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.rhythm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hugmun.core.common.TimeSource
import com.hugmun.core.domain.SafetyRepository
import com.hugmun.core.model.AdverseEvent
import com.hugmun.core.model.Practice
import com.hugmun.engine.psychophysics.DisplayTiming
import com.hugmun.engine.visuals.PhoticAvailability
import com.hugmun.engine.visuals.PhoticBlockReason
import com.hugmun.engine.visuals.PhoticStimulus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Ритм» — the experimental 40 Hz practice.
 *
 * Most of this class is refusal. The audio channel is always available; the photic
 * channel must clear screening, a separate consent, a display check, a daily cap and an
 * adverse-event lock before it will run, and when it will not run the user is told
 * precisely why rather than finding a greyed-out switch.
 *
 * The ordering of the checks is deliberate: the reason shown is the *first* thing that
 * blocks, so a user on a 60 Hz phone is told about their screen rather than being walked
 * through a medical questionnaire that will not help them.
 */
public class RhythmViewModel(private val safetyRepository: SafetyRepository, private val timeSource: TimeSource) :
    ViewModel() {

    private val _state = MutableStateFlow(RhythmUiState())
    public val state: StateFlow<RhythmUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            safetyRepository.observe().collect { safety ->
                _state.value = _state.value.copy(
                    isScreeningPassed = safety.photosensitivityScreeningPassedAt != null,
                    isConsentGiven = safety.photicConsentGrantedAt != null,
                    isLocked = safety.isLocked(Practice.RHYTHM),
                    lockReason = safety.lockReason(Practice.RHYTHM),
                    minutesUsedToday = safety.photicMinutesUsedToday,
                )
                recomputeAvailability()
            }
        }
    }

    /** Called once the display is known. */
    public fun onDisplayResolved(timing: DisplayTiming) {
        _state.value = _state.value.copy(
            displayTiming = timing,
            canDisplayRender = PhoticStimulus.canRender(timing),
        )
        recomputeAvailability()
    }

    /** The room is too bright or too dark, from the ambient light sensor. */
    public fun onAmbientLight(lux: Float?) {
        _state.value = _state.value.copy(isRoomTooDark = lux != null && lux < MIN_AMBIENT_LUX)
        recomputeAvailability()
    }

    public fun submitScreening(answers: List<Boolean>) {
        // Any "yes" fails. There is no scoring and no borderline case: the questions are
        // all contraindications, and a single one is disqualifying.
        val passed = answers.none { it }
        viewModelScope.launch {
            safetyRepository.recordPhotosensitivityScreening(passed, timeSource.now())
        }
    }

    public fun grantConsent() {
        viewModelScope.launch { safetyRepository.setPhoticConsent(true, timeSource.now()) }
    }

    public fun withdrawConsent() {
        viewModelScope.launch { safetyRepository.setPhoticConsent(false, timeSource.now()) }
    }

    /**
     * Records that the user felt unwell and locks the practice.
     *
     * Unlike «Зоркость», any adverse event here locks the module: this is the one
     * stimulus in the app with a plausible mechanism for harm, and the correct response
     * to an unexplained symptom is to stop offering it.
     */
    public fun reportAdverseEvent(kind: AdverseEvent.Kind, note: String? = null) {
        val event = AdverseEvent(kind = kind, reportedAt = timeSource.now(), note = note)
        viewModelScope.launch {
            safetyRepository.recordAdverseEvent(Practice.RHYTHM, event)
            safetyRepository.setPhoticConsent(false, timeSource.now())
        }
        _state.value = _state.value.copy(isRunning = false)
    }

    public fun setRunning(running: Boolean) {
        _state.value = _state.value.copy(isRunning = running)
    }

    public fun setVisualRequested(requested: Boolean) {
        _state.value = _state.value.copy(isVisualRequested = requested)
    }

    private fun recomputeAvailability() {
        val current = _state.value
        val reason = when {
            current.isLocked -> PhoticBlockReason.LOCKED_AFTER_ADVERSE_EVENT
            current.canDisplayRender == false -> PhoticBlockReason.DISPLAY_CANNOT_RENDER
            !current.isScreeningPassed -> PhoticBlockReason.NOT_SCREENED
            !current.isConsentGiven -> PhoticBlockReason.NOT_CONSENTED
            current.minutesUsedToday >= DAILY_LIMIT_MINUTES -> PhoticBlockReason.DAILY_LIMIT_REACHED
            current.isRoomTooDark -> PhoticBlockReason.ROOM_TOO_DARK
            else -> null
        }

        _state.value = current.copy(
            photic = reason?.let(PhoticAvailability::Blocked) ?: PhoticAvailability.Available,
        )
    }

    public class Factory(private val safetyRepository: SafetyRepository, private val timeSource: TimeSource) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RhythmViewModel(safetyRepository, timeSource) as T
    }

    public companion object {
        /** SAFETY.md: 60 minutes a day, enforced below the UI. */
        public const val DAILY_LIMIT_MINUTES: Int = 60

        /**
         * Below this the room is dark enough that relative contrast is at its highest.
         *
         * Roughly the light level of a dim bedroom. The stimulus is offered in an
         * ordinarily lit room or not at all.
         */
        public const val MIN_AMBIENT_LUX: Float = 15f
    }
}

public data class RhythmUiState(
    public val photic: PhoticAvailability = PhoticAvailability.Blocked(PhoticBlockReason.NOT_SCREENED),
    public val displayTiming: DisplayTiming? = null,
    public val canDisplayRender: Boolean? = null,
    public val isScreeningPassed: Boolean = false,
    public val isConsentGiven: Boolean = false,
    public val isLocked: Boolean = false,
    public val lockReason: String? = null,
    public val minutesUsedToday: Int = 0,
    public val isRoomTooDark: Boolean = false,
    public val isRunning: Boolean = false,
    public val isVisualRequested: Boolean = false,
) {
    /** The audio channel carries none of the photic risks and is never gated on them. */
    public val isAudioAvailable: Boolean get() = !isLocked

    public val willShowLight: Boolean get() = isVisualRequested && photic.isAvailable
}

/**
 * The photosensitivity screen.
 *
 * Every item is a contraindication, phrased so that "yes" always means "do not proceed".
 * Mixing polarity would be a trap for a tired reader, which in a safety questionnaire is
 * a defect rather than a style choice.
 */
public object PhotosensitivityScreening {
    public val QUESTIONS: List<String> = listOf(
        "Были ли у вас когда-нибудь судороги, приступы или эпилепсия?",
        "Была ли эпилепсия у кого-то из ваших родителей, братьев или сестёр?",
        "Бывает ли у вас мигрень со зрительной аурой — мерцанием, зигзагами, пятнами перед глазами?",
        "Случалось ли, что от мигающего света, стробоскопа или мелькания на экране вам становилось плохо?",
        "Принимаете ли вы лекарства, о которых врач предупреждал, что они снижают порог судорожной готовности?",
    )
}
