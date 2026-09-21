/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
public data class HugMunShapes(
    public val card: CornerBasedShape = RoundedCornerShape(20.dp),
    public val primaryAction: CornerBasedShape = RoundedCornerShape(28.dp),
    public val input: CornerBasedShape = RoundedCornerShape(14.dp),
    public val chip: CornerBasedShape = RoundedCornerShape(percent = 50),

    /**
     * Task surfaces are square-cornered and unstyled.
     *
     * A psychophysical stimulus sits on a neutral field; rounded, tinted chrome around it
     * would add luminance and contour cues that the measurement does not account for.
     */
    public val stimulus: Shape = RectangleShape,
)

public val DefaultShapes: HugMunShapes = HugMunShapes()
