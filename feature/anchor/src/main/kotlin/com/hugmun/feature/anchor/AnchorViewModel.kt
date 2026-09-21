/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.anchor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hugmun.core.common.TimeSource
import com.hugmun.core.domain.AnchorRepository
import com.hugmun.core.domain.BuildAnchorPromptUseCase
import com.hugmun.core.domain.ReviewAnchorItemUseCase
import com.hugmun.core.model.AnchorItem
import com.hugmun.core.model.AnchorKind
import com.hugmun.core.model.AnchorPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Якорь».
 *
 * The review loop implements errorless learning literally: at the lowest cue level the
 * correct option is marked, so the user cannot produce a wrong answer at all, and a
 * six-second pause reveals the answer rather than letting an error happen. "Wrong" is
 * therefore never a state this screen can reach — only "needed help", which is scored
 * differently and, crucially, feels differently.
 */
public class AnchorViewModel(
    private val repository: AnchorRepository,
    private val reviewItem: ReviewAnchorItemUseCase,
    private val buildPrompt: BuildAnchorPromptUseCase,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(AnchorUiState())
    public val state: StateFlow<AnchorUiState> = _state.asStateFlow()

    private var queue: List<AnchorItem> = emptyList()
    private var queueIndex: Int = 0
    private var promptShownAtMillis: Long = 0L
    private var helpedInSession: Boolean = false

    init {
        viewModelScope.launch {
            repository.observeItems().collect { items ->
                _state.value = _state.value.copy(items = items, isLoading = false)
            }
        }
        viewModelScope.launch {
            repository.observeDueCount(timeSource.today()).collect { due ->
                _state.value = _state.value.copy(dueCount = due)
            }
        }
    }

    public fun addItem(kind: AnchorKind, prompt: String, answer: String, note: String?) {
        if (prompt.isBlank() || answer.isBlank()) return
        viewModelScope.launch {
            repository.add(
                AnchorItem(
                    kind = kind,
                    prompt = prompt.trim(),
                    answer = answer.trim(),
                    note = note?.trim()?.takeIf { it.isNotBlank() },
                    // Due today: a new item is studied in the session it was added,
                    // never tested. The first exposure is always a study trial.
                    dueOn = timeSource.today(),
                    createdAt = timeSource.now(),
                ),
            )
        }
    }

    public fun archive(id: Long) {
        viewModelScope.launch { repository.archive(id) }
    }

    public fun startReview() {
        viewModelScope.launch {
            queue = repository.dueItems(timeSource.today())
            queueIndex = 0
            _state.value = _state.value.copy(
                reviewedInSession = 0,
                helpedInSession = 0,
                isSessionFinished = queue.isEmpty(),
            )
            presentCurrent()
        }
    }

    private suspend fun presentCurrent() {
        val item = queue.getOrNull(queueIndex)
        if (item == null) {
            _state.value = _state.value.copy(prompt = null, isSessionFinished = true)
            return
        }

        helpedInSession = false
        promptShownAtMillis = timeSource.now().toEpochMilliseconds()
        _state.value = _state.value.copy(
            prompt = buildPrompt(item),
            revealedAnswer = null,
            isSessionFinished = false,
        )
    }

    /**
     * The user chose an option.
     *
     * At the fully-cued level the correct option is marked, so a choice here is
     * confirmation rather than recall — which is exactly what a study trial is, and it
     * is scored as assisted.
     */
    public fun choose(option: String) {
        val prompt = _state.value.prompt ?: return
        val correct = option == prompt.item.answer

        if (!correct) {
            // Under errorless learning a wrong choice is not scored as a failure; the
            // answer is simply shown, and the trial counts as needing help.
            reveal()
            return
        }

        complete(recalledUnaided = !helpedInSession && !prompt.isFullyCued)
    }

    /** Shows the answer. Called on a long pause, or when the user asks. */
    public fun reveal() {
        val prompt = _state.value.prompt ?: return
        helpedInSession = true
        _state.value = _state.value.copy(revealedAnswer = prompt.item.answer)
    }

    /** Moves on after the answer has been shown. */
    public fun continueAfterReveal() {
        complete(recalledUnaided = false)
    }

    private fun complete(recalledUnaided: Boolean) {
        val prompt = _state.value.prompt ?: return
        val latency = timeSource.now().toEpochMilliseconds() - promptShownAtMillis

        viewModelScope.launch {
            reviewItem(item = prompt.item, recalledUnaided = recalledUnaided, latencyMillis = latency)

            queueIndex++
            _state.value = _state.value.copy(
                reviewedInSession = _state.value.reviewedInSession + 1,
                helpedInSession = _state.value.helpedInSession + if (recalledUnaided) 0 else 1,
            )
            presentCurrent()
        }
    }

    public class Factory(
        private val repository: AnchorRepository,
        private val reviewItem: ReviewAnchorItemUseCase,
        private val buildPrompt: BuildAnchorPromptUseCase,
        private val timeSource: TimeSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AnchorViewModel(repository, reviewItem, buildPrompt, timeSource) as T
    }

    public companion object {
        /**
         * How long to wait before revealing the answer.
         *
         * Long enough not to rush someone who is thinking, short enough that the silence
         * does not become uncomfortable. The reveal is what prevents an error, so it must
         * happen before frustration does.
         */
        public const val REVEAL_AFTER_MILLIS: Long = 6_000L
    }
}

public data class AnchorUiState(
    public val isLoading: Boolean = true,
    public val items: List<AnchorItem> = emptyList(),
    public val dueCount: Int = 0,
    public val prompt: AnchorPrompt? = null,
    public val revealedAnswer: String? = null,
    public val reviewedInSession: Int = 0,
    public val helpedInSession: Int = 0,
    public val isSessionFinished: Boolean = false,
) {
    public val recalledUnaidedInSession: Int get() = reviewedInSession - helpedInSession
}
