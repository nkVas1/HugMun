/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Time as a dependency.
 *
 * The training protocol reasons in local calendar days across a horizon of years, so
 * "what day is it" is a domain question rather than an incidental one. Tests drive it
 * directly; nothing calls `System.currentTimeMillis()`.
 */
public interface TimeSource {
    public fun now(): Instant
    public fun timeZone(): TimeZone

    public fun today(): LocalDate = now().toLocalDateTime(timeZone()).date
}

/** The real clock. */
public class SystemTimeSource(
    private val clock: Clock = Clock.System,
) : TimeSource {
    override fun now(): Instant = clock.now()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

/**
 * A monotonic nanosecond source, separate from wall-clock time.
 *
 * Stimulus timing must never be measured with a clock that can jump backwards when the
 * network updates the time, so the two are different interfaces on purpose.
 */
public fun interface MonotonicNanos {
    public fun nanos(): Long
}
