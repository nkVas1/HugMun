/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The HugMun palette.
 *
 * Derived from dawn light on unbleached paper: bone and oat for ground, iron-gall ink for
 * text, terracotta for the sun, and a single cold note of slate-teal for the sky opposite
 * the sunrise. See `docs/design/design-language.md` §3.
 *
 * Two decisions here are clinical rather than aesthetic:
 *
 * 1. **Pure white is absent from large areas.** At high device brightness it glares, and
 *    for an eye with any lens opacity it scatters.
 * 2. **Light is the default theme.** Ageing eyes need more light, and intraocular scatter
 *    makes light-text-on-dark bloom. The fashionable choice would be a dark app; the
 *    correct one for an 83-year-old is a bright, warm page.
 *
 * Contrast ratios for every pair actually used are enforced by `ColorContrastTest`, not
 * asserted in a comment. Change a colour and the build tells you if you broke legibility.
 */
@Immutable
public data class HugMunColors(
    /** Recessed wells and track backgrounds. */
    public val surfaceSunk: Color,
    /** The page. */
    public val surfaceBase: Color,
    /** Cards lifted off the page. */
    public val surfaceRaised: Color,
    /**
     * Decorative hairlines and dividers.
     *
     * Intentionally low-contrast. WCAG 1.4.11 applies to components and graphics needed
     * to understand content; a divider that merely separates two already-separated blocks
     * is not one, and forcing it to 3:1 would turn the page into a grid of boxes and
     * destroy the airiness the design depends on. Anything a finger can press uses
     * [controlEdge] instead.
     */
    public val surfaceEdge: Color,

    /**
     * The boundary of an interactive control: outlined buttons, inputs, selectable cards.
     *
     * This one **must** meet 3:1 against every surface it can appear on, and
     * `ColorContrastTest` enforces it. The distinction between this and [surfaceEdge] is
     * the difference between decoration and affordance.
     */
    public val controlEdge: Color,

    /** Focus indicator. Also held to 3:1. */
    public val focusRing: Color,

    /** Body and headings. */
    public val ink: Color,
    /** Secondary text. */
    public val inkMuted: Color,
    /** Captions and axis labels. Never load-bearing. */
    public val inkFaint: Color,

    /** Primary accent, dark enough to carry text. */
    public val dawn: Color,
    /** Primary accent for fills and large elements. */
    public val dawnBright: Color,
    /** Tinted container in the warm accent. */
    public val dawnWash: Color,
    /** Text and icons placed on [dawnBright]. */
    public val onDawn: Color,

    /** The cold counterpoint: second data series, informational states. */
    public val sky: Color,
    public val skyWash: Color,

    /** Steady, on-track. Paired with a glyph, never used alone. */
    public val moss: Color,
    /** Needs attention. Deliberately not named "error". */
    public val clay: Color,

    /** The raven mark. Never used for text. */
    public val ravenInk: Color,

    public val isLight: Boolean,
) {
    /** Surface for the 40 Hz session, where a dark surround is part of the stimulus. */
    public val stimulusBackdrop: Color get() = Color(0xFF07070A)
}

/** «Рассвет» — the default theme. */
public val DawnColors: HugMunColors = HugMunColors(
    surfaceSunk = Color(0xFFEDE5D9),
    surfaceBase = Color(0xFFFBF7F1),
    surfaceRaised = Color(0xFFFFFDF9),
    surfaceEdge = Color(0xFFE3D9C9),
    controlEdge = Color(0xFF877C69),
    focusRing = Color(0xFF8F3D18),
    ink = Color(0xFF1E1B16),
    inkMuted = Color(0xFF554D42),
    inkFaint = Color(0xFF6B6357),
    dawn = Color(0xFF8F3D18),
    dawnBright = Color(0xFFA8481C),
    dawnWash = Color(0xFFF6E7DA),
    onDawn = Color(0xFFFFFCF7),
    sky = Color(0xFF1F4A59),
    skyWash = Color(0xFFE2ECEF),
    moss = Color(0xFF2F5238),
    clay = Color(0xFF8E2F26),
    ravenInk = Color(0xFF15161B),
    isLight = true,
)

/** «Ночь» — available as a preference, forced inside «Ритм». */
public val NightColors: HugMunColors = HugMunColors(
    surfaceSunk = Color(0xFF0C0C0A),
    surfaceBase = Color(0xFF141412),
    surfaceRaised = Color(0xFF1D1C19),
    surfaceEdge = Color(0xFF3A3731),
    controlEdge = Color(0xFF767065),
    focusRing = Color(0xFFE8A268),
    ink = Color(0xFFF2ECE1),
    inkMuted = Color(0xFFC4BBAC),
    inkFaint = Color(0xFF9A9183),
    dawn = Color(0xFFF0B77F),
    dawnBright = Color(0xFFE8A268),
    dawnWash = Color(0xFF2B2018),
    onDawn = Color(0xFF1A1208),
    sky = Color(0xFF9CCBD9),
    skyWash = Color(0xFF14232A),
    moss = Color(0xFF9ECBA8),
    clay = Color(0xFFEE9A90),
    ravenInk = Color(0xFFE8E2D6),
    isLight = false,
)

// -----------------------------------------------------------------------------
// Contrast arithmetic — used by the design-system tests and by debug assertions.
// -----------------------------------------------------------------------------

/**
 * WCAG 2.x relative luminance.
 *
 * Implemented here rather than pulled from a library because the contrast test is a
 * build gate, and a build gate should not depend on someone else's rounding.
 */
public fun Color.wcagRelativeLuminance(): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= SRGB_THRESHOLD) c / SRGB_LOW_DIVISOR else ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
    }
    return RED_COEFFICIENT * channel(red) +
        GREEN_COEFFICIENT * channel(green) +
        BLUE_COEFFICIENT * channel(blue)
}

/** WCAG 2.x contrast ratio between two opaque colours, in the range 1.0 to 21.0. */
public fun contrastRatio(foreground: Color, background: Color): Double {
    val a = foreground.wcagRelativeLuminance()
    val b = background.wcagRelativeLuminance()
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)

private const val SRGB_THRESHOLD = 0.03928
private const val SRGB_LOW_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val RED_COEFFICIENT = 0.2126
private const val GREEN_COEFFICIENT = 0.7152
private const val BLUE_COEFFICIENT = 0.0722
private const val CONTRAST_OFFSET = 0.05

/** Contrast targets, from `docs/research/03-safety-and-regulatory.md` §8. */
public object ContrastTarget {
    /** Body text. Above the WCAG AA floor of 4.5 because the reader is 83. */
    public const val BODY_TEXT: Double = 7.0

    /** Text at 24 sp or above, and meaningful icons. */
    public const val LARGE_TEXT: Double = 4.5

    /** Control boundaries, focus rings, chart hairlines. */
    public const val NON_TEXT: Double = 3.0
}
