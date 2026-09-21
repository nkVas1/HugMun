/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, sizing and the touch-target floor.
 *
 * The numbers that matter for accessibility are not suggestions here: [minTouchTarget]
 * is 56 dp, above the usual 48 dp guideline, because the primary user is 83 and may have
 * a tremor. [touchTargetGap] keeps adjacent targets far enough apart that a slightly
 * off-centre tap lands on nothing rather than on the wrong thing.
 */
@Immutable
public data class HugMunDimens(
    /** 4 dp base grid; layout uses the 8 dp subset. */
    public val gridUnit: Dp = 4.dp,

    public val spaceXs: Dp = 4.dp,
    public val spaceS: Dp = 8.dp,
    public val spaceM: Dp = 16.dp,
    public val spaceL: Dp = 24.dp,
    public val spaceXl: Dp = 32.dp,
    public val spaceXxl: Dp = 48.dp,

    /** Horizontal page margin. */
    public val screenGutter: Dp = 24.dp,

    /** Vertical rhythm between distinct blocks. Generous on purpose. */
    public val blockSpacing: Dp = 32.dp,

    /** Above the 48 dp guideline. Not negotiable. */
    public val minTouchTarget: Dp = 56.dp,

    /** Minimum clear space between adjacent touch targets. */
    public val touchTargetGap: Dp = 12.dp,

    /** Height of the single primary action on a screen. */
    public val primaryActionHeight: Dp = 72.dp,

    public val cardPadding: Dp = 20.dp,
    public val hairline: Dp = 1.dp,

    /** Focus ring offset, kept large enough to be visible against a card edge. */
    public val focusRingWidth: Dp = 3.dp,
)

public val DefaultDimens: HugMunDimens = HugMunDimens()
