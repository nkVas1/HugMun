/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The eight response directions.
 *
 * These exist because a defect found on a real device — eight targets sharing four
 * spoken labels — could have been caught here. Anything that maps a direction to
 * something a person perceives now has to be one-to-one, and the test says so.
 */
class DirectionTest {

    @Test
    fun `there are eight directions, forty-five degrees apart`() {
        assertEquals(8, Direction.COUNT)
        assertEquals(8, Direction.ALL.size)

        Direction.ALL.zipWithNext().forEach { (a, b) ->
            assertEquals("directions must be evenly spaced", 45f, b.degrees - a.degrees, 1e-4f)
        }
    }

    @Test
    fun `direction zero points straight up and the set covers the full circle`() {
        assertEquals(0f, Direction(0).degrees, 1e-6f)
        assertEquals(180f, Direction(4).degrees, 1e-6f)
        assertTrue(Direction.ALL.all { it.degrees >= 0f && it.degrees < 360f })
    }

    /**
     * Any labelling of a direction must be injective.
     *
     * The clock-position scheme this replaced was not: at 30° per hour it cannot
     * represent targets 45° apart, and rounding collapsed eight positions onto four.
     */
    @Test
    fun `a clock-hour labelling cannot represent eight directions`() {
        val clockHours = Direction.ALL.map { ((it.index * 3) % 12).let { h -> if (h == 0) 12 else h } }
        assertTrue(
            "clock hours collapse eight directions onto ${clockHours.toSet().size}",
            clockHours.toSet().size < Direction.COUNT,
        )
    }

    @Test
    fun `an out-of-range direction is rejected rather than wrapped`() {
        assertTrue(runCatching { Direction(8) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { Direction(-1) }.exceptionOrNull() is IllegalArgumentException)
    }
}
