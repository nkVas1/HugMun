/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.notifications

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delay arithmetic for the daily reminder.
 *
 * Worth testing because the failure mode is slow and silent: a reminder computed as an
 * offset from "now" rather than against the local calendar drifts a little each day
 * until it arrives at three in the morning, and nobody reports that as a bug — they
 * just stop using the app.
 */
class ReminderSchedulerTest {

    private val zone = TimeZone.currentSystemDefault()

    private fun at(hour: Int, minute: Int): Instant =
        LocalDateTime(LocalDate(2026, 9, 21), LocalTime(hour, minute)).toInstant(zone)

    private fun delayFrom(hour: Int, minute: Int, targetHour: Int): Long =
        ReminderScheduler.millisUntilNext(at(hour, minute), zone, targetHour)

    @Test
    fun `before the hour, the reminder lands later the same day`() {
        val delay = delayFrom(hour = 8, minute = 0, targetHour = 10)
        assertEquals("two hours", 2 * HOUR_MILLIS, delay)
    }

    @Test
    fun `after the hour, the reminder lands the next day`() {
        val delay = delayFrom(hour = 14, minute = 0, targetHour = 10)
        assertEquals("twenty hours", 20 * HOUR_MILLIS, delay)
    }

    @Test
    fun `exactly on the hour waits for tomorrow rather than firing twice`() {
        val delay = delayFrom(hour = 10, minute = 0, targetHour = 10)
        assertEquals("a full day", 24 * HOUR_MILLIS, delay)
    }

    @Test
    fun `a minute before the hour waits only a minute`() {
        val delay = delayFrom(hour = 9, minute = 59, targetHour = 10)
        assertEquals(MINUTE_MILLIS, delay)
    }

    @Test
    fun `the delay is never negative for any hour of the day`() {
        for (nowHour in 0..23) {
            for (targetHour in 0..23) {
                val delay = delayFrom(nowHour, 30, targetHour)
                assertTrue("negative delay at now=$nowHour target=$targetHour", delay >= 0)
                assertTrue("delay exceeds a day at now=$nowHour target=$targetHour", delay <= 24 * HOUR_MILLIS)
            }
        }
    }

    /**
     * The interval is daily and the protocol decides whether to speak. Anything more
     * frequent trains the user to dismiss it, and then the app is not there on the day
     * the booster block opens eleven months later.
     */
    @Test
    fun `the check runs once a day`() {
        assertEquals(24L, ReminderScheduler.REPEAT_INTERVAL_HOURS)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    }
}
