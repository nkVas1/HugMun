/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hugmun.core.designsystem.theme.HugMunTheme

/** One destination in the bottom bar. */
public data class BottomBarItem(public val id: String, public val label: String)

/**
 * Primary navigation: a persistent bottom bar with at most three destinations.
 *
 * Several deliberate departures from the usual pattern, all from the accessibility
 * baseline:
 *
 * - **Words, not icons.** An icon plus a 10 sp caption is the convention; for this user
 *   it is a guessing game. Each destination is a word at a readable size.
 * - **Selection is carried by weight, colour and a rule**, never by colour alone.
 * - **No badge dots, no animation on selection** beyond a colour crossfade. A bar that
 *   moves when you look at it is a bar you distrust.
 * - Targets are the full height of the bar and at least 56 dp.
 */
@Composable
public fun HugBottomBar(
    items: List<BottomBarItem>,
    selectedId: String?,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised),
    ) {
        HugRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = BAR_HEIGHT),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomBarTab(
                    item = item,
                    selected = item.id == selectedId,
                    onSelect = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.height(dimens.spaceXs))
    }
}

@Composable
private fun BottomBarTab(item: BottomBarItem, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HugMunTheme.colors
    val dimens = HugMunTheme.dimens

    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = HugMunTheme.motion.stateChange(),
        label = "bottomBarEmphasis",
    )
    val content = lerp(colors.inkMuted, colors.dawn, emphasis)

    Column(
        modifier = modifier
            .clip(HugMunTheme.shapes.chip)
            .heightIn(min = dimens.minTouchTarget)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(vertical = dimens.spaceS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = item.label,
            style = if (selected) HugMunTheme.type.titleM else HugMunTheme.type.bodyM,
            color = content,
            textAlign = TextAlign.Center,
        )
        // A short rule under the selected tab: the second, non-colour signal.
        Box(
            modifier = Modifier
                .width(INDICATOR_WIDTH)
                .height(INDICATOR_HEIGHT)
                .background(
                    if (selected) colors.dawn else androidx.compose.ui.graphics.Color.Transparent,
                    HugMunTheme.shapes.chip,
                )
                .size(width = INDICATOR_WIDTH, height = INDICATOR_HEIGHT),
        )
    }
}

private val BAR_HEIGHT = 72.dp
private val INDICATOR_WIDTH = 28.dp
private val INDICATOR_HEIGHT = 3.dp
