/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hugmun.core.designsystem.R

/**
 * Two families, both SIL OFL, both with genuinely good Cyrillic — which rules out most
 * "characterful" display faces.
 *
 * - **Literata** for display and editorial moments. A serif drawn for extended screen
 *   reading; it carries the manuscript thread without costuming.
 * - **Golos Text** for everything functional. A humanist sans drawn specifically for
 *   Russian legibility at working sizes, with open apertures and unambiguous letterforms.
 *
 * Both are bundled rather than fetched through the downloadable-fonts provider. This app
 * is local-first and is used by someone who may be offline; a first run that falls back
 * to a system font because Play Services was unavailable is not acceptable for a design
 * that leans this hard on typography.
 */
private val GolosVariable = R.font.golos_text_variable
private val LiterataVariable = R.font.literata_variable

private fun golos(weight: FontWeight) = Font(
    resId = GolosVariable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun literata(weight: FontWeight) = Font(
    resId = LiterataVariable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

public val GolosText: FontFamily = FontFamily(
    golos(FontWeight.Normal),
    golos(FontWeight.Medium),
    golos(FontWeight.SemiBold),
    golos(FontWeight.Bold),
)

public val Literata: FontFamily = FontFamily(
    literata(FontWeight.Normal),
    literata(FontWeight.Medium),
    literata(FontWeight.SemiBold),
)

/**
 * The type scale.
 *
 * Base body is **20 sp**, larger than a general-audience app would use, because the
 * primary user is 83. The ratio between steps is a modest ~1.2 so that a large default
 * does not push display text off the screen.
 *
 * Everything here scales with the system font setting to 200 %. Layouts reflow; text is
 * never shrunk to fit.
 */
@Immutable
public data class HugMunTypography(
    public val displayL: TextStyle,
    public val displayM: TextStyle,
    public val titleL: TextStyle,
    public val titleM: TextStyle,
    public val bodyL: TextStyle,
    public val bodyM: TextStyle,
    public val label: TextStyle,
    /** The one big number on a results screen. Tabular. */
    public val numericXL: TextStyle,
    /** Figures in tables and on chart axes. Tabular. */
    public val numericM: TextStyle,
)

private val trimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Tabular figures, so columns of numbers line up and do not jitter as they change. */
private const val TABULAR_FIGURES = "tnum"

public val HugMunType: HugMunTypography = HugMunTypography(
    displayL = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.015).em,
        lineHeightStyle = trimmedLineHeight,
    ),
    displayM = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).em,
        lineHeightStyle = trimmedLineHeight,
    ),
    titleL = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        lineHeightStyle = trimmedLineHeight,
    ),
    titleM = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = trimmedLineHeight,
    ),
    bodyL = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        lineHeightStyle = trimmedLineHeight,
    ),
    bodyM = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        lineHeightStyle = trimmedLineHeight,
    ),
    label = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        lineHeightStyle = trimmedLineHeight,
    ),
    numericXL = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        fontFeatureSettings = TABULAR_FIGURES,
        letterSpacing = (-0.02).em,
    ),
    numericM = TextStyle(
        fontFamily = GolosText,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)
