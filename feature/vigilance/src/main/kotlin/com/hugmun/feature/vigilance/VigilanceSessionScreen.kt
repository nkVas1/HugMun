/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.vigilance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugStopButton
import com.hugmun.core.designsystem.component.HugTextButton
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.engine.psychophysics.CentralFigure
import com.hugmun.engine.psychophysics.Direction
import com.hugmun.engine.psychophysics.DisplayTiming
import com.hugmun.engine.visuals.FramePresenter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The session screen.
 *
 * Structure of one trial: fixation → stimulus (an exact number of frames) → mask →
 * response (untimed) → brief neutral feedback.
 *
 * The presentation loop lives here rather than in the ViewModel because only a
 * composable can count display frames. Everything it learns — how many frames were
 * actually shown, whether any were dropped — goes straight back to the ViewModel, which
 * carries it into the stored trial.
 */
@Composable
public fun VigilanceSessionScreen(
    viewModel: VigilanceViewModel,
    timing: DisplayTiming,
    onFinished: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HugMunTheme.colors

    LaunchedEffect(timing) { viewModel.start(timing) }

    val presenter = remember(timing) { FramePresenter(timing) }

    // One trial's timed sequence. Keyed on the trial so a new trial restarts it, and
    // cancelled automatically if the user leaves mid-presentation.
    LaunchedEffect(state.trial?.index) {
        val spec = state.trial ?: return@LaunchedEffect
        if (state.phase == VigilancePhase.FINISHED) return@LaunchedEffect

        viewModel.setPhase(VigilancePhase.FIXATION)
        delay(spec.fixationMillis.toLong())

        viewModel.setPhase(VigilancePhase.STIMULUS)
        val presentation = presenter.presentFrames(spec.stimulusFrames)

        viewModel.setPhase(VigilancePhase.MASK)
        presenter.presentApproximately(spec.maskMillis)

        viewModel.onPresented(presentation.isAccurate)
    }

    LaunchedEffect(state.phase) {
        if (state.phase == VigilancePhase.FEEDBACK) {
            delay(FEEDBACK_MILLIS)
            viewModel.advance()
        }
    }

    LaunchedEffect(state.phase, state.isSaving) {
        if (state.phase == VigilancePhase.FINISHED && !state.isSaving) {
            onFinished(state.savedSessionId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            TrialCounter(
                trialNumber = state.trialNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HugMunTheme.dimens.screenGutter, vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (state.phase) {
                    VigilancePhase.AWAIT_DIRECTION -> DirectionResponse(
                        onChoose = viewModel::onDirectionChosen,
                    )

                    else -> StimulusField(
                        phase = when (state.phase) {
                            VigilancePhase.FIXATION -> TrialPhase.FIXATION
                            VigilancePhase.STIMULUS -> TrialPhase.STIMULUS
                            VigilancePhase.MASK -> TrialPhase.MASK
                            else -> TrialPhase.BLANK
                        },
                        spec = state.trial,
                        ink = colors.ink,
                        ground = colors.surfaceBase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HugMunTheme.dimens.screenGutter)
                    .padding(bottom = HugMunTheme.dimens.spaceL),
            ) {
                when (state.phase) {
                    VigilancePhase.AWAIT_FIGURE -> FigureResponse(onChoose = viewModel::onFigureChosen)
                    VigilancePhase.FEEDBACK -> Feedback(correct = state.lastOutcomeWasCorrect)
                    VigilancePhase.AWAIT_DIRECTION -> Prompt(text = "Где была метка?")
                    else -> SessionControls(
                        onStop = { viewModel.stopEarly() },
                        onUnwell = {
                            viewModel.reportAdverseEvent(com.hugmun.core.model.AdverseEvent.Kind.OTHER)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrialCounter(trialNumber: Int, modifier: Modifier = Modifier) {
    Text(
        text = "Проба $trialNumber",
        style = HugMunTheme.type.label,
        color = HugMunTheme.colors.inkFaint,
        modifier = modifier,
    )
}

@Composable
private fun Prompt(text: String) {
    Text(
        text = text,
        style = HugMunTheme.type.titleM,
        color = HugMunTheme.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FigureResponse(onChoose: (CentralFigure) -> Unit) {
    val dimens = HugMunTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        Prompt(text = "Кто был в центре?")
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.touchTargetGap)) {
            FigureChoice("Ворон", Modifier.weight(1f)) { onChoose(CentralFigure.RAVEN) }
            FigureChoice("Сова", Modifier.weight(1f)) { onChoose(CentralFigure.OWL) }
        }
    }
}

@Composable
private fun FigureChoice(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = HugMunTheme.colors
    Box(
        modifier = modifier
            .clip(HugMunTheme.shapes.primaryAction)
            .background(colors.dawnWash)
            .border(2.dp, SolidColor(colors.controlEdge), HugMunTheme.shapes.primaryAction)
            .foundationClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = HugMunTheme.type.titleL, color = colors.ink)
    }
}

/**
 * The eight-alternative localisation response, laid out as the field it refers to.
 *
 * A list of compass words would be an extra translation step between seeing and
 * answering. Tapping the place where the mark appeared is the same act as remembering
 * it, which is the point: the response should not add cognitive load to a task that is
 * measuring cognitive load.
 */
@Composable
private fun DirectionResponse(onChoose: (Direction) -> Unit) {
    val colors = HugMunTheme.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val ringRadius = maxWidth * RING_RADIUS_FRACTION

        Box(
            modifier = Modifier
                .size(CENTRE_DOT)
                .clip(HugMunTheme.shapes.chip)
                .background(colors.surfaceEdge),
        )

        Direction.ALL.forEach { direction ->
            val radians = (direction.degrees - QUARTER_TURN) * PI.toFloat() / HALF_TURN
            val dx = ringRadius * cos(radians)
            val dy = ringRadius * sin(radians)

            Box(
                modifier = Modifier
                    .offset(x = dx, y = dy)
                    .size(TARGET_SIZE)
                    .clip(HugMunTheme.shapes.chip)
                    .background(colors.surfaceRaised)
                    .border(2.dp, SolidColor(colors.controlEdge), HugMunTheme.shapes.chip)
                    .foundationClickable(role = Role.Button) { onChoose(direction) }
                    .semantics {
                        contentDescription = directionLabel(direction)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(TARGET_DOT)
                        .clip(HugMunTheme.shapes.chip)
                        .background(colors.dawn),
                )
            }
        }
    }
}

@Composable
private fun Feedback(correct: Boolean?) {
    val colors = HugMunTheme.colors
    val text = when (correct) {
        true -> "Верно"
        false -> "Не угадали"
        null -> ""
    }
    val colour = if (correct == true) colors.moss else colors.inkMuted

    Text(
        text = text,
        style = HugMunTheme.type.titleM,
        color = colour,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SessionControls(onStop: () -> Unit, onUnwell: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceS),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HugStopButton(text = "Остановить занятие", onClick = onStop)
        HugTextButton(text = "Мне нехорошо", onClick = onUnwell)
    }
}

/**
 * Spoken labels for the eight positions.
 *
 * Clock positions rather than compass points: "на двенадцати часах" is the phrasing a
 * Russian speaker of this generation uses for a direction, and it needs no spatial
 * translation.
 */
private fun directionLabel(direction: Direction): String {
    val clock = ((direction.index * CLOCK_STEP) % CLOCK_POSITIONS).let { if (it == 0) CLOCK_POSITIONS else it }
    return "Метка на $clock часах"
}

private const val FEEDBACK_MILLIS = 550L
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val RING_RADIUS_FRACTION = 0.36f
private const val CLOCK_STEP = 3
private const val CLOCK_POSITIONS = 12
private val TARGET_SIZE = 64.dp
private val TARGET_DOT = 16.dp
private val CENTRE_DOT = 12.dp
