package com.routeforge.simulation.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HoldToRepeatTest {
    @Test
    fun `short touch leaves one click to the view and performs no repeats`() = runTest {
        var steps = 0
        val hold = HoldToRepeat(this)
        hold.start { steps++ }
        advanceTimeBy(449)
        assertFalse(hold.stop())
        advanceTimeBy(1000)
        assertEquals(0, steps)
    }

    @Test
    fun `hold repeats at ten steps per second and release adds no final click`() = runTest {
        var steps = 0
        val hold = HoldToRepeat(this)
        hold.start { steps++ }
        advanceTimeBy(450)
        runCurrent()
        assertEquals(1, steps)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(3, steps)
        assertTrue(hold.stop())
        advanceTimeBy(1000)
        assertEquals(3, steps)
    }

    @Test
    fun `new press cancels the previous button hold`() = runTest {
        var oldSteps = 0
        var newSteps = 0
        val hold = HoldToRepeat(this)
        hold.start { oldSteps++ }
        advanceTimeBy(400)
        hold.start { newSteps++ }
        advanceTimeBy(450)
        runCurrent()
        assertEquals(0, oldSteps)
        assertEquals(1, newSteps)
        hold.stop()
    }

    @Test
    fun `destroying the overlay scope prevents further speed changes`() = runTest {
        var steps = 0
        val job = Job()
        val hold = HoldToRepeat(CoroutineScope(StandardTestDispatcher(testScheduler) + job))
        hold.start { steps++ }
        advanceTimeBy(450)
        runCurrent()
        assertEquals(1, steps)
        job.cancel()
        advanceTimeBy(1000)
        assertEquals(1, steps)
        hold.stop()
    }
}
