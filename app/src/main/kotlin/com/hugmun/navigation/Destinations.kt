/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every place the user can be.
 *
 * Navigation 3 rather than a router: the back stack is an ordinary list that this app
 * owns and mutates, which is the same reasoning as ADR 0002 — explicit beats generated
 * when the graph is small enough to read.
 *
 * Keys are `@Serializable` so the stack survives process death, which matters here
 * because an 83-year-old who takes a phone call in the middle of a session should come
 * back to where he was.
 */
public sealed interface Destination : NavKey {

    /** The day's plan. The app's home. */
    @Serializable
    public data object Today : Destination

    /** Trends over time, with the uncertainty band drawn. */
    @Serializable
    public data object Progress : Destination

    /** Settings, evidence library, export, about. */
    @Serializable
    public data object More : Destination

    // --- «Зоркость» ---------------------------------------------------------------

    /** Explains the task before it starts. Always shown; never skippable by default. */
    @Serializable
    public data object VigilanceIntro : Destination

    /** The session itself. */
    @Serializable
    public data object VigilanceSession : Destination

    /** The result, with its caveats. */
    @Serializable
    public data class VigilanceResult(public val sessionId: Long) : Destination

    // --- «Якорь» --------------------------------------------------------------------

    /** The list of things the user is holding on to. */
    @Serializable
    public data object AnchorList : Destination

    @Serializable
    public data object AnchorAdd : Destination

    @Serializable
    public data object AnchorReview : Destination

    // --- «Ритм» ---------------------------------------------------------------------

    /** Explains the practice, its evidence tier and why the light may be unavailable. */
    @Serializable
    public data object RhythmIntro : Destination

    /** The photosensitivity questionnaire. */
    @Serializable
    public data object RhythmScreening : Destination

    /** Consent for the photic channel, separate from onboarding consent by design. */
    @Serializable
    public data object RhythmConsent : Destination

    /** The session. [withLight] is false for the audio-only variant. */
    @Serializable
    public data class RhythmSession(public val withLight: Boolean) : Destination

    // --- Supporting -----------------------------------------------------------------

    /** The evidence card for one practice. */
    @Serializable
    public data class Evidence(public val practiceId: String) : Destination

    /** The full evidence library. */
    @Serializable
    public data object Library : Destination

    @Serializable
    public data object Settings : Destination

    @Serializable
    public data object About : Destination
}

/**
 * The three top-level destinations.
 *
 * Three, not five, and always visible along the bottom. No drawer, no hamburger: the
 * accessibility baseline in `docs/research/03-safety-and-regulatory.md` §8 rules them
 * out for primary navigation, because a menu that hides where you can go is a menu an
 * unconfident user does not open.
 */
public enum class TopLevel(public val destination: Destination, public val labelRes: Int) {
    TODAY(Destination.Today, com.hugmun.R.string.nav_today),
    PROGRESS(Destination.Progress, com.hugmun.R.string.nav_progress),
    MORE(Destination.More, com.hugmun.R.string.nav_more),
    ;

    public companion object {
        public fun forDestination(destination: Destination): TopLevel? =
            entries.firstOrNull { it.destination == destination }
    }
}
