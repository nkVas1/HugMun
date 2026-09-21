/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.model

/**
 * How strong the evidence behind an exercise actually is.
 *
 * This type exists because of a product rule with no exceptions: **no module ships
 * without an evidence card**, and the card tells the truth, including when the truth is
 * "we don't really know". The tiers mirror `docs/research/README.md`.
 *
 * The honest version turns out to be more persuasive than the marketing version, and it
 * is the only version that respects an 83-year-old reader.
 *
 * @property ordinalLabel the tier letter, for the small badge.
 * @property shortLabel plain Russian, shown on the exercise card.
 * @property explanation plain Russian, shown when the badge is tapped.
 */
public enum class EvidenceTier(
    public val ordinalLabel: String,
    public val shortLabel: String,
    public val explanation: String,
) {
    A(
        ordinalLabel = "A",
        shortLabel = "Подтверждено в длительных исследованиях",
        explanation = "Есть крупные исследования с длительным наблюдением, где этот вид занятий " +
            "давал измеримый результат. Это не гарантия для конкретного человека, но самая " +
            "надёжная опора из тех, что у нас есть.",
    ),
    B(
        ordinalLabel = "B",
        shortLabel = "Хорошо изучено",
        explanation = "Есть согласованные результаты нескольких исследований, но обычно по " +
            "промежуточным показателям. Что будет через много лет — пока не проверено.",
    ),
    C(
        ordinalLabel = "C",
        shortLabel = "Изучается, результаты обнадёживают",
        explanation = "Исследования небольшие или ранние. Результаты интересные, но говорить " +
            "об установленном действии рано.",
    ),
    D(
        ordinalLabel = "D",
        shortLabel = "Экспериментально",
        explanation = "Основные данные получены в лаборатории или на животных. На людях " +
            "проверено мало. Мы включили это как эксперимент и честно об этом говорим.",
    ),
    ;

    /** True for tiers where the app must avoid anything resembling a promise. */
    public val requiresExperimentalFraming: Boolean get() = this == C || this == D
}

/**
 * A single citation, shown verbatim in the in-app library.
 *
 * @property key matches the BibTeX key in `docs/research/references.bib`.
 * @property summary what the study found, in one Russian sentence, phrased as something
 *   that happened to its participants — never as something that will happen to the user.
 */
public data class Citation(
    public val key: String,
    public val authors: String,
    public val year: Int,
    public val title: String,
    public val journal: String,
    public val identifier: String,
    public val summary: String,
) {
    init {
        require(key.isNotBlank()) { "citation key must not be blank" }
        require(summary.isNotBlank()) { "a citation without a plain-language summary is not shippable" }
    }
}

/**
 * The evidence card attached to every exercise.
 *
 * @property caveat the part most products leave out. Required, not optional: if there is
 *   genuinely nothing to caveat, the tier is wrong.
 */
public data class EvidenceCard(
    public val tier: EvidenceTier,
    public val headline: String,
    public val whatWasStudied: String,
    public val inWhom: String,
    public val whatWasFound: String,
    public val caveat: String,
    public val citations: List<Citation>,
) {
    init {
        require(citations.isNotEmpty()) { "an evidence card without citations is a marketing claim" }
        require(caveat.isNotBlank()) { "every card must state its limitation" }
    }
}
