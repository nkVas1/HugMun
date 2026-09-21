/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Which palette to use.
 *
 * [Dawn] is the default, and that is a clinical choice rather than a stylistic one — see
 * [HugMunColors]. [System] is offered because some people simply prefer dark, but it is
 * never the initial value.
 */
public enum class ThemePreference {
    Dawn,
    Night,
    System,
}

public object HugMunTheme {
    public val colors: HugMunColors
        @Composable @ReadOnlyComposable get() = LocalHugMunColors.current

    public val type: HugMunTypography
        @Composable @ReadOnlyComposable get() = LocalHugMunTypography.current

    public val dimens: HugMunDimens
        @Composable @ReadOnlyComposable get() = LocalHugMunDimens.current

    public val shapes: HugMunShapes
        @Composable @ReadOnlyComposable get() = LocalHugMunShapes.current

    public val motion: HugMunMotion
        @Composable @ReadOnlyComposable get() = LocalHugMunMotion.current
}

/**
 * The application theme.
 *
 * @param forceDark used by «Ритм», where a dark surround is part of the stimulus rather
 *   than a preference. It overrides everything, including the user's own setting.
 */
@Composable
public fun HugMunTheme(
    preference: ThemePreference = ThemePreference.Dawn,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when {
        forceDark -> true
        preference == ThemePreference.Night -> true
        preference == ThemePreference.System -> systemDark
        else -> false
    }

    val colors = if (dark) NightColors else DawnColors
    val reduceMotion = rememberReduceMotion()
    val motion = remember(reduceMotion) { DefaultMotion.copy(isReduced = reduceMotion) }

    CompositionLocalProvider(
        LocalHugMunColors provides colors,
        LocalHugMunTypography provides HugMunType,
        LocalHugMunDimens provides DefaultDimens,
        LocalHugMunShapes provides DefaultShapes,
        LocalHugMunMotion provides motion,
        LocalTextStyle provides HugMunType.bodyL,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = colors.toMaterialTypography(),
            shapes = androidx.compose.material3.Shapes(
                small = DefaultShapes.input,
                medium = DefaultShapes.card,
                large = DefaultShapes.primaryAction,
            ),
            content = content,
        )
    }
}

/**
 * Reads the platform's animation scale.
 *
 * `Settings.Global.ANIMATOR_DURATION_SCALE` at zero is the signal Android gives when the
 * user has asked for less motion — either through "Remove animations" in accessibility
 * settings or through developer options. Honouring it is not optional here; vestibular
 * sensitivity is common in this age group.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        scale == 0f
    }
}

/**
 * A Material 3 scheme derived from our own tokens.
 *
 * HugMun does not use Material's colour roles as its design language — the tokens above
 * are the source of truth — but Material components used inside the app (dialogs,
 * sliders, switches) need a coherent scheme rather than the purple default.
 */
private fun HugMunColors.toMaterialScheme() = if (isLight) {
    lightColorScheme(
        primary = dawnBright,
        onPrimary = onDawn,
        primaryContainer = dawnWash,
        onPrimaryContainer = dawn,
        secondary = sky,
        onSecondary = Color.White,
        secondaryContainer = skyWash,
        onSecondaryContainer = sky,
        background = surfaceBase,
        onBackground = ink,
        surface = surfaceBase,
        onSurface = ink,
        surfaceVariant = surfaceSunk,
        onSurfaceVariant = inkMuted,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerLow = surfaceSunk,
        outline = surfaceEdge,
        outlineVariant = surfaceEdge,
        error = clay,
        onError = Color.White,
    )
} else {
    darkColorScheme(
        primary = dawnBright,
        onPrimary = onDawn,
        primaryContainer = dawnWash,
        onPrimaryContainer = dawn,
        secondary = sky,
        onSecondary = Color.Black,
        secondaryContainer = skyWash,
        onSecondaryContainer = sky,
        background = surfaceBase,
        onBackground = ink,
        surface = surfaceBase,
        onSurface = ink,
        surfaceVariant = surfaceSunk,
        onSurfaceVariant = inkMuted,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerLow = surfaceSunk,
        outline = surfaceEdge,
        outlineVariant = surfaceEdge,
        error = clay,
        onError = Color.Black,
    )
}

private fun HugMunColors.toMaterialTypography(): Typography = Typography(
    displayLarge = HugMunType.displayL,
    displayMedium = HugMunType.displayM,
    displaySmall = HugMunType.displayM,
    headlineLarge = HugMunType.titleL,
    headlineMedium = HugMunType.titleL,
    headlineSmall = HugMunType.titleM,
    titleLarge = HugMunType.titleL,
    titleMedium = HugMunType.titleM,
    titleSmall = HugMunType.titleM,
    bodyLarge = HugMunType.bodyL,
    bodyMedium = HugMunType.bodyM,
    bodySmall = HugMunType.bodyM,
    labelLarge = HugMunType.label,
    labelMedium = HugMunType.label,
    labelSmall = HugMunType.label,
)

internal val LocalHugMunColors = staticCompositionLocalOf { DawnColors }
internal val LocalHugMunTypography = staticCompositionLocalOf { HugMunType }
internal val LocalHugMunDimens = staticCompositionLocalOf { DefaultDimens }
internal val LocalHugMunShapes = staticCompositionLocalOf { DefaultShapes }
internal val LocalHugMunMotion = staticCompositionLocalOf { DefaultMotion }
