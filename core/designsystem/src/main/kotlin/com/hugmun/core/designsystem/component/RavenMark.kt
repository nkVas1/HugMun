/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme

/**
 * The raven mark — one of exactly four places the mythology is allowed to surface.
 *
 * The brief asked for a thin atmospheric thread rather than a theme, so the budget is:
 * this mark as the launcher icon and splash, the same mark as a near-invisible watermark
 * on the completion screen, the names of the two long-term indices, and the About screen.
 * Nowhere else. A fifth appearance turns a thread into a mascot.
 *
 * Drawn as a single filled path in the spirit of a sumi-e stroke: one confident gesture,
 * no outline, no gradient, no eye. A bird in the moment of the dawn flight described in
 * Grímnismál 20 — out over the wide earth, back by breakfast.
 *
 * The path is defined in a 100 × 100 space and scaled, so it stays crisp at any size and
 * needs no vector asset.
 */
@Composable
public fun RavenMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = HugMunTheme.colors.ravenInk,
    alpha: Float = 1f,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        drawRaven(color = color, alpha = alpha)
    }
}

/**
 * The same mark, at watermark opacity, for the end of a completed session.
 *
 * Effectively subliminal — present enough to give the screen a centre of gravity, faint
 * enough that nobody would describe the app as "the one with the bird".
 */
@Composable
public fun RavenWatermark(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    RavenMark(
        modifier = modifier,
        size = size,
        color = HugMunTheme.colors.ink,
        alpha = WATERMARK_ALPHA,
    )
}

/**
 * Two marks, offset and mirrored: Huginn ahead, Muninn following.
 *
 * Used only on the About screen, beside the Grímnismál stanza.
 */
@Composable
public fun RavenPair(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    color: Color = HugMunTheme.colors.ravenInk,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        val unit = this.size.minDimension
        drawRavenPath(color = color, alpha = PAIR_LEAD_ALPHA, scale = unit * PAIR_LEAD_SCALE, dx = 0f, dy = 0f)
        drawRavenPath(
            color = color,
            alpha = PAIR_TRAIL_ALPHA,
            scale = unit * PAIR_TRAIL_SCALE,
            dx = unit * PAIR_TRAIL_DX,
            dy = unit * PAIR_TRAIL_DY,
        )
    }
}

private fun DrawScope.drawRaven(color: Color, alpha: Float) {
    drawRavenPath(color = color, alpha = alpha, scale = size.minDimension, dx = 0f, dy = 0f)
}

private fun DrawScope.drawRavenPath(
    color: Color,
    alpha: Float,
    scale: Float,
    dx: Float,
    dy: Float,
) {
    val path = ravenPath(scale).apply { translate(Offset(dx, dy)) }
    drawPath(path = path, color = color, alpha = alpha, style = Fill)
}

/**
 * The silhouette.
 *
 * Coordinates are hand-placed in a 100 × 100 box: beak at the left, wings swept up and
 * back, a wedge tail at the right. The asymmetry between the two wings is deliberate —
 * a perfectly symmetric bird reads as a logo, an asymmetric one reads as flight.
 */
private fun ravenPath(scale: Float): Path {
    val s = scale / REFERENCE_BOX
    fun x(v: Float) = v * s
    fun y(v: Float) = v * s

    return Path().apply {
        // Beak, then the crown of the head.
        moveTo(x(4f), y(50f))
        cubicTo(x(11f), y(45f), x(16f), y(42f), x(23f), y(41f))
        // Leading edge of the upper wing, sweeping back and up.
        cubicTo(x(30f), y(31f), x(38f), y(18f), x(50f), y(9f))
        cubicTo(x(56f), y(5f), x(62f), y(3f), x(67f), y(3f))
        // Two feather notches at the wing tip — enough to read as primaries, not a study.
        lineTo(x(61f), y(13f))
        lineTo(x(66f), y(14f))
        lineTo(x(57f), y(26f))
        // Trailing edge back down to the shoulder.
        cubicTo(x(54f), y(31f), x(51f), y(36f), x(49f), y(41f))
        // Along the back to the tail.
        cubicTo(x(60f), y(42f), x(72f), y(45f), x(80f), y(49f))
        // Wedge tail.
        lineTo(x(97f), y(52f))
        lineTo(x(96f), y(58f))
        lineTo(x(78f), y(58f))
        // Belly, forward again.
        cubicTo(x(68f), y(61f), x(56f), y(62f), x(46f), y(61f))
        // The near wing, dropped below the body.
        cubicTo(x(48f), y(70f), x(50f), y(82f), x(48f), y(94f))
        lineTo(x(41f), y(84f))
        lineTo(x(37f), y(90f))
        cubicTo(x(34f), y(78f), x(32f), y(68f), x(31f), y(60f))
        // Throat, closing back at the beak.
        cubicTo(x(22f), y(58f), x(12f), y(55f), x(4f), y(50f))
        close()
    }
}

/**
 * A hairline version of the mark, for places where a filled silhouette would be too
 * heavy — currently only the splash, where it is drawn over a light field.
 */
@Composable
public fun RavenOutline(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = HugMunTheme.colors.ravenInk,
    strokeWidth: Dp = 1.5.dp,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val strokePx = remember(strokeWidth, density) { with(density) { strokeWidth.toPx() } }

    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        drawPath(
            path = ravenPath(this.size.minDimension),
            color = color,
            style = Stroke(width = strokePx),
        )
    }
}

/** Reference box the coordinates above are expressed in. */
private const val REFERENCE_BOX = 100f

private const val WATERMARK_ALPHA = 0.04f
private const val PAIR_LEAD_ALPHA = 0.9f
private const val PAIR_TRAIL_ALPHA = 0.45f
private const val PAIR_LEAD_SCALE = 0.62f
private const val PAIR_TRAIL_SCALE = 0.44f
private const val PAIR_TRAIL_DX = 0.42f
private const val PAIR_TRAIL_DY = 0.46f
