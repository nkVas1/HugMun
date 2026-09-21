/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hugmun.core.common.TimeSource
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps the training schedule reachable.
 *
 * This is not a convenience feature. The ACTIVE result the whole product is built on
 * says that speed training *with* booster blocks was associated with HR 0.75 and the
 * same training *without* them with HR 1.01 — no effect at all. The second booster falls
 * around month thirty-five. An app that cannot get someone's attention thirty-five
 * months from now has not implemented the intervention; it has implemented the arm that
 * did nothing.
 *
 * So the worker is deliberately dumb and deliberately persistent: it wakes once a day,
 * asks the protocol what is due, and says something only when there is something to say.
 * All the intelligence lives in `TrainingProtocol`, which is pure Kotlin and tested.
 */
public class ReminderScheduler(private val context: Context, private val timeSource: TimeSource) {
    /**
     * Schedules the daily check.
     *
     * `KEEP` rather than `REPLACE`, so that an app update does not silently reset the
     * schedule and delay the next reminder by a day.
     */
    public fun schedule(reminderHour: Int) {
        val request = PeriodicWorkRequestBuilder<PlanReminderWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(reminderHour), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    // No network constraint: there is no network. No charging or idle
                    // constraint either — a reminder that waits for the phone to be
                    // plugged in is a reminder that arrives at three in the morning.
                    .setRequiresBatteryNotLow(false)
                    .build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Re-schedules at a new hour, replacing whatever is queued. */
    public fun reschedule(reminderHour: Int) {
        cancel()
        schedule(reminderHour)
    }

    public fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private fun millisUntilNext(hour: Int): Long = millisUntilNext(timeSource.now(), timeSource.timeZone(), hour)

    public companion object {
        /**
         * Milliseconds until the next occurrence of [hour] in local time.
         *
         * A pure function of the three things it needs, so it can be tested without a
         * Context, an emulator or a clock. Computed against the user's own calendar
         * rather than as an offset from now: an offset drifts a little every day until
         * the reminder arrives at three in the morning, and nobody reports that as a
         * bug — they just stop using the app.
         */
        public fun millisUntilNext(now: Instant, zone: TimeZone, hour: Int): Long {
            val today = now.toLocalDateTime(zone).date
            val todayAtHour = LocalDateTime(today, LocalTime(hour, 0)).toInstant(zone)
            val target = if (todayAtHour > now) todayAtHour else todayAtHour.plus(1.days)
            return (target - now).inWholeMilliseconds.coerceAtLeast(0L)
        }

        public const val UNIQUE_NAME: String = "hugmun-plan-reminder"
        public const val TAG: String = "reminder"

        /**
         * Daily.
         *
         * More often would be nagging; less often would miss a due date. The protocol
         * itself decides whether there is anything to say on any given day, and most
         * days there is not.
         */
        public const val REPEAT_INTERVAL_HOURS: Long = 24L
    }
}
