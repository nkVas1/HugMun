/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugEvidenceBadge
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugPrimaryButton
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSectionHeader
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.PlanReason
import com.hugmun.core.model.PlannedPractice
import com.hugmun.core.model.Practice
import kotlin.math.roundToInt
import kotlinx.datetime.Month

/**
 * The day's plan.
 *
 * Deliberately almost empty. There is one thing to do, a sentence saying why, and the
 * evidence behind it. No dashboard, no streak, no ring to close. On a day with nothing
 * scheduled the screen says so and stops, because rest is part of the protocol and
 * inventing filler would train the wrong habit.
 */
@Composable
public fun HomeScreen(
    viewModel: HomeViewModel,
    onEnrolled: () -> Unit,
    onStartPractice: (Practice) -> Unit,
    onOpenOptional: (Practice) -> Unit,
    onOpenEvidence: (Practice) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HugScreen(modifier = modifier, bottomBar = bottomBar) {
        HugScreenTitle(
            text = greeting(state),
            supporting = null,
        )

        val plan = state.plan
        when {
            state.isLoading -> Unit

            plan == null || !plan.isEnrolled -> FirstRunCard(
                onBegin = {
                    viewModel.enrolIfNeeded {
                        // Reminders only become meaningful once there is a schedule, so
                        // this is where the permission is asked for.
                        onEnrolled()
                        onStartPractice(Practice.VIGILANCE)
                    }
                },
            )

            !plan.hasAnythingDue -> NothingDueCard(plan.entries.firstOrNull())

            else -> {
                HugSectionHeader(text = stringResource(R.string.home_plan_title))
                plan.due.forEach { entry ->
                    PracticeCard(
                        entry = entry,
                        onStart = { onStartPractice(entry.practice) },
                        onOpenEvidence = { onOpenEvidence(entry.practice) },
                    )
                }
            }
        }

        if (plan?.hasCompletedABoosterBlock == true) {
            HugNote(
                text = "Вы прошли повторный курс полностью. Именно повторные курсы, а не только " +
                    "первые занятия, дали результат в том исследовании, на которое мы опираемся.",
                tone = NoteTone.Informational,
            )
        }

        state.lastSession?.thresholdMillis?.let { millis ->
            LastResultLine(millis)
        }

        if (plan?.isEnrolled == true) {
            OptionalPractices(onOpen = onOpenOptional)
        }
    }
}

@Composable
private fun FirstRunCard(onBegin: () -> Unit) {
    HugCard {
        Text(
            text = "Начнём с «Зоркости»",
            style = HugMunTheme.type.titleL,
            color = HugMunTheme.colors.ink,
        )
        Text(
            text = "Это упражнение на скорость обработки — то самое, которое изучали в " +
                "исследовании ACTIVE. Первое занятие займёт около пятнадцати минут вместе " +
                "с объяснением.",
            style = HugMunTheme.type.bodyL,
            color = HugMunTheme.colors.inkMuted,
        )
        HugEvidenceBadge(tier = Practice.VIGILANCE.tier)
        HugPrimaryButton(
            text = "Начать",
            supportingText = "15 минут",
            onClick = onBegin,
            modifier = Modifier.padding(top = HugMunTheme.dimens.spaceS),
        )
    }
}

@Composable
private fun NothingDueCard(next: PlannedPractice?) {
    HugCard {
        Text(
            text = stringResource(R.string.home_nothing_due),
            style = HugMunTheme.type.bodyL,
            color = HugMunTheme.colors.ink,
        )
        if (next != null) {
            Text(
                text = "Следующее занятие — ${formatDate(next)}.",
                style = HugMunTheme.type.bodyM,
                color = HugMunTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun PracticeCard(entry: PlannedPractice, onStart: () -> Unit, onOpenEvidence: () -> Unit) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    HugCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                Text(
                    text = entry.practice.displayName,
                    style = HugMunTheme.type.titleL,
                    color = colors.ink,
                )
                Text(
                    text = entry.practice.subtitle,
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkMuted,
                )
            }
            Text(
                text = stringResource(R.string.home_minutes, entry.estimated.inWholeMinutes.toInt()),
                style = HugMunTheme.type.numericM,
                color = colors.inkFaint,
            )
        }

        // The reason this is here today. Never blank, by construction.
        Text(
            text = entry.rationale,
            style = HugMunTheme.type.bodyM,
            color = colors.inkMuted,
            modifier = Modifier.padding(top = dimens.spaceXs),
        )

        if (entry.reason == PlanReason.RESCHEDULED) {
            HugNote(
                text = "Вы пропустили прошлое занятие. Я перенёс его на сегодня — навёрстывать " +
                    "ничего не нужно.",
                modifier = Modifier.padding(top = dimens.spaceS),
            )
        }

        HugEvidenceBadge(
            tier = entry.practice.tier,
            onClick = onOpenEvidence,
            modifier = Modifier.padding(top = dimens.spaceS),
        )

        HugPrimaryButton(
            text = stringResource(R.string.home_start),
            onClick = onStart,
            modifier = Modifier.padding(top = dimens.spaceS),
        )
    }
}

/**
 * Practices that are available but not scheduled.
 *
 * Kept visually and textually separate from the plan. The plan is what the evidence
 * says to do today; this is what the user may do if they want to, and the difference
 * should be obvious at a glance rather than buried in a tier badge.
 */
@Composable
private fun OptionalPractices(onOpen: (Practice) -> Unit) {
    val colors = HugMunTheme.colors

    HugSectionHeader(text = "Можно сделать дополнительно")

    HugCard(onClick = { onOpen(Practice.RHYTHM) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Practice.RHYTHM.displayName,
                    style = HugMunTheme.type.titleM,
                    color = colors.ink,
                )
                Text(
                    text = Practice.RHYTHM.subtitle,
                    style = HugMunTheme.type.bodyM,
                    color = colors.inkMuted,
                )
            }
        }
        HugEvidenceBadge(tier = Practice.RHYTHM.tier)
        Text(
            text = "Не входит в расписание: доказательств пока мало, чтобы назначать это " +
                "как часть курса.",
            style = HugMunTheme.type.label,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun LastResultLine(thresholdMillis: Double) {
    Text(
        text = "Прошлый результат — ${thresholdMillis.roundToInt()} мс.",
        style = HugMunTheme.type.bodyM,
        color = HugMunTheme.colors.inkFaint,
    )
}

@Composable
private fun greeting(state: HomeUiState): String {
    val base = when (state.partOfDay) {
        PartOfDay.MORNING -> stringResource(R.string.home_greeting_morning)
        PartOfDay.DAY -> stringResource(R.string.home_greeting_day)
        PartOfDay.EVENING -> stringResource(R.string.home_greeting_evening)
        PartOfDay.NIGHT -> stringResource(R.string.home_greeting_night)
    }
    val name = state.displayName
    return if (name.isNullOrBlank()) base else "$base, $name"
}

private fun formatDate(entry: PlannedPractice): String {
    val date = entry.dueOn
    return "${date.day} ${monthGenitive(date.month)}"
}

/**
 * Russian month names in the genitive case, as "12 сентября" requires.
 *
 * Written out rather than formatted through the platform, because Android's Russian
 * month names come back in the nominative ("сентябрь") and read as a mistake to a native
 * speaker. A date that looks wrong undermines trust in everything else on the screen.
 */
private fun monthGenitive(month: Month): String = when (month) {
    Month.JANUARY -> "января"
    Month.FEBRUARY -> "февраля"
    Month.MARCH -> "марта"
    Month.APRIL -> "апреля"
    Month.MAY -> "мая"
    Month.JUNE -> "июня"
    Month.JULY -> "июля"
    Month.AUGUST -> "августа"
    Month.SEPTEMBER -> "сентября"
    Month.OCTOBER -> "октября"
    Month.NOVEMBER -> "ноября"
    Month.DECEMBER -> "декабря"
}
