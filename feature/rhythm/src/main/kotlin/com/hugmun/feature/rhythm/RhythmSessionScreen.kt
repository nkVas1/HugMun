/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.rhythm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugStopButton
import com.hugmun.core.designsystem.component.HugTextButton
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.AdverseEvent
import com.hugmun.engine.audio.GammaStimulusPlayer
import com.hugmun.engine.visuals.PhoticStimulus
import kotlinx.coroutines.launch

/**
 * The «Ритм» session.
 *
 * Structure of the loop: the audio player is the clock, the visual disc reads its phase
 * from `AudioTrack.getTimestamp()` and draws a raised-cosine luminance at that phase.
 * When both channels run, they are therefore the *combined* stimulus from the source
 * studies rather than two independent ones that drift apart. See ADR 0003.
 *
 * Safety behaviour, all of it load-bearing:
 *
 * - The theme is forced dark, because a dark surround is part of the stimulus.
 * - Depth ramps from zero over twenty seconds; the first moments are almost invisible.
 * - **Any tap anywhere stops everything**, with no confirmation dialog and no animation.
 * - The system back gesture also stops it, rather than being swallowed.
 * - "Мне нехорошо" ends the session, records the event and locks the practice.
 */
@Composable
public fun RhythmSessionScreen(
    viewModel: RhythmViewModel,
    withLight: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = HugMunTheme.colors

    val stimulus = remember { PhoticStimulus.create() }
    val player = remember {
        GammaStimulusPlayer(scope = scope, dispatcher = kotlinx.coroutines.Dispatchers.Default)
    }

    var elapsedSeconds by remember { mutableDoubleStateOf(0.0) }
    var luminance by remember { mutableDoubleStateOf(0.0) }
    var startNanos by remember { mutableLongStateOf(0L) }

    fun stop(immediate: Boolean) {
        viewModel.setRunning(false)
        scope.launch {
            player.stop(immediate = immediate)
            onFinished()
        }
    }

    DisposableEffect(Unit) {
        player.start()
        viewModel.setRunning(true)
        onDispose {
            // A session must never outlive its screen; leaking a stimulus into the
            // background would be the worst possible defect in this module.
            scope.launch { player.stop(immediate = true) }
        }
    }

    // The render loop. Phase comes from the audio clock when it is available, and from
    // the frame clock otherwise — the light must stay correct even if the platform
    // declines to report an audio timestamp.
    LaunchedEffect(withLight) {
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                elapsedSeconds = (now - startNanos) / NANOS_PER_SECOND

                if (withLight) {
                    val epoch = player.audioClock()
                    val phase = epoch?.phaseAt(now, PhoticStimulus.GAMMA_HZ)
                        ?: freeRunningPhase(now, startNanos)
                    luminance = stimulus.luminanceAt(phase, stimulus.rampedDepth(elapsedSeconds))
                }
            }
        }
    }

    BackHandler { stop(immediate = true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.stimulusBackdrop)
            // The abort control. Deliberately the whole screen: someone who feels unwell
            // should not have to find a button.
            .pointerInput(Unit) {
                detectTapGestures { stop(immediate = true) }
            },
    ) {
        if (withLight) {
            PhoticDisc(
                luminance = luminance,
                radiusFraction = stimulus.discRadiusFraction,
                colour = stimulus.colourAt(luminance),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = HugMunTheme.dimens.screenGutter),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatElapsed(elapsedSeconds),
                style = HugMunTheme.type.numericM,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = HugMunTheme.dimens.spaceM),
            )

            if (!withLight) {
                Text(
                    text = "Закройте глаза, если хотите. Звук работает сам.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "Смотрите на пятно спокойно, не вглядываясь.",
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = HugMunTheme.dimens.spaceL),
                verticalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceS),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HugStopButton(text = "Остановить", onClick = { stop(immediate = true) })
                HugTextButton(
                    text = "Мне нехорошо",
                    emphasis = true,
                    onClick = {
                        viewModel.reportAdverseEvent(AdverseEvent.Kind.VISUAL_DISCOMFORT)
                        stop(immediate = true)
                    },
                )
            }
        }
    }

    // Keeps the availability state fresh while the screen is open; a lock recorded
    // mid-session must take effect immediately.
    LaunchedEffect(state.isLocked) {
        if (state.isLocked) stop(immediate = true)
    }
}

/**
 * The stimulus itself: a central disc with a soft radial falloff.
 *
 * Never the full screen. The bounded area keeps the stimulated solid angle far closer
 * to the photosensitivity guideline threshold than a full-field flash would, at some
 * cost in entrainment strength — a deliberate trade recorded in
 * `docs/research/03-safety-and-regulatory.md` §3.3.
 */
@Composable
private fun PhoticDisc(luminance: Double, radiusFraction: Double, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * radiusFraction.toFloat()
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to colour,
                    FALLOFF_START to colour,
                    1f to Color.Transparent,
                ),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
            alpha = luminance.coerceIn(0.0, 1.0).toFloat(),
        )
    }
}

/**
 * Phase from the frame clock, used only when the platform will not report an audio
 * timestamp. Correct in frequency, arbitrary in absolute phase — which is fine for the
 * light alone, and is why the audio clock is preferred when both channels run.
 */
private fun freeRunningPhase(nowNanos: Long, startNanos: Long): Double {
    val seconds = (nowNanos - startNanos) / NANOS_PER_SECOND
    val cycles = seconds * PhoticStimulus.GAMMA_HZ
    return cycles - kotlin.math.floor(cycles)
}

private fun formatElapsed(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val minutes = total / SECONDS_PER_MINUTE
    val remainder = total % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, remainder)
}

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val SECONDS_PER_MINUTE = 60
private const val FALLOFF_START = 0.55f
