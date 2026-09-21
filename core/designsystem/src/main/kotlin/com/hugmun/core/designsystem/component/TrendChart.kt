/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** One measurement on the trend. */
public data class TrendPoint(
    public val value: Double,
    /** Short label for the axis, e.g. "12.09". Only some points are labelled. */
    public val label: String,
    /** Drawn hollow. Used for sessions the app does not fully trust. */
    public val isProvisional: Boolean = false,
)

/**
 * The person's own usual variation, as a corridor around their baseline.
 *
 * Drawing the uncertainty is the whole point of this chart. A line without it invites
 * the reader to see a trend in what is almost always noise — and this particular reader
 * is looking at it specifically because he is worried.
 */
public data class TrendBand(public val baseline: Double, public val halfWidth: Double)

/**
 * An engraved trend chart.
 *
 * This is where the «Обсерватория» influence is allowed in, and only here: hairline
 * rules, real tick marks, tabular figures, an honest scale. No gradient fill, no glow,
 * no rounded "friendly" bars that misrepresent the axis.
 *
 * @param lowerIsBetter true for the «Зоркость» threshold. Changes the wording, never the
 *   direction of the axis — flipping the axis to make progress point upward would be a
 *   small lie that compounds.
 */
@Composable
public fun HugTrendChart(
    points: List<TrendPoint>,
    band: TrendBand?,
    unitLabel: String,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = DEFAULT_HEIGHT,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    if (points.isEmpty()) {
        HugNote(text = "Пока нечего показывать — нужно хотя бы одно занятие.", modifier = modifier)
        return
    }

    val description = describe(points, band, unitLabel, lowerIsBetter)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        ) {
            drawChart(
                points = points,
                band = band,
                palette = ChartPalette(
                    axis = colors.surfaceEdge,
                    bandFill = colors.skyWash,
                    bandEdge = colors.sky,
                    line = colors.dawn,
                    dot = colors.dawn,
                    surface = colors.surfaceRaised,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = points.first().label,
                style = HugMunTheme.type.label,
                color = colors.inkFaint,
            )
            Text(
                text = "$unitLabel · ${if (lowerIsBetter) "меньше — лучше" else "больше — лучше"}",
                style = HugMunTheme.type.label,
                color = colors.inkFaint,
            )
            Text(
                text = points.last().label,
                style = HugMunTheme.type.label,
                color = colors.inkFaint,
            )
        }

        if (band != null) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                modifier = Modifier.padding(top = dimens.spaceXs),
            ) {
                Text(
                    text = "Полоса — ваша обычная изменчивость " +
                        "(${(band.baseline - band.halfWidth).roundToInt()}–" +
                        "${(band.baseline + band.halfWidth).roundToInt()} $unitLabel).",
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

/** The handful of colours the chart draws with, so the geometry is not buried in them. */
private data class ChartPalette(
    val axis: androidx.compose.ui.graphics.Color,
    val bandFill: androidx.compose.ui.graphics.Color,
    val bandEdge: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val dot: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
)

/** Maps a data value onto the vertical extent of the plot. */
private class VerticalScale(
    private val minimum: Double,
    private val maximum: Double,
    private val top: Float,
    private val bottom: Float,
) {
    fun map(value: Double): Float {
        val fraction = (value - minimum) / (maximum - minimum)
        return (bottom - (bottom - top) * fraction).toFloat()
    }
}

private fun DrawScope.drawChart(points: List<TrendPoint>, band: TrendBand?, palette: ChartPalette) {
    val plotLeft = AXIS_GUTTER_PX
    val plotRight = size.width
    val plotBottom = size.height - AXIS_GUTTER_PX
    val scale = verticalScaleFor(points, band, top = 0f, bottom = plotBottom)

    fun x(index: Int): Float = if (points.size == 1) {
        (plotLeft + plotRight) / 2f
    } else {
        plotLeft + (plotRight - plotLeft) * index / (points.size - 1f)
    }

    if (band != null) {
        drawUsualVariation(band, scale, plotLeft, plotRight, palette)
    }
    drawAxes(points.size, ::x, plotLeft, plotRight, plotBottom, palette.axis)
    drawSeries(points, scale, ::x, palette)
}

/**
 * The scale includes the band as well as the data.
 *
 * If the corridor were allowed to fall outside the plot, a point inside it could appear
 * to sit at the very edge of the chart — which reads as alarming for a value that is
 * entirely ordinary.
 */
private fun verticalScaleFor(points: List<TrendPoint>, band: TrendBand?, top: Float, bottom: Float): VerticalScale {
    val candidates = buildList {
        addAll(points.map { it.value })
        if (band != null) {
            add(band.baseline - band.halfWidth)
            add(band.baseline + band.halfWidth)
        }
    }
    val rawMin = candidates.min()
    val rawMax = candidates.max()
    val padding = ((rawMax - rawMin).takeIf { it > 0.0 } ?: 1.0) * SCALE_PADDING
    return VerticalScale(rawMin - padding, rawMax + padding, top, bottom)
}

private fun DrawScope.drawUsualVariation(
    band: TrendBand,
    scale: VerticalScale,
    left: Float,
    right: Float,
    palette: ChartPalette,
) {
    val top = scale.map(band.baseline + band.halfWidth)
    val bottom = scale.map(band.baseline - band.halfWidth)
    drawRect(
        color = palette.bandFill,
        topLeft = Offset(left, minOf(top, bottom)),
        size = Size(right - left, abs(bottom - top)),
    )
    // The baseline is a reference rather than a measurement, so it is dashed.
    drawLine(
        color = palette.bandEdge,
        start = Offset(left, scale.map(band.baseline)),
        end = Offset(right, scale.map(band.baseline)),
        strokeWidth = HAIRLINE_PX,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX, DASH_OFF_PX)),
    )
}

/** Two hairlines and real tick marks. Nothing heavier than the data itself. */
private fun DrawScope.drawAxes(
    count: Int,
    x: (Int) -> Float,
    left: Float,
    right: Float,
    bottom: Float,
    axis: androidx.compose.ui.graphics.Color,
) {
    drawLine(axis, Offset(left, 0f), Offset(left, bottom), HAIRLINE_PX)
    drawLine(axis, Offset(left, bottom), Offset(right, bottom), HAIRLINE_PX)
    repeat(count) { index ->
        val tickX = x(index)
        drawLine(axis, Offset(tickX, bottom), Offset(tickX, bottom + TICK_PX), HAIRLINE_PX)
    }
}

private fun DrawScope.drawSeries(
    points: List<TrendPoint>,
    scale: VerticalScale,
    x: (Int) -> Float,
    palette: ChartPalette,
) {
    val path = Path()
    points.forEachIndexed { index, point ->
        val px = x(index)
        val py = scale.map(point.value)
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path = path, color = palette.line, style = Stroke(width = LINE_PX))

    points.forEachIndexed { index, point ->
        val centre = Offset(x(index), scale.map(point.value))
        if (point.isProvisional) {
            // Hollow: the app does not fully trust this session, and says so in shape
            // rather than only in colour.
            drawCircle(color = palette.surface, radius = DOT_PX, center = centre)
            drawCircle(color = palette.dot, radius = DOT_PX, center = centre, style = Stroke(HAIRLINE_PX * 2))
        } else {
            drawCircle(color = palette.dot, radius = DOT_PX, center = centre)
        }
    }
}

/**
 * The spoken description.
 *
 * A chart that is invisible to a screen reader is a chart that excludes exactly the
 * users this app is for. The summary states the direction, the latest value, and
 * whether it lies inside the usual-variation band.
 */
private fun describe(points: List<TrendPoint>, band: TrendBand?, unitLabel: String, lowerIsBetter: Boolean): String {
    val latest = points.last().value
    val first = points.first().value
    val direction = when {
        points.size < 2 -> "одно измерение"
        latest < first && lowerIsBetter -> "в целом улучшение"
        latest > first && !lowerIsBetter -> "в целом улучшение"
        latest == first -> "без изменений"
        else -> "в целом ухудшение"
    }

    val inBand = band?.let { abs(latest - it.baseline) <= it.halfWidth }
    val bandPhrase = when (inBand) {
        true -> "Последнее значение в пределах вашей обычной изменчивости."
        false -> "Последнее значение выходит за пределы вашей обычной изменчивости."
        null -> "Полосы обычной изменчивости пока нет: мало измерений."
    }

    return "График из ${points.size} измерений, $direction. " +
        "Последнее значение ${latest.roundToInt()} $unitLabel. $bandPhrase"
}

private val DEFAULT_HEIGHT = 180.dp
private const val AXIS_GUTTER_PX = 8f
private const val HAIRLINE_PX = 1.5f
private const val LINE_PX = 2.5f
private const val DOT_PX = 5f
private const val TICK_PX = 4f
private const val DASH_ON_PX = 6f
private const val DASH_OFF_PX = 6f
private const val SCALE_PADDING = 0.12
