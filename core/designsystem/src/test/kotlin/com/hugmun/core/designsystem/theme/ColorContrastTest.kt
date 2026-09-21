/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast is a build gate here, not a review item.
 *
 * Change a colour, and if any pair the theme actually uses drops below its target, this
 * fails. The targets come from `docs/research/03-safety-and-regulatory.md` §8: body text
 * at AAA (7:1) rather than AA, because the primary user is 83 and lens changes reduce
 * effective retinal contrast well before anything shows up on an acuity chart.
 */
class ColorContrastTest {

    private data class Pair(val name: String, val foreground: Color, val background: Color, val target: Double)

    private fun pairsFor(colors: HugMunColors, theme: String): List<Pair> = listOf(
        // --- Body text: AAA ----------------------------------------------------
        Pair("$theme ink on base", colors.ink, colors.surfaceBase, ContrastTarget.BODY_TEXT),
        Pair("$theme ink on raised", colors.ink, colors.surfaceRaised, ContrastTarget.BODY_TEXT),
        Pair("$theme ink on sunk", colors.ink, colors.surfaceSunk, ContrastTarget.BODY_TEXT),
        Pair("$theme ink on dawnWash", colors.ink, colors.dawnWash, ContrastTarget.BODY_TEXT),
        Pair("$theme ink on skyWash", colors.ink, colors.skyWash, ContrastTarget.BODY_TEXT),
        Pair("$theme inkMuted on base", colors.inkMuted, colors.surfaceBase, ContrastTarget.BODY_TEXT),
        Pair("$theme inkMuted on raised", colors.inkMuted, colors.surfaceRaised, ContrastTarget.BODY_TEXT),

        // --- Large text and meaningful icons: AA large --------------------------
        Pair("$theme inkFaint on base", colors.inkFaint, colors.surfaceBase, ContrastTarget.LARGE_TEXT),
        Pair("$theme inkFaint on raised", colors.inkFaint, colors.surfaceRaised, ContrastTarget.LARGE_TEXT),
        Pair("$theme inkFaint on sunk", colors.inkFaint, colors.surfaceSunk, ContrastTarget.LARGE_TEXT),
        Pair("$theme dawn on base", colors.dawn, colors.surfaceBase, ContrastTarget.LARGE_TEXT),
        Pair("$theme dawn on raised", colors.dawn, colors.surfaceRaised, ContrastTarget.LARGE_TEXT),
        Pair("$theme dawn on dawnWash", colors.dawn, colors.dawnWash, ContrastTarget.LARGE_TEXT),
        Pair("$theme sky on base", colors.sky, colors.surfaceBase, ContrastTarget.LARGE_TEXT),
        Pair("$theme sky on skyWash", colors.sky, colors.skyWash, ContrastTarget.LARGE_TEXT),
        Pair("$theme moss on base", colors.moss, colors.surfaceBase, ContrastTarget.LARGE_TEXT),
        Pair("$theme moss on raised", colors.moss, colors.surfaceRaised, ContrastTarget.LARGE_TEXT),
        Pair("$theme clay on base", colors.clay, colors.surfaceBase, ContrastTarget.LARGE_TEXT),
        Pair("$theme clay on raised", colors.clay, colors.surfaceRaised, ContrastTarget.LARGE_TEXT),

        // --- The primary button label sits on the accent fill -------------------
        Pair("$theme onDawn on dawnBright", colors.onDawn, colors.dawnBright, ContrastTarget.LARGE_TEXT),

        // --- Non-text: control boundaries and focus rings -----------------------
        // Note the token split: surfaceEdge is decorative and deliberately quiet, while
        // controlEdge marks anything a finger can press and is held to 3:1.
        Pair("$theme controlEdge on base", colors.controlEdge, colors.surfaceBase, ContrastTarget.NON_TEXT),
        Pair("$theme controlEdge on raised", colors.controlEdge, colors.surfaceRaised, ContrastTarget.NON_TEXT),
        Pair("$theme controlEdge on sunk", colors.controlEdge, colors.surfaceSunk, ContrastTarget.NON_TEXT),
        Pair("$theme focusRing on base", colors.focusRing, colors.surfaceBase, ContrastTarget.NON_TEXT),
        Pair("$theme focusRing on raised", colors.focusRing, colors.surfaceRaised, ContrastTarget.NON_TEXT),
        Pair("$theme dawnBright on base", colors.dawnBright, colors.surfaceBase, ContrastTarget.NON_TEXT),
    )

    @Test
    fun `the dawn theme meets every contrast target it uses`() {
        assertPairs(pairsFor(DawnColors, "dawn"))
    }

    @Test
    fun `the night theme meets every contrast target it uses`() {
        assertPairs(pairsFor(NightColors, "night"))
    }

    private fun assertPairs(pairs: List<Pair>) {
        val failures = pairs.mapNotNull { pair ->
            val ratio = contrastRatio(pair.foreground, pair.background)
            if (ratio < pair.target) {
                "%-34s %.2f:1  (needs %.1f:1)".format(pair.name, ratio, pair.target)
            } else {
                null
            }
        }
        assertTrue(
            "Contrast targets not met:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    // --- Sanity checks on the arithmetic itself ---------------------------------

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }

    @Test
    fun `a colour against itself has no contrast`() {
        assertEquals(1.0, contrastRatio(DawnColors.ink, DawnColors.ink), 1e-9)
    }

    @Test
    fun `the ratio is symmetric`() {
        val forward = contrastRatio(DawnColors.ink, DawnColors.surfaceBase)
        val reverse = contrastRatio(DawnColors.surfaceBase, DawnColors.ink)
        assertEquals(forward, reverse, 1e-12)
    }

    @Test
    fun `relative luminance matches the WCAG reference values`() {
        assertEquals(0.0, Color.Black.wcagRelativeLuminance(), 1e-9)
        assertEquals(1.0, Color.White.wcagRelativeLuminance(), 1e-9)
        // Pure sRGB green carries most of the luminance weight.
        assertEquals(0.7152, Color.Green.wcagRelativeLuminance(), 1e-4)
    }

    /**
     * The page must never be pure white.
     *
     * At high device brightness a pure-white field glares, and for an eye with any lens
     * opacity it scatters. The palette is built on warm bone for a reason, and it would
     * be easy for someone to "clean it up" to #FFFFFF without knowing that.
     */
    @Test
    fun `the page background is warm paper rather than pure white`() {
        assertTrue("surfaceBase must not be pure white", DawnColors.surfaceBase != Color.White)
        assertTrue(
            "surfaceBase should be warm: red channel above blue",
            DawnColors.surfaceBase.red > DawnColors.surfaceBase.blue,
        )
    }

    /**
     * Saturated red is the most provocative colour for photosensitive responses, so it is
     * excluded from the palette outright rather than avoided by convention.
     */
    @Test
    fun `no palette colour is a saturated red`() {
        val all = listOf(DawnColors, NightColors).flatMap {
            listOf(it.dawn, it.dawnBright, it.dawnWash, it.clay, it.sky, it.moss, it.ink)
        }
        all.forEach { color ->
            val dominant = color.red > SATURATION_FLOOR &&
                color.green < LOW_CHANNEL &&
                color.blue < LOW_CHANNEL
            assertTrue("saturated red found: $color", !dominant)
        }
    }

    /**
     * Decorative hairlines are exempt from 3:1, but they still have to be visible at all.
     * Without a floor here, someone could "simplify" the palette by making dividers
     * invisible and no test would notice.
     */
    @Test
    fun `decorative hairlines stay visible even though they are exempt from 3 to 1`() {
        for ((theme, colors) in listOf("dawn" to DawnColors, "night" to NightColors)) {
            val ratio = contrastRatio(colors.surfaceEdge, colors.surfaceBase)
            assertTrue("$theme hairline is invisible at %.2f:1".format(ratio), ratio >= DECORATIVE_FLOOR)
            assertTrue(
                "$theme hairline at %.2f:1 is loud enough to be a control edge; use controlEdge".format(ratio),
                ratio < ContrastTarget.NON_TEXT,
            )
        }
    }

    private companion object {
        const val SATURATION_FLOOR = 0.8f
        const val LOW_CHANNEL = 0.2f

        /** A divider below this is not a divider, it is nothing. */
        const val DECORATIVE_FLOOR = 1.25
    }
}
