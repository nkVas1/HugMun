/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hugmun.core.domain.PreferencesRepository
import com.hugmun.core.domain.ThemeChoice
import com.hugmun.core.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User preferences.
 *
 * Preferences rather than settings: several of these change what a practice *does*, not
 * merely how it looks, so they belong to the domain and are read through an interface
 * the feature modules can see.
 */
public class PreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

    override fun observe(): Flow<UserPreferences> = dataStore.data.map(::read)

    override suspend fun current(): UserPreferences = read(dataStore.data.first())

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { prefs ->
            val updated = transform(read(prefs))
            prefs[Keys.DISPLAY_NAME] = updated.displayName.orEmpty()
            prefs[Keys.THEME] = updated.theme.name
            prefs[Keys.LARGE_TYPE_BOOST] = updated.largeTypeBoost
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.HAPTICS_ENABLED] = updated.hapticsEnabled
            prefs[Keys.REMINDERS_ENABLED] = updated.remindersEnabled
            prefs[Keys.REMINDER_HOUR] = updated.reminderHour
            prefs[Keys.ONBOARDED] = updated.hasCompletedOnboarding
        }
    }

    private fun read(prefs: Preferences): UserPreferences = UserPreferences(
        displayName = prefs[Keys.DISPLAY_NAME]?.takeIf { it.isNotBlank() },
        theme = prefs[Keys.THEME]?.let { name ->
            runCatching { ThemeChoice.valueOf(name) }.getOrDefault(ThemeChoice.DAWN)
        } ?: ThemeChoice.DAWN,
        largeTypeBoost = prefs[Keys.LARGE_TYPE_BOOST] ?: false,
        soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
        hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
        remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
        reminderHour = prefs[Keys.REMINDER_HOUR] ?: UserPreferences.DEFAULT_REMINDER_HOUR,
        hasCompletedOnboarding = prefs[Keys.ONBOARDED] ?: false,
    )

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val THEME = stringPreferencesKey("theme")
        val LARGE_TYPE_BOOST = booleanPreferencesKey("large_type_boost")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val ONBOARDED = booleanPreferencesKey("has_completed_onboarding")
    }

    public companion object {
        public const val FILE_NAME: String = "hugmun_preferences"
    }
}
