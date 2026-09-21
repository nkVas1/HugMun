/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hugmun.core.domain.DayPlan
import com.hugmun.core.domain.PlanTodayUseCase
import com.hugmun.core.domain.PreferencesRepository
import com.hugmun.core.model.PlannedPractice

/**
 * Asks the protocol what is due today, and says so once if anything is.
 *
 * Silent on every other day. The point of a schedule-driven app is that it speaks rarely
 * and means it; an app that notifies daily regardless trains the user to dismiss it, and
 * then it is not there on the day the booster block opens.
 */
public class PlanReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val dependencies = provider?.invoke(applicationContext) ?: return Result.success()

        val preferences = dependencies.preferences.current()
        if (!preferences.remindersEnabled) return Result.success()

        val plan = dependencies.planToday()
        if (!plan.hasAnythingDue) return Result.success()

        HugMunNotifications.notifyPlan(
            context = applicationContext,
            plan = plan,
            launchIntent = dependencies.launchIntent(applicationContext),
        )
        return Result.success()
    }

    /** What the worker needs, supplied by the application's graph. */
    public class Dependencies(
        public val planToday: PlanTodayUseCase,
        public val preferences: PreferencesRepository,
        public val launchIntent: (Context) -> PendingIntent?,
    )

    public companion object {
        /**
         * How the worker reaches the dependency graph.
         *
         * WorkManager constructs workers itself, so with a hand-written graph (ADR 0002)
         * something has to bridge the two. A single function set once at application
         * start is the smallest thing that works, and it keeps the worker free of any
         * knowledge of how the graph is built.
         */
        @Volatile
        private var provider: ((Context) -> Dependencies)? = null

        public fun install(provider: (Context) -> Dependencies) {
            this.provider = provider
        }
    }
}

/** Notification channel setup and posting. */
public object HugMunNotifications {

    public const val CHANNEL_PLAN: String = "plan"
    private const val NOTIFICATION_ID_PLAN = 1001

    public fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            CHANNEL_PLAN,
            context.getString(R.string.notification_channel_plan_name),
            // DEFAULT, not HIGH: this is a reminder, not an emergency, and it should
            // never interrupt something the person is doing.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_plan_description)
            enableVibration(false)
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the reminder.
     *
     * Copy rules apply here as everywhere: it states what is due and how long it takes.
     * It does not say "you missed yesterday", does not count a streak, and does not use
     * the word "надо".
     */
    public fun notifyPlan(context: Context, plan: DayPlan, launchIntent: PendingIntent?) {
        if (!canPostNotifications(context)) return

        val due: PlannedPractice = plan.due.firstOrNull() ?: return
        val body = context.getString(
            R.string.notification_plan_body_single,
            due.practice.displayName,
            due.estimated.inWholeMinutes.toInt(),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notification_plan_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\n${due.rationale}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply { launchIntent?.let(::setContentIntent) }
            .build()

        // Guarded by canPostNotifications above, which lint cannot follow across the
        // call. The runCatching stays regardless: the grant can be revoked between the
        // check and the post, and a missed reminder must never crash a background worker.
        @SuppressLint("MissingPermission")
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PLAN, notification)
        }
    }

    /**
     * Whether a reminder can actually reach the user.
     *
     * [NotificationManagerCompat.areNotificationsEnabled] rather than a permission check
     * on `POST_NOTIFICATIONS`, because that permission did not exist before API 33 and
     * querying it below that returns *denied* — which would have silently switched
     * reminders off for every user on Android 8 through 12 while working perfectly on
     * the one Android 13 device this was tried on.
     *
     * It is also the better question. A user who has notifications turned off for the
     * app in system settings has said no just as clearly as one who declined the
     * dialogue, and this call covers both on every version in range.
     */
    private fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
