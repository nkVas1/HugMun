/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme
import androidx.compose.foundation.clickable as foundationClickable

/**
 * A card.
 *
 * Note what is missing: no shadow, no gradient, no border by default. Cards are separated
 * from the page by luminance and by air, which is the whole premise of «Рассветный
 * воздух». A hairline appears only when the card is interactive, where it doubles as the
 * affordance.
 */
@Composable
public fun HugCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens
    val shape = HugMunTheme.shapes.card

    val interactive = onClick != null
    val pressedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = HugMunTheme.motion.stateChange(),
        label = "cardAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = pressedAlpha))
            .then(
                if (interactive) {
                    Modifier.border(dimens.hairline, SolidColor(colors.controlEdge), shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.foundationClickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { }
                } else {
                    Modifier
                },
            )
            .padding(dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        content = content,
    )
}

/**
 * The standard screen frame: page background, system-bar insets, gutter, scroll.
 *
 * Every screen in the app goes through here, which is how the 24 dp gutter and the
 * vertical rhythm stay consistent without each screen having to remember them.
 */
@Composable
public fun HugScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    backgroundColor: Color = HugMunTheme.colors.surfaceBase,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = HugMunTheme.dimens
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = dimens.screenGutter),
            verticalArrangement = Arrangement.spacedBy(dimens.blockSpacing),
        ) {
            Spacer(Modifier.height(dimens.spaceS))
            content()
            Spacer(Modifier.height(dimens.spaceXxl))
        }

        if (bottomBar != null) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                bottomBar()
            }
        }
    }
}

/** Screen title. Marked as a heading so screen readers can jump to it. */
@Composable
public fun HugScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val colors = HugMunTheme.colors
    Column(
        modifier = modifier.semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(HugMunTheme.dimens.spaceS),
    ) {
        Text(text = text, style = HugMunTheme.type.displayM, color = colors.ink)
        if (supporting != null) {
            Text(text = supporting, style = HugMunTheme.type.bodyM, color = colors.inkMuted)
        }
    }
}

/** Section heading inside a screen. */
@Composable
public fun HugSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = HugMunTheme.type.titleM,
        color = HugMunTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * A 1 dp rule.
 *
 * The only divider in the system. Heavier separators encourage boxing content in, which
 * fights the airiness the design depends on.
 */
@Composable
public fun HugRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HugMunTheme.dimens.hairline)
            .background(HugMunTheme.colors.surfaceEdge)
            .clearAndSetSemantics { },
    )
}

/** Fixed vertical space, for the rare case the standard rhythm is wrong. */
@Composable
public fun HugSpacer(height: androidx.compose.ui.unit.Dp = 16.dp) {
    Spacer(Modifier.height(height))
}

/** Remembers a stable value derived from the theme, for use in draw scopes. */
@Composable
internal fun <T> rememberThemed(key: Any?, producer: () -> T): T = remember(key) { producer() }
