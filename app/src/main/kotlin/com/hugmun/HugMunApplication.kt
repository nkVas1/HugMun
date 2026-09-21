/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.hugmun.core.notifications.HugMunNotifications
import com.hugmun.core.notifications.PlanReminderWorker
import com.hugmun.di.AppGraph
import kotlinx.coroutines.launch

public class HugMunApplication : Application() {

    public lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)

        HugMunNotifications.createChannels(this)

        // WorkManager builds its own workers, so with a hand-written graph (ADR 0002)
        // this is the bridge. One function, set once, and the worker stays ignorant of
        // how the graph is assembled.
        PlanReminderWorker.install { context ->
            PlanReminderWorker.Dependencies(
                planToday = graph.planToday,
                preferences = graph.preferencesRepository,
                launchIntent = { ctx ->
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    PendingIntent.getActivity(
                        ctx,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                },
            )
        }

        graph.applicationScope.launch {
            val preferences = graph.preferencesRepository.current()
            if (preferences.remindersEnabled) {
                graph.reminderScheduler.schedule(preferences.reminderHour)
            }
        }
    }
}
