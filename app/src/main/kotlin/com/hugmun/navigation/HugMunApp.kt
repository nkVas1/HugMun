/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hugmun.core.designsystem.component.BottomBarItem
import com.hugmun.core.designsystem.component.HugBottomBar
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.Practice
import com.hugmun.di.AppGraph
import com.hugmun.engine.psychophysics.DisplayTiming
import com.hugmun.feature.home.HomeScreen
import com.hugmun.feature.home.HomeViewModel
import com.hugmun.feature.insights.InsightsScreen
import com.hugmun.feature.insights.InsightsViewModel
import com.hugmun.feature.library.EvidenceScreen
import com.hugmun.feature.library.LibraryScreen
import com.hugmun.feature.rhythm.PhoticConsentScreen
import com.hugmun.feature.rhythm.PhotosensitivityScreeningScreen
import com.hugmun.feature.rhythm.RhythmIntroScreen
import com.hugmun.feature.rhythm.RhythmSessionScreen
import com.hugmun.feature.rhythm.RhythmViewModel
import com.hugmun.feature.vigilance.VigilanceIntroScreen
import com.hugmun.feature.vigilance.VigilanceResultScreen
import com.hugmun.feature.vigilance.VigilanceResultView
import com.hugmun.feature.vigilance.VigilanceSessionScreen
import com.hugmun.feature.vigilance.VigilanceViewModel

/**
 * The application shell.
 *
 * Navigation 3, whose entire premise is that the back stack is an ordinary list the app
 * owns. That fits the way the rest of this codebase is put together — see ADR 0002 — and
 * it means "where can the user go from here?" is answered by reading one `when`.
 */
@Composable
public fun HugMunApp(
    graph: AppGraph,
    displayTiming: DisplayTiming,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Destination.Today)

    // A session must not be interruptible by a stray back gesture; the screen offers an
    // explicit stop control instead, which is also the accessible path.
    val current = backStack.lastOrNull()
    val blockBack = current is Destination.VigilanceSession
    BackHandler(enabled = blockBack) { /* deliberately ignored */ }

    fun goTo(destination: Destination) {
        backStack.add(destination)
    }

    fun switchTopLevel(destination: Destination) {
        backStack.clear()
        backStack.add(destination)
    }

    fun popToToday() {
        backStack.clear()
        backStack.add(Destination.Today)
    }

    val bottomBar: @Composable () -> Unit = {
        val selected = TopLevel.forDestination(current as? Destination ?: Destination.Today)
        HugBottomBar(
            items = TopLevel.entries.map { BottomBarItem(it.name, stringResource(it.labelRes)) },
            selectedId = selected?.name,
            onSelect = { item -> switchTopLevel(TopLevel.valueOf(item.id).destination) },
        )
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider<NavKey> {
            entry<Destination.Today> {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        planToday = graph.planToday,
                        protocolRepository = graph.protocolRepository,
                        vigilanceRepository = graph.vigilanceRepository,
                        preferencesRepository = graph.preferencesRepository,
                        timeSource = graph.timeSource,
                    ),
                )
                HomeScreen(
                    viewModel = viewModel,
                    onEnrolled = onRequestNotificationPermission,
                    onStartPractice = { goTo(Destination.VigilanceIntro) },
                    onOpenOptional = { practice ->
                        when (practice) {
                            Practice.RHYTHM -> goTo(Destination.RhythmIntro)
                            else -> goTo(Destination.Evidence(practice.id))
                        }
                    },
                    onOpenEvidence = { goTo(Destination.Evidence(it.id)) },
                    bottomBar = bottomBar,
                )
            }

            entry<Destination.Progress> {
                val viewModel: InsightsViewModel = viewModel(
                    factory = InsightsViewModel.Factory(
                        vigilanceRepository = graph.vigilanceRepository,
                        protocolRepository = graph.protocolRepository,
                    ),
                )
                InsightsScreen(viewModel = viewModel, bottomBar = bottomBar)
            }

            entry<Destination.More> {
                LibraryScreen(
                    onOpenPractice = { goTo(Destination.Evidence(it.id)) },
                    bottomBar = bottomBar,
                )
            }

            entry<Destination.VigilanceIntro> {
                VigilanceIntroScreen(
                    onBegin = { goTo(Destination.VigilanceSession) },
                    onOpenEvidence = { goTo(Destination.Evidence("vigilance")) },
                    onCancel = { popToToday() },
                )
            }

            entry<Destination.VigilanceSession> {
                val viewModel: VigilanceViewModel = viewModel(
                    factory = VigilanceViewModel.Factory(
                        vigilanceRepository = graph.vigilanceRepository,
                        protocolRepository = graph.protocolRepository,
                        safetyRepository = graph.safetyRepository,
                        timeSource = graph.timeSource,
                    ),
                )
                VigilanceSessionScreen(
                    viewModel = viewModel,
                    timing = displayTiming,
                    onFinished = { sessionId ->
                        backStack.clear()
                        backStack.add(Destination.Today)
                        backStack.add(Destination.VigilanceResult(sessionId ?: 0L))
                    },
                )
            }

            entry<Destination.RhythmIntro> {
                val viewModel = rhythmViewModel(graph)
                LaunchedEffect(displayTiming) { viewModel.onDisplayResolved(displayTiming) }
                RhythmIntroScreen(
                    viewModel = viewModel,
                    onStartAudioOnly = { goTo(Destination.RhythmSession(withLight = false)) },
                    onStartWithLight = { goTo(Destination.RhythmSession(withLight = true)) },
                    onOpenScreening = { goTo(Destination.RhythmScreening) },
                    onOpenEvidence = { goTo(Destination.Evidence(Practice.RHYTHM.id)) },
                )
            }

            entry<Destination.RhythmScreening> {
                val viewModel = rhythmViewModel(graph)
                PhotosensitivityScreeningScreen(
                    viewModel = viewModel,
                    onFinished = {
                        backStack.removeAt(backStack.lastIndex)
                        goTo(Destination.RhythmConsent)
                    },
                )
            }

            entry<Destination.RhythmConsent> {
                val viewModel = rhythmViewModel(graph)
                PhoticConsentScreen(
                    viewModel = viewModel,
                    onGranted = { backStack.removeAt(backStack.lastIndex) },
                    onDeclined = { backStack.removeAt(backStack.lastIndex) },
                )
            }

            entry<Destination.RhythmSession> { key ->
                val viewModel = rhythmViewModel(graph)
                // Dark surround is part of the stimulus, not a preference.
                HugMunTheme(forceDark = true) {
                    RhythmSessionScreen(
                        viewModel = viewModel,
                        withLight = key.withLight,
                        onFinished = { popToToday() },
                    )
                }
            }

            entry<Destination.VigilanceResult> {
                VigilanceResultRoute(graph = graph, onDone = ::popToToday)
            }

            entry<Destination.Evidence> { key ->
                val practice = Practice.fromId(key.practiceId)
                if (practice == null) {
                    PlaceholderScreen(title = "Не найдено", body = "")
                } else {
                    EvidenceScreen(
                        practice = practice,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    )
                }
            }

            entry<Destination.Library> {
                LibraryScreen(onOpenPractice = { goTo(Destination.Evidence(it.id)) })
            }

            entry<Destination.Settings> {
                PlaceholderScreen(title = "Настройки", body = "")
            }

            entry<Destination.About> {
                PlaceholderScreen(title = "О программе", body = "")
            }
        },
    )
}

/**
 * Loads the stored session so the result survives process death.
 *
 * Reading it back rather than carrying it through the back stack means the screen shows
 * what was actually written to disk, which is the same thing a clinician would see in an
 * export. Rendering from memory in one case and from storage in another is how the two
 * quietly diverge.
 */
@Composable
private fun VigilanceResultRoute(graph: AppGraph, onDone: () -> Unit) {
    var view by remember { mutableStateOf<VigilanceResultView?>(null) }
    var previous by remember { mutableStateOf<Double?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val latest = graph.vigilanceRepository.latestResult()
        view = latest?.let(VigilanceResultView::from)
        // The newest entry in the history is this session; the one before it is the
        // comparison the user actually cares about.
        previous = graph.vigilanceRepositoryImpl.thresholdHistory().dropLast(1).lastOrNull()
        loaded = true
    }

    if (loaded) {
        VigilanceResultScreen(
            result = view,
            previousThresholdMillis = previous,
            onDone = onDone,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize())
    }
}

/**
 * One ViewModel shared across the «Ритм» flow.
 *
 * Keyed so that the screening, consent and session screens see the same instance:
 * answering the questionnaire must immediately change what the intro screen offers.
 */
@Composable
private fun rhythmViewModel(graph: AppGraph): RhythmViewModel = viewModel(
    key = "rhythm",
    factory = RhythmViewModel.Factory(
        safetyRepository = graph.safetyRepository,
        timeSource = graph.timeSource,
    ),
)

@Composable
private fun PlaceholderScreen(title: String, body: String, bottomBar: @Composable () -> Unit = {}) {
    HugScreen(bottomBar = bottomBar) {
        HugScreenTitle(text = title)
        if (body.isNotBlank()) {
            Text(text = body, style = HugMunTheme.type.bodyL, color = HugMunTheme.colors.inkMuted)
        }
    }
}
