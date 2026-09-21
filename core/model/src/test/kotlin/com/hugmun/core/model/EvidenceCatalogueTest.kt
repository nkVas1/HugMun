/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The product rules from `docs/research/03-safety-and-regulatory.md` §2.2, enforced by
 * the build rather than by review discipline.
 *
 * A reviewer will catch an overstated claim most of the time. "Most of the time" is not
 * good enough for copy an 83-year-old will use to decide what to believe about his own
 * brain.
 */
class EvidenceCatalogueTest {

    @Test
    fun `every practice has an evidence card`() {
        Practice.entries.forEach { practice ->
            val card = EvidenceCatalogue.cardFor(practice)
            assertTrue("${practice.id} has no headline", card.headline.isNotBlank())
            assertTrue("${practice.id} has no citations", card.citations.isNotEmpty())
        }
    }

    /**
     * `EvidenceCard` already refuses to construct without a caveat. This asserts the
     * caveat is a real sentence rather than a placeholder that satisfies the type.
     */
    @Test
    fun `every caveat is substantive`() {
        Practice.entries.forEach { practice ->
            val caveat = EvidenceCatalogue.cardFor(practice).caveat
            assertTrue(
                "${practice.id} has a token caveat of ${caveat.length} characters",
                caveat.length > MIN_CAVEAT_LENGTH,
            )
        }
    }

    @Test
    fun `the tier on the card matches the tier on the practice`() {
        Practice.entries.forEach { practice ->
            val card = EvidenceCatalogue.cardFor(practice)
            assertEquals("${practice.id}: practice and card disagree", practice.tier, card.tier)
        }
    }

    /**
     * The forbidden-copy rule.
     *
     * These are the phrasings that turn a wellness product into an unapproved medical
     * device and — more importantly — promise a specific person something no study can
     * promise them.
     */
    @Test
    fun `no card promises a medical outcome`() {
        val forbidden = listOf(
            "снижает риск",
            "снизит риск",
            "предотвращает",
            "предотвратит",
            "лечит",
            "вылечит",
            "терапия",
            "гарантирует",
            "доказано, что вы",
            "у вас улучшится",
            "вернёт память",
        )

        Practice.entries.forEach { practice ->
            val card = EvidenceCatalogue.cardFor(practice)
            val text = textOf(card).lowercase()
            forbidden.forEach { phrase ->
                assertTrue(
                    "${practice.id} contains the forbidden phrase \"$phrase\"",
                    !text.contains(phrase),
                )
            }
        }
    }

    /**
     * Tier C and D describe things that are not established. Their cards must say so in
     * a word the reader will actually notice, not only through a badge.
     */
    @Test
    fun `experimental practices announce themselves as experimental`() {
        Practice.entries
            .filter { it.tier.requiresExperimentalFraming }
            .forEach { practice ->
                val card = EvidenceCatalogue.cardFor(practice)
                val text = (card.headline + " " + card.caveat).lowercase()
                assertTrue(
                    "${practice.id} is tier ${card.tier} but never says so plainly",
                    text.contains("эксперимент") || text.contains("рано") || text.contains("ранни"),
                )
            }
    }

    @Test
    fun `no card uses emoji`() {
        Practice.entries.forEach { practice ->
            val text = textOf(EvidenceCatalogue.cardFor(practice))
            assertTrue(
                "${practice.id} contains emoji, which the voice rules forbid outright",
                text.none { it.isSurrogate() || it.code in PICTOGRAPH_START..PICTOGRAPH_END },
            )
        }
    }

    @Test
    fun `every citation carries a plain-language summary and an identifier`() {
        Practice.entries.forEach { practice ->
            EvidenceCatalogue.cardFor(practice).citations.forEach { citation ->
                assertTrue("${citation.key} has no identifier", citation.identifier.isNotBlank())
                assertTrue(
                    "${citation.key} has a token summary",
                    citation.summary.length > MIN_SUMMARY_LENGTH,
                )
            }
        }
    }

    /**
     * The one practice with Tier A long-term evidence must say the thing the whole
     * product is built around, or the user has no way to understand why the app keeps
     * coming back months later.
     */
    @Test
    fun `the speed practice explains why boosters matter`() {
        val card = EvidenceCatalogue.cardFor(Practice.VIGILANCE)
        val text = textOf(card).lowercase()
        assertTrue("the booster contrast must be stated", text.contains("повторн"))
        assertTrue("the null result without boosters must be stated", text.contains("без повторных"))
    }

    @Test
    fun `the catalogue exposes one entry per practice`() {
        assertEquals(Practice.entries.size, EvidenceCatalogue.all.size)
    }

    private fun textOf(card: EvidenceCard): String = buildString {
        append(card.headline).append(' ')
        append(card.whatWasStudied).append(' ')
        append(card.inWhom).append(' ')
        append(card.whatWasFound).append(' ')
        append(card.caveat).append(' ')
        card.citations.forEach { append(it.summary).append(' ') }
    }

    private companion object {
        const val MIN_CAVEAT_LENGTH = 60
        const val MIN_SUMMARY_LENGTH = 30

        /** Miscellaneous symbols and pictographs, which covers the emoji we might slip in. */
        const val PICTOGRAPH_START = 0x2600
        const val PICTOGRAPH_END = 0x27BF
    }
}
