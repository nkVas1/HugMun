/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hugmun.core.designsystem.component.HugCard
import com.hugmun.core.designsystem.component.HugEvidenceBadge
import com.hugmun.core.designsystem.component.HugEvidenceDetail
import com.hugmun.core.designsystem.component.HugNote
import com.hugmun.core.designsystem.component.HugScreen
import com.hugmun.core.designsystem.component.HugScreenTitle
import com.hugmun.core.designsystem.component.HugSecondaryButton
import com.hugmun.core.designsystem.component.NoteTone
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.EvidenceCatalogue
import com.hugmun.core.model.EvidenceTier
import com.hugmun.core.model.Practice

/**
 * The evidence card for one practice.
 *
 * Reached from the badge on every exercise. The point of it existing at all is that a
 * user who wants to know *why* the app is asking for something can find out in two taps,
 * and what they find will include the limitations rather than only the encouraging part.
 */
@Composable
public fun EvidenceScreen(practice: Practice, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val card = EvidenceCatalogue.cardFor(practice)

    HugScreen(modifier = modifier) {
        HugScreenTitle(
            text = "Что об этом известно",
            supporting = practice.displayName,
        )

        HugEvidenceDetail(card = card)

        TierExplanation(tier = card.tier)

        HugNote(
            text = "Здесь написано, что нашли у участников исследований. Про вас лично " +
                "ни одно исследование ничего сказать не может — и мы не будем делать вид, " +
                "что может.",
        )

        HugSecondaryButton(text = "Назад", onClick = onBack)
    }
}

/**
 * The whole library.
 *
 * Ordered by evidence strength rather than by menu convenience, which makes the shape of
 * what we actually know visible at a glance: one practice at tier A with long-term
 * outcomes, several at B, and one openly experimental.
 */
@Composable
public fun LibraryScreen(
    onOpenPractice: (Practice) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val colors = HugMunTheme.colors

    HugScreen(modifier = modifier, bottomBar = bottomBar) {
        HugScreenTitle(
            text = "Источники",
            supporting = "На чём основано каждое занятие и насколько это надёжно",
        )

        HugNote(
            text = "Упражнения расставлены по силе доказательств, а не по важности в меню. " +
                "Так видно настоящую картину: надёжно изучено немногое.",
            tone = NoteTone.Informational,
        )

        EvidenceCatalogue.all
            .sortedBy { (_, card) -> card.tier.ordinal }
            .forEach { (practice, card) ->
                HugCard(onClick = { onOpenPractice(practice) }) {
                    HugEvidenceBadge(tier = card.tier)
                    Text(
                        text = practice.displayName,
                        style = HugMunTheme.type.titleM,
                        color = colors.ink,
                    )
                    Text(
                        text = card.headline,
                        style = HugMunTheme.type.bodyM,
                        color = colors.inkMuted,
                    )
                }
            }

        HugCard {
            Text(
                text = "Полное досье",
                style = HugMunTheme.type.titleM,
                color = colors.ink,
            )
            Text(
                text = "Разбор всех исследований, включая те, чьи результаты нам не подошли, " +
                    "лежит в открытом виде в репозитории проекта: docs/research. Там же " +
                    "написано, что мы сознательно решили не делать и почему.",
                style = HugMunTheme.type.bodyM,
                color = colors.inkMuted,
            )
        }
    }
}

/** What the tier letter means, spelled out next to the card it applies to. */
@Composable
private fun TierExplanation(tier: EvidenceTier) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    HugCard {
        Text(
            text = "Уровень доказательности ${tier.ordinalLabel}",
            style = HugMunTheme.type.titleM,
            color = colors.ink,
        )
        Text(
            text = tier.explanation,
            style = HugMunTheme.type.bodyM,
            color = colors.ink,
        )

        Column(
            modifier = Modifier.padding(top = dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            EvidenceTier.entries.forEach { entry ->
                Text(
                    text = "${entry.ordinalLabel} — ${entry.shortLabel}",
                    style = HugMunTheme.type.bodyM,
                    color = if (entry == tier) colors.ink else colors.inkFaint,
                )
            }
        }
    }
}
