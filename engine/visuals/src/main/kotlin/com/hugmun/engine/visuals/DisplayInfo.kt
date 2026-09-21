/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.visuals

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hugmun.engine.psychophysics.DisplayTiming

/**
 * What the screen can actually do.
 *
 * Read at the start of every session rather than cached, because a device can change
 * display mode underneath the app — battery saver, adaptive refresh, an external
 * display. A threshold measured at an assumed refresh rate would be wrong in a way
 * nothing would flag.
 */
public object DisplayInfo {

    /** The timing of the currently active display mode. */
    public fun current(context: Context): DisplayTiming {
        val display = resolveDisplay(context)
        val hz = display?.refreshRate?.toDouble()?.takeIf { it > MIN_PLAUSIBLE_HZ } ?: FALLBACK_HZ
        return DisplayTiming(hz)
    }

    /**
     * Every refresh rate this display supports, highest first.
     *
     * Used by «Ритм» to decide whether a 40 Hz stimulus can be rendered at all, and by
     * «Зоркость» to prefer the mode with the finest time quantum.
     */
    public fun supportedRefreshRates(context: Context): List<Double> {
        val display = resolveDisplay(context) ?: return listOf(FALLBACK_HZ)
        return display.supportedModes
            .map { it.refreshRate.toDouble() }
            .filter { it > MIN_PLAUSIBLE_HZ }
            .distinct()
            .sortedDescending()
            .ifEmpty { listOf(FALLBACK_HZ) }
    }

    /**
     * Asks the platform for the highest refresh rate available.
     *
     * A finer time quantum means a more precise threshold: 8.3 ms steps at 120 Hz against
     * 16.7 ms at 60 Hz. The request is advisory — the platform may refuse, which is why
     * the session records the rate it actually got rather than the one it asked for.
     */
    public fun requestHighestRefreshRate(activity: Activity) {
        val display = resolveDisplay(activity) ?: return
        val best = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = best.modeId
        }
    }

    /**
     * Asks for a mode whose refresh rate is an integer multiple of [frequencyHz].
     *
     * Returns the timing actually requested, or null when no such mode exists — in which
     * case the caller must disable the stimulus rather than approximate it. See
     * `docs/research/03-safety-and-regulatory.md` §3.3.
     */
    public fun requestModeFor(activity: Activity, frequencyHz: Double): DisplayTiming? {
        val display = resolveDisplay(activity) ?: return null
        val candidate = display.supportedModes
            .filter { DisplayTiming(it.refreshRate.toDouble()).supportsPeriodicStimulus(frequencyHz) }
            .maxByOrNull { it.refreshRate }
            ?: return null

        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = candidate.modeId
        }
        return DisplayTiming(candidate.refreshRate.toDouble())
    }

    /** Clears any mode preference this app set. */
    public fun releaseModePreference(activity: Activity) {
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = 0
        }
    }

    private fun resolveDisplay(context: Context): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display }.getOrNull()
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

    private const val FALLBACK_HZ = 60.0
    private const val MIN_PLAUSIBLE_HZ = 20.0
}

/** Remembers the current display timing for the composition. */
@Composable
public fun rememberDisplayTiming(): DisplayTiming {
    val context = LocalContext.current
    return remember(context) { DisplayInfo.current(context) }
}
