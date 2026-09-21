/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {

    @Test
    fun `the forgetting curve passes through 90 percent at one stability`() {
        // This is the definition of stability. If it drifts, every interval is wrong.
        for (stability in listOf(0.5, 1.0, 7.0, 30.0, 365.0)) {
            val r = Fsrs.retrievability(elapsedDays = stability, stabilityDays = stability)
            assertEquals("R(S,S) must be 0.9 at S=$stability", 0.90, r, 1e-9)
        }
    }

    @Test
    fun `retrievability decreases monotonically with elapsed time`() {
        var previous = 1.1
        for (days in 0..120) {
            val r = Fsrs.retrievability(days.toDouble(), stabilityDays = 10.0)
            assertTrue("R must not increase at day $days", r < previous)
            previous = r
        }
    }

    @Test
    fun `interval inverts the forgetting curve`() {
        val stability = 20.0
        val policy = Fsrs.Policy(desiredRetention = 0.90, maximumIntervalDays = 10_000)
        val interval = Fsrs.intervalDays(stability, policy)
        // At 90% desired retention the interval is, by definition, the stability.
        assertEquals(20L, interval)
    }

    @Test
    fun `a higher retention target produces shorter intervals`() {
        val stability = 30.0
        val relaxed = Fsrs.intervalDays(stability, Fsrs.Policy(desiredRetention = 0.85, maximumIntervalDays = 10_000))
        val standard = Fsrs.intervalDays(stability, Fsrs.Policy(desiredRetention = 0.90, maximumIntervalDays = 10_000))
        val strict = Fsrs.intervalDays(stability, Fsrs.Policy(desiredRetention = 0.95, maximumIntervalDays = 10_000))

        assertTrue("0.85 -> $relaxed should exceed 0.90 -> $standard", relaxed > standard)
        assertTrue("0.90 -> $standard should exceed 0.95 -> $strict", standard > strict)
    }

    @Test
    fun `the HugMun policy clamps intervals to 45 days however stable the memory`() {
        val interval = Fsrs.intervalDays(stabilityDays = 5_000.0, policy = Fsrs.Policy())
        assertEquals(Fsrs.DEFAULT_MAXIMUM_INTERVAL_DAYS, interval)
    }

    @Test
    fun `the HugMun policy never schedules the same item twice in one day`() {
        val interval = Fsrs.intervalDays(stabilityDays = 0.01, policy = Fsrs.Policy())
        assertTrue("interval $interval must be at least one day", interval >= 1L)
    }

    @Test
    fun `initial stability follows the first grade`() {
        val again = Fsrs.initialState(Fsrs.Grade.AGAIN).stabilityDays
        val hard = Fsrs.initialState(Fsrs.Grade.HARD).stabilityDays
        val good = Fsrs.initialState(Fsrs.Grade.GOOD).stabilityDays
        val easy = Fsrs.initialState(Fsrs.Grade.EASY).stabilityDays

        assertTrue("stability should increase with the grade", again < hard && hard < good && good < easy)
    }

    @Test
    fun `initial difficulty decreases as the first grade improves`() {
        val again = Fsrs.initialState(Fsrs.Grade.AGAIN).difficulty
        val easy = Fsrs.initialState(Fsrs.Grade.EASY).difficulty
        assertTrue("an item answered easily should be rated easier, $again vs $easy", easy < again)
    }

    @Test
    fun `difficulty always stays inside its published bounds`() {
        var state = Fsrs.initialState(Fsrs.Grade.GOOD)
        val grades = listOf(Fsrs.Grade.AGAIN, Fsrs.Grade.HARD, Fsrs.Grade.GOOD, Fsrs.Grade.EASY)

        repeat(500) { index ->
            state = Fsrs.nextState(state, grades[index % grades.size], elapsedDays = 3.0)
            assertTrue(
                "difficulty ${state.difficulty} left [1, 10]",
                state.difficulty in Fsrs.MIN_DIFFICULTY..Fsrs.MAX_DIFFICULTY,
            )
            assertTrue("stability must stay positive", state.stabilityDays > 0.0)
        }
    }

    /**
     * The clamp HugMun adds on top of the published formula. Without it, a long-forgotten
     * item can come out of a lapse with more stability than it had going in, which would
     * mean forgetting something earns you a longer gap before seeing it again.
     */
    @Test
    fun `forgetting never increases stability`() {
        for (stability in listOf(1.0, 5.0, 20.0, 60.0, 200.0)) {
            for (elapsed in listOf(1.0, 10.0, 100.0, 400.0)) {
                val before = Fsrs.MemoryState(stabilityDays = stability, difficulty = 5.0)
                val after = Fsrs.nextState(before, Fsrs.Grade.AGAIN, elapsed)
                assertTrue(
                    "S=$stability elapsed=$elapsed produced ${after.stabilityDays}",
                    after.stabilityDays <= before.stabilityDays + 1e-9,
                )
            }
        }
    }

    @Test
    fun `a lapse is counted`() {
        val before = Fsrs.initialState(Fsrs.Grade.GOOD)
        val after = Fsrs.nextState(before, Fsrs.Grade.AGAIN, elapsedDays = 5.0)
        assertEquals(1, after.lapseCount)
        assertEquals(2, after.reviewCount)
    }

    @Test
    fun `remembering increases stability and so lengthens the next interval`() {
        var state = Fsrs.initialState(Fsrs.Grade.GOOD)
        var previousInterval = Fsrs.intervalDays(state.stabilityDays)

        repeat(6) {
            val elapsed = previousInterval.toDouble()
            state = Fsrs.nextState(state, Fsrs.Grade.GOOD, elapsed)
            val interval = Fsrs.intervalDays(state.stabilityDays)
            assertTrue(
                "interval should grow or hit the 45-day ceiling: $previousInterval -> $interval",
                interval >= previousInterval,
            )
            previousInterval = interval
        }
    }

    @Test
    fun `an easy answer yields more stability than a hard one`() {
        val base = Fsrs.MemoryState(stabilityDays = 10.0, difficulty = 5.0)
        val hard = Fsrs.nextState(base, Fsrs.Grade.HARD, elapsedDays = 10.0).stabilityDays
        val good = Fsrs.nextState(base, Fsrs.Grade.GOOD, elapsedDays = 10.0).stabilityDays
        val easy = Fsrs.nextState(base, Fsrs.Grade.EASY, elapsedDays = 10.0).stabilityDays

        assertTrue("hard=$hard good=$good easy=$easy", hard < good && good < easy)
    }

    @Test
    fun `grades are derived from behaviour rather than self report`() {
        assertEquals(Fsrs.Grade.AGAIN, Fsrs.gradeFor(recalledUnaided = false, cueLevel = 3, latencyMillis = 900))
        assertEquals(Fsrs.Grade.HARD, Fsrs.gradeFor(recalledUnaided = true, cueLevel = 0, latencyMillis = 900))
        assertEquals(Fsrs.Grade.HARD, Fsrs.gradeFor(recalledUnaided = true, cueLevel = 1, latencyMillis = 900))
        assertEquals(Fsrs.Grade.EASY, Fsrs.gradeFor(recalledUnaided = true, cueLevel = 3, latencyMillis = 900))
        assertEquals(Fsrs.Grade.GOOD, Fsrs.gradeFor(recalledUnaided = true, cueLevel = 3, latencyMillis = 4_000))
        assertEquals(Fsrs.Grade.HARD, Fsrs.gradeFor(recalledUnaided = true, cueLevel = 3, latencyMillis = 20_000))
    }

    @Test
    fun `the published parameter vector has the expected size`() {
        assertEquals(Fsrs.PARAMETER_COUNT, Fsrs.DEFAULT_PARAMETERS.w.size)
        val wrongSize = runCatching { Fsrs.Parameters(DoubleArray(5)) }.exceptionOrNull()
        assertTrue(wrongSize is IllegalArgumentException)
    }
}
