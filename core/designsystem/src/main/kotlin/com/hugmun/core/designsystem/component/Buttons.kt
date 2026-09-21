/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme

/**
 * The primary action — the one tactile object in the interface.
 *
 * This is where the «Гнездо» direction is allowed in: warm enamel, a 1 dp inner highlight
 * along the top edge, a soft warm shadow beneath, and a real pressed state where the
 * whole control travels downward and the shadow collapses. Nothing else in HugMun
 * pretends to be a physical object, which is exactly what makes this read as pressable
 * to someone who is not confident with touchscreens.
 *
 * There is at most one of these per screen. If a screen needs two primary actions, it is
 * two screens.
 */
@Composable
public fun HugPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    val shape = HugMunTheme.shapes.primaryAction

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val travel by animateDpAsState(
        targetValue = if (pressed) PRESS_TRAVEL else 0.dp,
        animationSpec = HugMunTheme.motion.stateChange(),
        label = "primaryButtonTravel",
    )
    val shadowY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 6.dp,
        animationSpec = HugMunTheme.motion.stateChange(),
        label = "primaryButtonShadowY",
    )
    val shadowBlur by animateDpAsState(
        targetValue = if (pressed) 8.dp else 20.dp,
        animationSpec = HugMunTheme.motion.stateChange(),
        label = "primaryButtonShadowBlur",
    )

    val fill = when {
        !enabled -> colors.surfaceSunk
        pressed -> lerp(colors.dawnBright, Color.Black, PRESS_DARKEN)
        else -> colors.dawnBright
    }
    val label = if (enabled) colors.onDawn else colors.inkFaint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offsetY(travel)
            .softShadow(
                color = colors.dawn,
                alpha = if (enabled) SHADOW_ALPHA else 0f,
                offsetY = shadowY,
                blur = shadowBlur,
                shape = shape,
            )
            .clip(shape)
            .background(fill)
            .enamelHighlight(enabled = enabled && !pressed, shape = shape)
            .heightIn(min = dimens.primaryActionHeight)
            .foundationClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = dimens.spaceL, vertical = dimens.spaceM),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                style = HugMunTheme.type.titleL,
                color = label,
                textAlign = TextAlign.Center,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = HugMunTheme.type.label,
                    color = label,
                    modifier = Modifier.alpha(SUPPORTING_ALPHA),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Secondary action: outlined, flat, no shadow. Deliberately quieter than
 * [HugPrimaryButton] so the hierarchy is unmistakable at a glance.
 */
@Composable
public fun HugSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    val content = if (enabled) colors.ink else colors.inkFaint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HugMunTheme.shapes.primaryAction)
            .border(
                border = BorderStroke(dimens.hairline * 2, SolidColor(colors.controlEdge)),
                shape = HugMunTheme.shapes.primaryAction,
            )
            .heightIn(min = dimens.minTouchTarget)
            .foundationClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceL, vertical = dimens.spaceM),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HugMunTheme.type.titleM, color = content, textAlign = TextAlign.Center)
    }
}

/**
 * Tertiary action: plain text with a full-size hit area.
 *
 * The visible text may be small; the touch target never is. Every gesture in HugMun has
 * a visible button equivalent, and this is usually it.
 */
@Composable
public fun HugTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    val content = when {
        !enabled -> colors.inkFaint
        emphasis -> colors.dawn
        else -> colors.inkMuted
    }

    Row(
        modifier = modifier
            .clip(HugMunTheme.shapes.chip)
            .defaultMinSize(minWidth = dimens.minTouchTarget, minHeight = dimens.minTouchTarget)
            .foundationClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = HugMunTheme.type.label, color = content)
    }
}

/**
 * A large, unmistakable stop control.
 *
 * Used inside stimulation and measurement sessions, where the safety policy requires the
 * user to be able to get out instantly. It is never behind a confirmation dialog, never
 * animated away, and never smaller than this.
 */
@Composable
public fun HugStopButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    Box(
        modifier = modifier
            .clip(HugMunTheme.shapes.primaryAction)
            .background(colors.clay.copy(alpha = STOP_FILL_ALPHA))
            .border(
                border = BorderStroke(dimens.hairline * 2, SolidColor(colors.clay)),
                shape = HugMunTheme.shapes.primaryAction,
            )
            .heightIn(min = dimens.primaryActionHeight)
            .fillMaxWidth()
            .foundationClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceL),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HugMunTheme.type.titleM, color = colors.clay, textAlign = TextAlign.Center)
    }
}

// --- Modifiers ---------------------------------------------------------------------

/** Shifts a layout downward without affecting the space it occupies. */
private fun Modifier.offsetY(offset: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, offset.roundToPx())
    }
}

/**
 * A warm, soft shadow drawn as a blurred rounded rectangle rather than via `Modifier
 * .shadow`, so its colour is the accent rather than neutral black. A grey shadow under a
 * terracotta control looks like dirt; a shadow tinted with the fill looks like light.
 */
private fun Modifier.softShadow(color: Color, alpha: Float, offsetY: Dp, blur: Dp, shape: CornerBasedShape): Modifier =
    drawBehind {
        if (alpha <= 0f) return@drawBehind
        val blurPx = blur.toPx()
        val steps = SHADOW_STEPS
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            val spread = blurPx * fraction
            val stepAlpha = alpha * (1f - fraction) / steps
            val radius = shape.topStart.toPx(size, this)
            drawRoundRect(
                color = color.copy(alpha = stepAlpha),
                topLeft = androidx.compose.ui.geometry.Offset(-spread / 2f, offsetY.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width + spread, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + spread / 2f),
            )
        }
    }

/** The 1 dp inner highlight along the top edge that makes the fill read as enamel. */
private fun Modifier.enamelHighlight(enabled: Boolean, shape: CornerBasedShape): Modifier = drawBehind {
    if (!enabled) return@drawBehind
    val radius = shape.topStart.toPx(size, this)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = HIGHLIGHT_ALPHA),
                HIGHLIGHT_STOP to Color.Transparent,
            ),
            startY = 0f,
            endY = size.height,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
    )
}

private val PRESS_TRAVEL = 2.dp
private const val PRESS_DARKEN = 0.06f
private const val SHADOW_ALPHA = 0.18f
private const val SHADOW_STEPS = 6
private const val HIGHLIGHT_ALPHA = 0.28f
private const val HIGHLIGHT_STOP = 0.22f
private const val SUPPORTING_ALPHA = 0.82f
private const val STOP_FILL_ALPHA = 0.08f
