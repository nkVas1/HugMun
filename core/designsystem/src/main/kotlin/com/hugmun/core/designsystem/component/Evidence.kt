/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.core.model.EvidenceCard
import com.hugmun.core.model.EvidenceTier

/**
 * The evidence badge.
 *
 * Every exercise in HugMun carries one, and it is honest about weak evidence rather than
 * hiding it. This is a product rule, not a style: see `docs/research/README.md`.
 *
 * The tier is carried by **letter, colour and words together** — never by colour alone,
 * because blue–yellow discrimination declines with age and colour-vision deficiency is
 * common.
 */
@Composable
public fun HugEvidenceBadge(
    tier: EvidenceTier,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    val accent = when (tier) {
        EvidenceTier.A -> colors.moss
        EvidenceTier.B -> colors.sky
        EvidenceTier.C -> colors.dawn
        EvidenceTier.D -> colors.inkMuted
    }

    Row(
        modifier = modifier
            .clip(HugMunTheme.shapes.chip)
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minHeight = dimens.minTouchTarget)
                        .foundationClickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = dimens.spaceXs)
            .semantics {
                contentDescription = "Уровень доказательности ${tier.ordinalLabel}. ${tier.shortLabel}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        Box(
            modifier = Modifier
                .size(BADGE_SIZE)
                .clip(HugMunTheme.shapes.chip)
                .background(accent.copy(alpha = BADGE_FILL_ALPHA))
                .border(dimens.hairline, SolidColor(accent), HugMunTheme.shapes.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = tier.ordinalLabel, style = HugMunTheme.type.label, color = accent)
        }
        if (showLabel) {
            Text(text = tier.shortLabel, style = HugMunTheme.type.label, color = colors.inkMuted)
        }
    }
}

/**
 * The full evidence card.
 *
 * Structure is fixed and deliberate: what was studied, in whom, what was found, and —
 * always — what the limitation is. The caveat is not smaller, greyer or collapsed. An app
 * that buries its caveats has not really disclosed them.
 */
@Composable
public fun HugEvidenceDetail(
    card: EvidenceCard,
    modifier: Modifier = Modifier,
    onOpenCitation: ((String) -> Unit)? = null,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    HugCard(modifier = modifier) {
        HugEvidenceBadge(tier = card.tier)
        Text(text = card.headline, style = HugMunTheme.type.titleM, color = colors.ink)

        EvidenceRow(label = "Что изучали", value = card.whatWasStudied)
        EvidenceRow(label = "У кого", value = card.inWhom)
        EvidenceRow(label = "Что получилось", value = card.whatWasFound)

        Box(
            modifier = Modifier
                .padding(top = dimens.spaceS)
                .clip(HugMunTheme.shapes.input)
                .background(colors.surfaceSunk)
                .padding(dimens.spaceM),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Text(text = "О чём стоит помнить", style = HugMunTheme.type.label, color = colors.inkMuted)
                Text(text = card.caveat, style = HugMunTheme.type.bodyM, color = colors.ink)
            }
        }

        if (card.citations.isNotEmpty()) {
            HugRule(modifier = Modifier.padding(vertical = dimens.spaceS))
            Text(text = "Источники", style = HugMunTheme.type.label, color = colors.inkMuted)
            card.citations.forEach { citation ->
                CitationRow(
                    text = "${citation.authors}, ${citation.year}. ${citation.title}. ${citation.journal}.",
                    summary = citation.summary,
                    onClick = onOpenCitation?.let { open -> { open(citation.identifier) } },
                )
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    val colors = HugMunTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = HugMunTheme.type.label, color = colors.inkFaint)
        Text(text = value, style = HugMunTheme.type.bodyM, color = colors.ink)
    }
}

@Composable
private fun CitationRow(text: String, summary: String, onClick: (() -> Unit)?) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    Column(
        modifier = Modifier
            .clip(HugMunTheme.shapes.input)
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minHeight = dimens.minTouchTarget)
                        .foundationClickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(text = summary, style = HugMunTheme.type.bodyM, color = colors.ink)
        Text(text = text, style = HugMunTheme.type.label, color = colors.inkFaint)
    }
}

/**
 * A neutral informational note.
 *
 * Used for the sentences the product exists to say honestly — "this change is within your
 * usual variation", "improvement on a practised task is expected and does not by itself
 * mean your memory improved".
 */
@Composable
public fun HugNote(text: String, modifier: Modifier = Modifier, tone: NoteTone = NoteTone.Neutral) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    val (fill, accent) = when (tone) {
        NoteTone.Neutral -> colors.surfaceSunk to colors.inkMuted
        NoteTone.Informational -> colors.skyWash to colors.sky
        NoteTone.Caution -> colors.dawnWash to colors.dawn
    }

    Row(
        modifier = modifier
            .clip(HugMunTheme.shapes.input)
            .background(fill)
            .padding(dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        Box(
            modifier = Modifier
                .size(width = RULE_WIDTH, height = RULE_HEIGHT)
                .background(accent),
        )
        Text(text = text, style = HugMunTheme.type.bodyM, color = colors.ink)
    }
}

public enum class NoteTone {
    Neutral,
    Informational,
    Caution,
}

private val BADGE_SIZE = 32.dp
private val RULE_WIDTH = 3.dp
private val RULE_HEIGHT = 24.dp
private const val BADGE_FILL_ALPHA = 0.10f
