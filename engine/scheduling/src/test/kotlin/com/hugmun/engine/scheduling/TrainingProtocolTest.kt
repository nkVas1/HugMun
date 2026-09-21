/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.scheduling

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The protocol is the intervention (see the class docs on [TrainingProtocol]), so these
 * tests assert on the schedule the way one would assert on a dosing regimen.
 */
class TrainingProtocolTest {

    private val enrolled = LocalDate(2026, 9, 21)

    @Test
    fun `the core block is the ten sessions ACTIVE used`() {
        val spec = TrainingProtocol.specFor(TrainingProtocol.Phase.CORE)
        assertEquals(TrainingProtocol.PhaseLength.Sessions(10), spec.length)
        assertEquals(TrainingProtocol.Provenance.TRIAL, spec.provenance)
    }

    @Test
    fun `each booster block is the four sessions ACTIVE used`() {
        for (phase in listOf(TrainingProtocol.Phase.BOOSTER_ONE, TrainingProtocol.Phase.BOOSTER_TWO)) {
            val spec = TrainingProtocol.specFor(phase)
            assertEquals(TrainingProtocol.PhaseLength.Sessions(4), spec.length)
            assertEquals(
                "$phase must be marked as coming from the trial",
                TrainingProtocol.Provenance.TRIAL,
                spec.provenance,
            )
        }
    }

    @Test
    fun `everything we invented is labelled as an extrapolation`() {
        val invented = TrainingProtocol.PHASES.filter { it.provenance == TrainingProtocol.Provenance.EXTRAPOLATION }
        val inventedPhases = invented.map { it.phase }.toSet()
        assertEquals(
            setOf(
                TrainingProtocol.Phase.MAINTENANCE_WEEKLY,
                TrainingProtocol.Phase.MAINTENANCE_MONTHLY,
                TrainingProtocol.Phase.MAINTENANCE_MONTHLY_TWO,
                TrainingProtocol.Phase.MAINTENANCE_ONGOING,
            ),
            inventedPhases,
        )
    }

    @Test
    fun `the very first session is due on the day of enrolment`() {
        val plan = TrainingProtocol.plan(TrainingProtocol.State(enrolledOn = enrolled), enrolled)
        assertEquals(enrolled, plan.dueOn)
        assertTrue(plan.isDue)
        assertEquals(1, plan.sessionNumberInPhase)
        assertEquals(10, plan.sessionsRemainingInPhase)
    }

    @Test
    fun `core sessions are spaced three days apart`() {
        var state = TrainingProtocol.State(enrolledOn = enrolled)
        state = TrainingProtocol.afterSession(state, enrolled)

        val plan = TrainingProtocol.plan(state, enrolled)
        assertEquals(enrolled.plus(3, DateTimeUnit.DAY), plan.dueOn)
        assertFalse("not due yet on the day of the previous session", plan.isDue)
    }

    @Test
    fun `a missed month produces one session, not a backlog`() {
        var state = TrainingProtocol.State(enrolledOn = enrolled)
        state = TrainingProtocol.afterSession(state, enrolled)

        val muchLater = enrolled.plus(40, DateTimeUnit.DAY)
        val plan = TrainingProtocol.plan(state, muchLater)

        assertTrue("the next session should simply be due", plan.isDue)
        assertEquals("still only the second session of the core block", 2, plan.sessionNumberInPhase)
    }

    @Test
    fun `completing ten core sessions moves on to weekly maintenance`() {
        var state = TrainingProtocol.State(enrolledOn = enrolled)
        var day = enrolled
        repeat(10) {
            state = TrainingProtocol.afterSession(state, day)
            day = day.plus(3, DateTimeUnit.DAY)
        }

        assertEquals(TrainingProtocol.Phase.MAINTENANCE_WEEKLY, state.phase)
        assertEquals(10, state.totalSessionsCompleted)
        assertEquals(0, state.sessionsCompletedInPhase)
    }

    @Test
    fun `the first booster cannot be scheduled before week 46`() {
        val state = TrainingProtocol.State(
            enrolledOn = enrolled,
            phase = TrainingProtocol.Phase.BOOSTER_ONE,
            lastSessionOn = enrolled.plus(200, DateTimeUnit.DAY),
        )
        val plan = TrainingProtocol.plan(state, enrolled.plus(210, DateTimeUnit.DAY))
        val windowOpens = enrolled.plus(TrainingProtocol.BOOSTER_ONE_WEEK * 7, DateTimeUnit.DAY)

        assertTrue("booster due ${plan.dueOn} must not precede $windowOpens", plan.dueOn >= windowOpens)
    }

    /**
     * The end-to-end check that matters: a person who simply does what the app asks,
     * when it asks, must actually arrive at both booster blocks — because in ACTIVE the
     * boosters are what separated HR 0.75 from HR 1.01.
     */
    @Test
    fun `following the schedule for three years reaches both booster blocks`() {
        var state = TrainingProtocol.State(enrolledOn = enrolled)
        var today = enrolled
        val horizon = enrolled.plus(365 * 4, DateTimeUnit.DAY)

        val phasesVisited = mutableSetOf(state.phase)
        var boosterOneSessions = 0
        var boosterTwoSessions = 0

        while (today < horizon) {
            val plan = TrainingProtocol.plan(state, today)
            if (plan.isDue) {
                when (plan.phase) {
                    TrainingProtocol.Phase.BOOSTER_ONE -> boosterOneSessions++
                    TrainingProtocol.Phase.BOOSTER_TWO -> boosterTwoSessions++
                    else -> Unit
                }
                state = TrainingProtocol.afterSession(state, today)
                phasesVisited += state.phase
            }
            today = today.plus(1, DateTimeUnit.DAY)
        }

        assertEquals("booster 1 must deliver four sessions", 4, boosterOneSessions)
        assertEquals("booster 2 must deliver four sessions", 4, boosterTwoSessions)
        assertTrue("booster 1 must have been visited", TrainingProtocol.Phase.BOOSTER_ONE in phasesVisited)
        assertTrue("booster 2 must have been visited", TrainingProtocol.Phase.BOOSTER_TWO in phasesVisited)
        assertTrue(
            "the protocol must recognise a completed booster block",
            TrainingProtocol.hasCompletedABoosterBlock(state),
        )
    }

    @Test
    fun `a booster block is not considered complete until all four sessions are done`() {
        val partway = TrainingProtocol.State(
            enrolledOn = enrolled,
            phase = TrainingProtocol.Phase.BOOSTER_ONE,
            sessionsCompletedInPhase = 3,
        )
        assertFalse(TrainingProtocol.hasCompletedABoosterBlock(partway))

        val done = partway.copy(sessionsCompletedInPhase = 4)
        assertTrue(TrainingProtocol.hasCompletedABoosterBlock(done))
    }

    @Test
    fun `every plan carries a reason the user can read`() {
        for (spec in TrainingProtocol.PHASES) {
            assertTrue("${spec.phase} has no rationale", spec.rationale.isNotBlank())
            assertTrue("${spec.phase} spacing must be positive", spec.spacingDays > 0)
        }
    }
}
