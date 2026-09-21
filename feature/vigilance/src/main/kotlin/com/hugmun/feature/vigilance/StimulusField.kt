/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.vigilance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.hugmun.engine.psychophysics.CentralFigure
import com.hugmun.engine.psychophysics.PeripheralMarker
import com.hugmun.engine.psychophysics.UfovTrialSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The stimulus field.
 *
 * Everything here is drawn rather than composed from widgets, for two reasons: a widget
 * tree cannot be guaranteed to appear within a single frame, and the field must have no
 * chrome at all. A psychophysical stimulus sits on a neutral ground; rounded corners,
 * tints and shadows around it would add luminance and contour cues the measurement does
 * not account for.
 */

/** Which part of the trial is on screen. */
public enum class TrialPhase {
    BLANK,
    FIXATION,
    STIMULUS,
    MASK,
}

@Composable
internal fun StimulusField(
    phase: TrialPhase,
    spec: UfovTrialSpec?,
    ink: Color,
    ground: Color,
    modifier: Modifier = Modifier,
) {
    // The mask is regenerated per trial, not per frame: a mask that shimmers would be a
    // second, uncontrolled stimulus.
    val maskSeed = remember(spec?.index) { spec?.index ?: 0 }

    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = ground)
            when (phase) {
                TrialPhase.BLANK -> Unit
                TrialPhase.FIXATION -> drawFixation(ink)
                TrialPhase.STIMULUS -> spec?.let { drawStimulus(it, ink) }
                TrialPhase.MASK -> drawMask(ink, maskSeed)
            }
        }
    }
}

private fun DrawScope.drawFixation(ink: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val arm = size.minDimension * FIXATION_ARM_FRACTION
    val stroke = size.minDimension * FIXATION_STROKE_FRACTION

    drawLine(ink, Offset(centre.x - arm, centre.y), Offset(centre.x + arm, centre.y), stroke)
    drawLine(ink, Offset(centre.x, centre.y - arm), Offset(centre.x, centre.y + arm), stroke)
}

private fun DrawScope.drawStimulus(spec: UfovTrialSpec, ink: Color) {
    drawCentralBox(ink)
    drawCentralFigure(spec.centralFigure, ink)
    spec.distractors.forEach { drawMarker(it, ink, isTarget = false) }
    spec.target?.let { drawMarker(it, ink, isTarget = true) }
}

/**
 * The box around the central figure.
 *
 * Present in the classical paradigm and worth keeping: it tells the eye where the central
 * task lives without the participant having to search for it, which is what keeps the
 * peripheral subtask the thing that is actually being loaded.
 */
private fun DrawScope.drawCentralBox(ink: Color) {
    val side = size.minDimension * CENTRAL_BOX_FRACTION
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawRect(
        color = ink,
        topLeft = Offset(centre.x - side / 2f, centre.y - side / 2f),
        size = Size(side, side),
        style = Stroke(width = size.minDimension * BOX_STROKE_FRACTION),
    )
}

private fun DrawScope.drawCentralFigure(figure: CentralFigure, ink: Color) {
    val box = size.minDimension * CENTRAL_BOX_FRACTION
    val figureSize = box * FIGURE_FILL_FRACTION
    val centre = Offset(size.width / 2f, size.height / 2f)

    val path = when (figure) {
        CentralFigure.RAVEN -> ravenFigurePath(figureSize)
        CentralFigure.OWL -> owlFigurePath(figureSize)
    }

    translate(left = centre.x - figureSize / 2f, top = centre.y - figureSize / 2f) {
        drawPath(path = path, color = ink, style = Fill)
    }
}

private fun DrawScope.drawMarker(marker: PeripheralMarker, ink: Color, isTarget: Boolean) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val fieldRadius = size.minDimension * FIELD_RADIUS_FRACTION
    val radius = fieldRadius * marker.eccentricity.normalisedRadius

    // Direction 0 is straight up, increasing clockwise, which is how the response ring
    // is laid out. Screen y grows downward, hence the negation.
    val radians = (marker.direction.degrees - QUARTER_TURN_DEGREES) * PI.toFloat() / HALF_TURN_DEGREES
    val position = Offset(
        x = centre.x + radius * cos(radians),
        y = centre.y + radius * sin(radians),
    )

    val markerRadius = size.minDimension * MARKER_RADIUS_FRACTION

    if (isTarget) {
        // The target is a filled disc inside a ring; distractors are rings alone. The
        // difference is in the interior, not in size or position, so the search cannot be
        // solved by peripheral size cues.
        drawCircle(
            color = ink,
            radius = markerRadius,
            center = position,
            style = Stroke(width = markerRadius * TARGET_RING_STROKE),
        )
        drawCircle(color = ink, radius = markerRadius * TARGET_CORE_FRACTION, center = position)
    } else {
        drawCircle(
            color = ink,
            radius = markerRadius,
            center = position,
            style = Stroke(width = markerRadius * DISTRACTOR_STROKE),
        )
    }
}

/**
 * A high-spatial-frequency mask over the whole field.
 *
 * Not decoration. Without it, the afterimage keeps the figure legible after the stimulus
 * is gone, the presentation duration stops being the limiting variable, and the measured
 * threshold collapses toward the display floor. This is the difference between measuring
 * perception and measuring persistence.
 */
private fun DrawScope.drawMask(ink: Color, seed: Int) {
    val random = Random(seed)
    val cell = size.minDimension * MASK_CELL_FRACTION
    val columns = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if (random.nextBoolean()) continue
            drawRect(
                color = ink,
                topLeft = Offset(column * cell, row * cell),
                size = Size(cell, cell),
                alpha = MASK_ALPHA,
            )
        }
    }
}

/**
 * The raven, in flight: a wide horizontal silhouette.
 *
 * The two figures are discriminated by gross orientation — wide-horizontal against
 * compact-upright — because that is what survives a 25 ms presentation. Both are drawn
 * into the same square at the same nominal size, so overall extent carries no
 * information; the remaining difference in filled area between them is small and
 * constant across trials, and it is not what the staircase moves on.
 */
private fun ravenFigurePath(extent: Float): Path {
    val s = extent / REFERENCE_BOX
    fun p(v: Float) = v * s
    return Path().apply {
        moveTo(p(4f), p(50f))
        cubicTo(p(11f), p(45f), p(16f), p(42f), p(23f), p(41f))
        cubicTo(p(30f), p(31f), p(38f), p(18f), p(50f), p(9f))
        cubicTo(p(56f), p(5f), p(62f), p(3f), p(67f), p(3f))
        lineTo(p(61f), p(13f))
        lineTo(p(66f), p(14f))
        lineTo(p(57f), p(26f))
        cubicTo(p(54f), p(31f), p(51f), p(36f), p(49f), p(41f))
        cubicTo(p(60f), p(42f), p(72f), p(45f), p(80f), p(49f))
        lineTo(p(97f), p(52f))
        lineTo(p(96f), p(58f))
        lineTo(p(78f), p(58f))
        cubicTo(p(68f), p(61f), p(56f), p(62f), p(46f), p(61f))
        cubicTo(p(48f), p(70f), p(50f), p(82f), p(48f), p(94f))
        lineTo(p(41f), p(84f))
        lineTo(p(37f), p(90f))
        cubicTo(p(34f), p(78f), p(32f), p(68f), p(31f), p(60f))
        cubicTo(p(22f), p(58f), p(12f), p(55f), p(4f), p(50f))
        close()
    }
}

/** The owl, perched: a compact upright silhouette with ear tufts. */
private fun owlFigurePath(extent: Float): Path {
    val s = extent / REFERENCE_BOX
    fun p(v: Float) = v * s
    return Path().apply {
        // Left ear tuft.
        moveTo(p(26f), p(18f))
        lineTo(p(34f), p(4f))
        lineTo(p(40f), p(15f))
        // Crown across to the right tuft.
        cubicTo(p(46f), p(13f), p(54f), p(13f), p(60f), p(15f))
        lineTo(p(66f), p(4f))
        lineTo(p(74f), p(18f))
        // Right side of the head and body, widening to the base.
        cubicTo(p(82f), p(27f), p(85f), p(40f), p(84f), p(52f))
        cubicTo(p(83f), p(70f), p(76f), p(86f), p(64f), p(93f))
        // Feet.
        lineTo(p(66f), p(97f))
        lineTo(p(56f), p(97f))
        lineTo(p(56f), p(95f))
        lineTo(p(44f), p(95f))
        lineTo(p(44f), p(97f))
        lineTo(p(34f), p(97f))
        lineTo(p(36f), p(93f))
        // Left side back up to the tuft.
        cubicTo(p(24f), p(86f), p(17f), p(70f), p(16f), p(52f))
        cubicTo(p(15f), p(40f), p(18f), p(27f), p(26f), p(18f))
        close()
    }
}

private const val REFERENCE_BOX = 100f
private const val QUARTER_TURN_DEGREES = 90f
private const val HALF_TURN_DEGREES = 180f

private const val FIXATION_ARM_FRACTION = 0.035f
private const val FIXATION_STROKE_FRACTION = 0.008f
private const val CENTRAL_BOX_FRACTION = 0.22f
private const val BOX_STROKE_FRACTION = 0.005f
private const val FIGURE_FILL_FRACTION = 0.72f
private const val FIELD_RADIUS_FRACTION = 0.46f
private const val MARKER_RADIUS_FRACTION = 0.032f
private const val TARGET_RING_STROKE = 0.30f
private const val TARGET_CORE_FRACTION = 0.45f
private const val DISTRACTOR_STROKE = 0.30f
private const val MASK_CELL_FRACTION = 0.028f
private const val MASK_ALPHA = 0.85f
