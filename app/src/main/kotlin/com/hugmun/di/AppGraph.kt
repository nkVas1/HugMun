/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hugmun.core.common.AppDispatchers
import com.hugmun.core.common.SystemTimeSource
import com.hugmun.core.common.TimeSource
import com.hugmun.core.data.ProtocolRepositoryImpl
import com.hugmun.core.data.SafetyRepositoryImpl
import com.hugmun.core.data.VigilanceRepositoryImpl
import com.hugmun.core.database.HugMunDatabase
import com.hugmun.core.datastore.PreferencesRepositoryImpl
import com.hugmun.core.domain.PlanTodayUseCase
import com.hugmun.core.domain.PreferencesRepository
import com.hugmun.core.domain.ProtocolRepository
import com.hugmun.core.domain.SafetyRepository
import com.hugmun.core.domain.VigilanceRepository
import com.hugmun.core.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The dependency graph, written by hand.
 *
 * There is no DI framework here, and that is a decision rather than an omission — see
 * ADR 0002. The whole wiring of the application is this one file, it reads top to
 * bottom, and "where does this instance come from?" is answerable by looking.
 *
 * Everything is lazy, so starting the app does not open the database before the first
 * screen needs it.
 */
public class AppGraph(private val context: Context, public val dispatchers: AppDispatchers = DefaultAppDispatchers()) {
    public val timeSource: TimeSource = SystemTimeSource()

    /** Application-scoped work that must outlive any single screen. */
    public val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)

    private val database: HugMunDatabase by lazy {
        HugMunDatabase.create(context, dispatchers.io)
    }

    private val preferencesStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = { context.preferencesDataStoreFile(PreferencesRepositoryImpl.FILE_NAME) },
        )
    }

    // --- Repositories -------------------------------------------------------------

    public val protocolRepository: ProtocolRepository by lazy {
        ProtocolRepositoryImpl(database.protocolDao(), dispatchers)
    }

    public val vigilanceRepository: VigilanceRepository by lazy {
        vigilanceRepositoryImpl
    }

    /**
     * Exposed concretely as well, because the trend view needs `thresholdHistory`, which
     * is a query the port deliberately does not expose to every caller.
     */
    public val vigilanceRepositoryImpl: VigilanceRepositoryImpl by lazy {
        VigilanceRepositoryImpl(database.vigilanceDao(), dispatchers)
    }

    public val safetyRepository: SafetyRepository by lazy {
        SafetyRepositoryImpl(database.safetyDao(), dispatchers, timeSource)
    }

    public val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepositoryImpl(preferencesStore)
    }

    // --- Use cases ----------------------------------------------------------------

    public val planToday: PlanTodayUseCase by lazy {
        PlanTodayUseCase(protocolRepository, safetyRepository, timeSource)
    }

    // --- Reminders ----------------------------------------------------------------

    /**
     * The schedule has to be able to reach the user in eleven months, and again in
     * thirty-five. Without this the app implements the ACTIVE arm that showed no
     * effect — see the class docs on [ReminderScheduler].
     */
    public val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(context, timeSource)
    }
}

/**
 * Real dispatchers.
 *
 * `mainImmediate` matters: the stimulus presenter must not lose a frame to a dispatch,
 * because a frame is the measurement unit.
 */
public class DefaultAppDispatchers : AppDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
}
