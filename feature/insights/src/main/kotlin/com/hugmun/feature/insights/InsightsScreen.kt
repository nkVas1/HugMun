/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSectionHeader
import com.hugmun.core.designsystem.component.HugTrendChart
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.component.TrendBand
import com.hugmun.core.designsystem.component.TrendPoint
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.domain.ReliableChange
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.engine.scheduling.TrainingProtocol
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * «Динамика».
 *
 * The screen exists to answer one question — has anything actually changed? — and to
 * answer "no" convincingly, which is what it will be saying most of the time.
 */
@Composable
public fun InsightsScreen(
    viewModel: InsightsViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HugMunTheme.colors

    HugScreen(modifier = modifier, bottomBar = bottomBar) {
        HugScreenTitle(
            text = "Динамика",
            supporting = "Порог в упражнении «Зоркость»",
        )

        when {
            state.isLoading -> Unit

            state.sessions.isEmpty() -> HugCard {
                Text(
                    text = "Здесь появится ваш график, когда наберётся несколько занятий. " +
                        "Раньше показывать его бессмысленно: по двум точкам нельзя отличить " +
                        "изменение от обычного разброса.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
            }

            else -> {
                HugCard {
                    HugTrendChart(
                        points = state.sessions.map { it.toPoint() },
                        band = state.change?.toBand(),
                        unitLabel = "мс",
                        lowerIsBetter = true,
                    )
                }

                VerdictCard(state.change)

                if (state.excludedForQuality > 0) {
                    HugNote(
                        text = "Занятий, где экран не успевал показывать картинку вовремя: " +
                            "${state.excludedForQuality}. Они видны на графике, но в расчёт " +
                            "не идут.",
                        tone = NoteTone.Caution,
                    )
                }

                HugSectionHeader(text = "Курс")
                ProgressCard(
                    totalSessions = state.totalSessions,
                    phase = state.phase,
                    hasBooster = state.hasCompletedABoosterBlock,
                )

                // The sentence the product depends on. See docs/research/05-measurement.md §5.
                HugNote(
                    text = "Улучшение в самом упражнении ожидаемо: так работает практика. " +
                        "Само по себе оно ещё не означает, что улучшились память или мышление.",
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(change: ReliableChange.Result?) {
    val colors = HugMunTheme.colors

    val (title, body) = when (change?.verdict) {
        null, ReliableChange.Verdict.INSUFFICIENT_DATA ->
            "Пока рано делать выводы" to
                "Нужно хотя бы ${ReliableChange.MIN_OBSERVATIONS + 1} занятий, чтобы понять, " +
                "какой разброс для вас обычный. Без этого любое изменение — просто цифра."

        ReliableChange.Verdict.WITHIN_USUAL_VARIATION ->
            "Без изменений" to
                "Последний результат укладывается в ваш обычный разброс. Это самый частый " +
                "и совершенно нормальный ответ: у всех результат колеблется день ото дня."

        ReliableChange.Verdict.IMPROVED ->
            "Заметное улучшение" to
                "Последний результат вышел за пределы вашего обычного разброса в лучшую " +
                "сторону. Один такой замер — ещё не тенденция; посмотрим на следующий."

        ReliableChange.Verdict.DECLINED ->
            "Результат заметно хуже обычного" to
                "Последний замер вышел за пределы вашего обычного разброса. Так бывает от " +
                "усталости, недосыпа или простуды. Если то же повторится в следующий раз, " +
                "об этом стоит сказать врачу."
    }

    val tone = when (change?.verdict) {
        ReliableChange.Verdict.DECLINED -> NoteTone.Caution
        else -> NoteTone.Neutral
    }

    HugCard {
        Text(text = title, style = HugMunTheme.type.titleM, color = colors.ink)
        Text(text = body, style = HugMunTheme.type.bodyL, color = colors.ink)

        if (change?.isJudged == true) {
            HugNote(
                text = "Считаем по вашим собственным ${change.observationsUsed} прошлым " +
                    "замерам, а не по чужой норме.",
                tone = tone,
            )
        }
    }
}

@Composable
private fun ProgressCard(totalSessions: Int, phase: TrainingProtocol.Phase?, hasBooster: Boolean) {
    val colors = HugMunTheme.colors

    HugCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Занятий пройдено", style = HugMunTheme.type.bodyM, color = colors.inkMuted)
            Text(
                text = totalSessions.toString(),
                style = HugMunTheme.type.numericM,
                color = colors.ink,
                textAlign = TextAlign.End,
            )
        }

        if (phase != null) {
            Text(
                text = TrainingProtocol.specFor(phase).rationale,
                style = HugMunTheme.type.bodyM,
                color = colors.inkMuted,
            )
            if (TrainingProtocol.specFor(phase).provenance == TrainingProtocol.Provenance.EXTRAPOLATION) {
                Text(
                    text = "Этот этап мы добавили сами: в исследовании его не было. " +
                        "Он нужен, чтобы не терять навык между повторными курсами.",
                    style = HugMunTheme.type.label,
                    color = colors.inkFaint,
                )
            }
        }

        if (hasBooster) {
            HugNote(
                text = "Вы прошли повторный курс полностью. В том исследовании разница была " +
                    "именно между людьми, которые прошли повторные курсы, и теми, кто " +
                    "ограничился первым.",
                tone = NoteTone.Informational,
            )
        }
    }
}

private fun StoredVigilanceSession.toPoint(): TrendPoint {
    val date = startedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return TrendPoint(
        value = thresholdMillis ?: 0.0,
        label = "${date.day}.${date.month.ordinal + 1}",
        isProvisional = !isQualityAcceptable,
    )
}

private fun ReliableChange.Result.toBand(): TrendBand? {
    val centre = baseline ?: return null
    val width = bandHalfWidth ?: return null
    return TrendBand(baseline = centre, halfWidth = width)
}
