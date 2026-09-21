/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.hugmun.core.designsystem.theme.HugMunTheme
import com.hugmun.engine.visuals.DisplayInfo
import com.hugmun.navigation.HugMunApp

/**
 * The single activity.
 *
 * Two things here are not boilerplate:
 *
 * 1. The highest available refresh rate is requested at startup. A finer time quantum
 *    means a more precise «Зоркость» threshold — 8.3 ms steps at 120 Hz against 16.7 ms
 *    at 60 Hz — and the request is advisory, so the session records what it actually got.
 * 2. The display timing is re-read on every resume rather than cached. Battery saver,
 *    adaptive refresh and external displays can all change it underneath the app, and a
 *    threshold measured against an assumed refresh rate would be wrong with nothing to
 *    flag it.
 */
public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        DisplayInfo.requestHighestRefreshRate(this)

        val graph = (application as HugMunApplication).graph

        setContent {
            val timing by remember { mutableStateOf(DisplayInfo.current(this)) }
            HugMunTheme {
                HugMunApp(graph = graph, displayTiming = timing)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DisplayInfo.requestHighestRefreshRate(this)
    }
}
