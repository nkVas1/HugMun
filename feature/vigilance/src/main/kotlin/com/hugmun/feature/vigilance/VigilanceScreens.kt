/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.vigilance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugEvidenceBadge
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugPrimaryButton
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSecondaryButton
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.component.RavenWatermark
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.domain.StoredVigilanceSession
import com.hugmun.core.model.Practice
import kotlin.math.roundToInt

/**
 * What happens, explained before it happens.
 *
 * Always shown, and not skippable by a setting. A task that begins with a bright flash
 * and no warning is disorienting for anyone and frightening for someone who is already
 * anxious about their memory. The thirty seconds this costs are worth more than they
 * take.
 */
@Composable
public fun VigilanceIntroScreen(
    onBegin: () -> Unit,
    onOpenEvidence: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    HugScreen(modifier = modifier) {
        HugScreenTitle(
            text = Practice.VIGILANCE.displayName,
            supporting = Practice.VIGILANCE.subtitle,
        )

        HugCard {
            Text(text = "Как это устроено", style = HugMunTheme.type.titleM, color = colors.ink)
            Text(
                text = "В центре на короткое время появится птица — ворон или сова. " +
                    "Одновременно где-то по краю мелькнёт метка. Нужно заметить обе вещи.",
                style = HugMunTheme.type.bodyL,
                color = colors.ink,
            )
            Text(
                text = "Сначала вы выберете птицу, потом покажете, где была метка. " +
                    "Спешить не нужно: время на ответ не ограничено. Меняется только то, " +
                    "насколько коротко показывают картинку.",
                style = HugMunTheme.type.bodyL,
                color = colors.ink,
            )
            Text(
                text = "Смотрите в центр экрана и не переводите взгляд — метку нужно " +
                    "заметить боковым зрением. Именно это и тренируется.",
                style = HugMunTheme.type.bodyM,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = dimens.spaceXs),
            )
        }

        HugNote(
            text = "Картинку будут показывать всё короче, пока вы не начнёте ошибаться " +
                "примерно в одном случае из четырёх. Ошибки здесь — не провал, а то, " +
                "как измеряется ваш порог. Занятие без единой ошибки ничего бы не измерило.",
            tone = NoteTone.Informational,
        )

        HugEvidenceBadge(tier = Practice.VIGILANCE.tier, onClick = onOpenEvidence)

        HugPrimaryButton(text = "Начать занятие", supportingText = "около 12 минут", onClick = onBegin)
        HugSecondaryButton(text = "Не сейчас", onClick = onCancel)
    }
}

/**
 * The result.
 *
 * One number, what it means, and everything that qualifies it. The qualifications are
 * not in small grey text at the bottom — a caveat nobody reads has not been disclosed.
 */
/**
 * What the result screen needs to know.
 *
 * A view type rather than the engine's own result, because the screen is reached again
 * after process death and must then render what was *stored* — which is also exactly
 * what a clinician would see in an export. Rendering from memory in one case and from
 * disk in another is how the two quietly diverge.
 */
public data class VigilanceResultView(
    public val thresholdMillis: Double?,
    public val isFloorLimited: Boolean,
    public val isQualityAcceptable: Boolean,
    public val scoredTrials: Int,
    public val correctTrials: Int,
    public val discardedTrials: Int,
    public val reversalsUsed: Int,
    public val accuracy: Double,
    public val refreshHz: Double,
) {
    public companion object {
        public fun from(session: StoredVigilanceSession): VigilanceResultView = VigilanceResultView(
            thresholdMillis = session.thresholdMillis,
            isFloorLimited = session.isFloorLimited,
            isQualityAcceptable = session.isQualityAcceptable,
            scoredTrials = session.scoredTrials,
            correctTrials = session.correctTrials,
            discardedTrials = session.discardedTrials,
            reversalsUsed = session.reversalsUsed,
            accuracy = session.accuracy,
            refreshHz = session.refreshHz,
        )
    }
}

@Composable
public fun VigilanceResultScreen(
    result: VigilanceResultView?,
    previousThresholdMillis: Double?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    HugScreen(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RavenWatermark()
        }

        HugScreenTitle(text = "Занятие завершено")

        if (result?.thresholdMillis == null) {
            HugCard {
                Text(
                    text = "В этот раз замера не получилось — занятие закончилось слишком рано, " +
                        "чтобы посчитать порог. Ничего страшного: занятие всё равно засчитано.",
                    style = HugMunTheme.type.bodyL,
                    color = colors.ink,
                )
            }
        } else {
            HugCard {
                Text(text = "Ваш порог", style = HugMunTheme.type.label, color = colors.inkMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = result.thresholdMillis.roundToInt().toString(),
                        style = HugMunTheme.type.numericXL,
                        color = colors.ink,
                    )
                    Text(
                        text = " мс",
                        style = HugMunTheme.type.titleM,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text(
                    text = "Столько времени вам хватало, чтобы правильно узнать обе картинки " +
                        "примерно в трёх случаях из четырёх.",
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkMuted,
                )

                if (previousThresholdMillis != null) {
                    Text(
                        text = "В прошлый раз — ${previousThresholdMillis.roundToInt()} мс.",
                        style = HugMunTheme.type.bodyM,
                        color = colors.ink,
                        modifier = Modifier.padding(top = dimens.spaceS),
                    )
                } else {
                    Text(
                        text = "Это ваш первый замер. С ним будем сравнивать дальше.",
                        style = HugMunTheme.type.bodyM,
                        color = colors.ink,
                        modifier = Modifier.padding(top = dimens.spaceS),
                    )
                }
            }

            if (result.isFloorLimited) {
                HugNote(
                    text = "Ваш результат упёрся в возможности экрана: короче он показать не " +
                        "может. Настоящий порог, возможно, ещё меньше.",
                    tone = NoteTone.Informational,
                )
            }

            if (!result.isQualityAcceptable) {
                HugNote(
                    text = "Экран не успевал показывать картинку вовремя в части проб, поэтому " +
                        "к этому числу стоит отнестись осторожно. Такие пробы не учитывались.",
                    tone = NoteTone.Caution,
                )
            }

            SessionDetail(result)
        }

        // The sentence the whole product depends on. Never abbreviated, never moved to a
        // secondary screen. See docs/research/05-measurement.md §5.
        HugNote(
            text = "Улучшение в самом упражнении ожидаемо и само по себе ещё не означает, " +
                "что улучшились память или мышление.",
        )

        HugPrimaryButton(text = "Готово", onClick = onDone)
    }
}

@Composable
private fun SessionDetail(result: VigilanceResultView) {
    val colors = HugMunTheme.colors
    HugCard {
        Text(text = "Подробности занятия", style = HugMunTheme.type.label, color = colors.inkMuted)
        DetailRow("Проб засчитано", result.scoredTrials.toString())
        DetailRow("Из них верно", result.correctTrials.toString())
        DetailRow("Точность", "${(result.accuracy * PERCENT).roundToInt()} %")
        DetailRow("Разворотов лестницы", result.reversalsUsed.toString())
        if (result.discardedTrials > 0) {
            DetailRow("Отброшено из-за экрана", result.discardedTrials.toString())
        }
        DetailRow("Частота экрана", "${result.refreshHz.roundToInt()} Гц")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = HugMunTheme.type.bodyM, color = HugMunTheme.colors.inkMuted)
        Text(
            text = value,
            style = HugMunTheme.type.numericM,
            color = HugMunTheme.colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

private const val PERCENT = 100.0
