package com.routeforge.simulation.data

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimulationPowerKeeperTest {
    @Test
    fun `idle simulation takes no wake lock`() = runTest {
        var acquisitions = 0
        var releases = 0
        val keeper = SimulationPowerKeeper(this, { acquisitions++ }, { releases++ })
        keeper.setActive(false)
        advanceTimeBy(600_000)
        keeper.close()
        assertEquals(0, acquisitions)
        assertEquals(0, releases)
    }

    @Test
    fun `moving simulation renews bounded lease before it expires`() = runTest {
        val leases = mutableListOf<Long>()
        val keeper = SimulationPowerKeeper(this, { leases.add(it) }, {})
        keeper.setActive(true)
        runCurrent()
        assertEquals(listOf(600_000L), leases)
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(listOf(600_000L, 600_000L), leases)
        keeper.close()
    }

    @Test
    fun `pause or stop releases the lock and cancels renewals`() = runTest {
        var acquisitions = 0
        var releases = 0
        val keeper = SimulationPowerKeeper(this, { acquisitions++ }, { releases++ })
        keeper.setActive(true)
        runCurrent()
        keeper.setActive(false)
        advanceTimeBy(1_000_000)
        assertEquals(1, acquisitions)
        assertEquals(1, releases)
        keeper.close()
        assertEquals(1, releases)
    }

    @Test
    fun `repeated running updates and destroy cannot leave duplicate leases`() = runTest {
        var acquisitions = 0
        var releases = 0
        val keeper = SimulationPowerKeeper(this, { acquisitions++ }, { releases++ })
        keeper.setActive(true)
        keeper.setActive(true)
        runCurrent()
        assertEquals(1, acquisitions)
        keeper.close()
        keeper.close()
        keeper.setActive(true)
        advanceTimeBy(1_000_000)
        assertEquals(1, acquisitions)
        assertEquals(1, releases)
    }
}
