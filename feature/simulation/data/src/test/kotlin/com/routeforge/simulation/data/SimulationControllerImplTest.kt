package com.routeforge.simulation.data

import com.routeforge.coredomain.Result
import com.routeforge.coredomain.MockLocationAuthorizationChecker
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.RealLocation
import com.routeforge.coredomain.model.Route
import com.routeforge.simulation.domain.ExecutionModeFailure
import com.routeforge.simulation.domain.JoystickStartFailure
import com.routeforge.simulation.domain.model.ExecutionMode
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationStatus
import com.routeforge.simulation.domain.model.SpeedSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val longRoute =
    Route(
        points = emptyList(),
        geometry = listOf(LatLng(0.0, 0.0), LatLng(10.0, 0.0)),
        distanceMeters = 1_000_000.0,
    )

private val shortRoute =
    Route(
        points = emptyList(),
        geometry = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.0001)),
        distanceMeters = 11.0,
    )

class SimulationControllerImplTest {
    private val backgroundJob = Job().apply { cancel() }
    private val backgroundScope = CoroutineScope(backgroundJob)
    private val publisher = FakeMockLocationPublisher()
    private val authorizationChecker = FakeMockLocationAuthorizationChecker()
    private val lastKnownRealLocationHolder = LastKnownRealLocationHolder()

    private fun createController(): SimulationControllerImpl =
        SimulationControllerImpl(
            mockLocationPublisher = publisher,
            mockLocationAuthorizationChecker = authorizationChecker,
            lastKnownRealLocationHolder = lastKnownRealLocationHolder,
            coroutineScope = backgroundScope,
        )

    @Test
    fun `speed change received during a tick is retained and used`() {
        lateinit var controller: SimulationControllerImpl
        controller = SimulationControllerImpl(
            publisher,
            object : MockLocationAuthorizationChecker {
                override fun isAuthorized(): Boolean {
                    controller.setSpeed(SpeedSetting.Manual(20f))
                    return true
                }
            },
            coroutineScope = backgroundScope,
        )
        controller.startRoute(longRoute, SpeedSetting.Manual(10f))
        controller.tick(1.0)
        assertEquals(20f, controller.mockedSession.value?.speedMetersPerSecond)
        assertEquals(20.0, controller.mockedSession.value?.distanceTraveledMeters)
    }

    @Test
    fun `pause received during a tick cannot be undone by interpolation`() {
        lateinit var controller: SimulationControllerImpl
        controller = SimulationControllerImpl(
            publisher,
            object : MockLocationAuthorizationChecker {
                override fun isAuthorized(): Boolean {
                    controller.pause()
                    return true
                }
            },
            coroutineScope = backgroundScope,
        )
        controller.startRoute(longRoute, SpeedSetting.Manual(10f))
        controller.tick(1.0)
        assertEquals(SimulationStatus.PAUSED, controller.mockedSession.value?.status)
        assertEquals(0.0, controller.mockedSession.value?.distanceTraveledMeters)
    }

    @Test
    fun `stop received during a tick cannot recreate the session`() {
        lateinit var controller: SimulationControllerImpl
        controller = SimulationControllerImpl(
            publisher,
            object : MockLocationAuthorizationChecker {
                override fun isAuthorized(): Boolean {
                    controller.stop()
                    return true
                }
            },
            coroutineScope = backgroundScope,
        )
        controller.startRoute(longRoute, SpeedSetting.Manual(10f))
        controller.tick(1.0)
        assertNull(controller.mockedSession.value)
    }

    @Test
    fun `teleport immediately publishes the point once and reports a stationary running session`() {
        val controller = createController()

        controller.teleport(latitude = 12.0, longitude = 34.0)

        val mockedSession = controller.mockedSession.value
        assertEquals(SimulationMode.STATIONARY, mockedSession?.mode)
        assertEquals(SimulationStatus.RUNNING, mockedSession?.status)
        assertEquals(12.0, mockedSession?.latitude)
        assertEquals(34.0, mockedSession?.longitude)
        assertEquals(1, publisher.publishedFixes.size)
        assertEquals(12.0, publisher.publishedFixes.first().latitude)
    }

    @Test
    fun `startRoute advances distance traveled by speed times elapsed time on each tick`() {
        val controller = createController()

        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
        controller.tick(elapsedSeconds = 1.0)
        controller.tick(elapsedSeconds = 1.0)

        val mockedSession = controller.mockedSession.value
        assertEquals(SimulationMode.ROUTE, mockedSession?.mode)
        assertEquals(SimulationStatus.RUNNING, mockedSession?.status)
        assertEquals(20.0, mockedSession?.distanceTraveledMeters)
    }

    @Test
    fun `updateActiveRoute swaps the active route's geometry while preserving distance traveled`() {
        val controller = createController()
        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
        controller.tick(elapsedSeconds = 1.0)

        val alternateRoute = longRoute.copy(geometry = listOf(LatLng(0.0, 0.0), LatLng(0.0, 10.0)))
        controller.updateActiveRoute(alternateRoute)
        controller.tick(elapsedSeconds = 1.0)

        val mockedSession = controller.mockedSession.value
        assertEquals(alternateRoute, mockedSession?.route)
        assertEquals(20.0, mockedSession?.distanceTraveledMeters)
    }

    @Test
    fun `updateActiveRoute does nothing when no route session is active`() {
        val controller = createController()
        controller.teleport(latitude = 1.0, longitude = 2.0)

        controller.updateActiveRoute(longRoute)

        val mockedSession = controller.mockedSession.value
        assertEquals(SimulationMode.STATIONARY, mockedSession?.mode)
        assertNull(mockedSession?.route)
    }

    @Test
    fun `a route reaching its truly final completion keeps reporting the final point, then automatically ends once the completion-auto-stop delay elapses`() =
        runTest {
            val controller =
                SimulationControllerImpl(
                    mockLocationPublisher = publisher,
                    mockLocationAuthorizationChecker = authorizationChecker,
                    lastKnownRealLocationHolder = lastKnownRealLocationHolder,
                    coroutineScope = this,
                    completionAutoStopDelayMillis = 5_000L,
                )

            controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
            controller.tick(elapsedSeconds = 1.0)

            val completedSession = controller.mockedSession.value
            assertEquals(SimulationStatus.COMPLETED, completedSession?.status)
            assertEquals(0.0001, completedSession?.longitude)
            val distanceAtCompletion = completedSession?.distanceTraveledMeters

            controller.tick(elapsedSeconds = 1.0)
            controller.tick(elapsedSeconds = 1.0)

            val stillCompletedSession = controller.mockedSession.value
            assertEquals(SimulationStatus.COMPLETED, stillCompletedSession?.status)
            assertEquals(distanceAtCompletion, stillCompletedSession?.distanceTraveledMeters)
            assertEquals(0.0001, stillCompletedSession?.longitude)
            assertTrue(publisher.publishedFixes.size >= 3)

            advanceTimeBy(4_000)
            assertEquals(SimulationStatus.COMPLETED, controller.mockedSession.value?.status)

            advanceTimeBy(2_000) // total elapsed 6_000ms, past the 5_000ms completion-auto-stop delay
            assertNull(controller.mockedSession.value)
        }

    @Test
    fun `a manual stop before the completion-auto-stop delay elapses ends the session immediately, and the later-elapsing scheduled callback is a harmless no-op`() =
        runTest {
            val controller =
                SimulationControllerImpl(
                    mockLocationPublisher = publisher,
                    mockLocationAuthorizationChecker = authorizationChecker,
                    lastKnownRealLocationHolder = lastKnownRealLocationHolder,
                    coroutineScope = this,
                    completionAutoStopDelayMillis = 5_000L,
                )

            controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
            controller.tick(elapsedSeconds = 1.0)
            assertEquals(SimulationStatus.COMPLETED, controller.mockedSession.value?.status)

            controller.stop()
            assertNull(controller.mockedSession.value)

            advanceTimeBy(6_000) // past the scheduled delay — must not throw or revive the session
            assertNull(controller.mockedSession.value)
        }

    @Test
    fun `a Loop restart never schedules the completion-auto-stop timer, so the session stays alive well past the delay`() =
        runTest {
            val controller =
                SimulationControllerImpl(
                    mockLocationPublisher = publisher,
                    mockLocationAuthorizationChecker = authorizationChecker,
                    lastKnownRealLocationHolder = lastKnownRealLocationHolder,
                    coroutineScope = this,
                    completionAutoStopDelayMillis = 5_000L,
                )

            controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
            controller.setExecutionMode(ExecutionMode.Loop)
            controller.tick(elapsedSeconds = 1.0) // completes lap 1, restarts to RUNNING — never COMPLETED

            assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)

            advanceTimeBy(6_000) // past what would have been the completion-auto-stop delay, had one been scheduled
            assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
            assertTrue((controller.mockedSession.value?.completedRuns ?: 0) >= 1)
            controller.stop()
        }

    @Test
    fun `pause halts distance progression and resume continues from the paused distance`() {
        val controller = createController()

        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
        controller.tick(elapsedSeconds = 1.0)
        controller.pause()
        val distanceWhenPaused = controller.mockedSession.value?.distanceTraveledMeters

        controller.tick(elapsedSeconds = 1.0)
        assertEquals(SimulationStatus.PAUSED, controller.mockedSession.value?.status)
        assertEquals(distanceWhenPaused, controller.mockedSession.value?.distanceTraveledMeters)

        controller.resume()
        assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
        controller.tick(elapsedSeconds = 1.0)

        assertEquals((distanceWhenPaused ?: 0.0) + 10.0, controller.mockedSession.value?.distanceTraveledMeters)
    }

    @Test
    fun `stop clears the session and clears the mock location publisher`() {
        val controller = createController()
        controller.teleport(latitude = 1.0, longitude = 1.0)

        controller.stop()

        assertNull(controller.mockedSession.value)
        assertEquals(1, publisher.clearCallCount)
    }

    @Test
    fun `revoked authorization mid-simulation stops the session cleanly on the next tick`() {
        val controller = createController()
        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))

        authorizationChecker.authorized = false
        controller.tick(elapsedSeconds = 1.0)

        assertNull(controller.mockedSession.value)
        assertEquals(1, publisher.clearCallCount)
    }

    @Test
    fun `revoked authorization mid-simulation emits authorizationLost`() =
        runTest {
            val controller = createController()
            controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
            val authorizationLostDeferred = async { controller.authorizationLost.first() }
            runCurrent()

            authorizationChecker.authorized = false
            controller.tick(elapsedSeconds = 1.0)

            authorizationLostDeferred.await()
        }

    // --- User Story 4: speed control ---

    @Test
    fun `setSpeed updates the active session's effective speed immediately without resetting progress`() {
        val controller = createController()
        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
        controller.tick(elapsedSeconds = 1.0)
        val distanceBefore = controller.mockedSession.value?.distanceTraveledMeters

        controller.setSpeed(SpeedSetting.Manual(50f))

        assertEquals(distanceBefore, controller.mockedSession.value?.distanceTraveledMeters)
        assertEquals(50f, controller.mockedSession.value?.speedMetersPerSecond)
        controller.tick(elapsedSeconds = 1.0)
        assertEquals((distanceBefore ?: 0.0) + 50.0, controller.mockedSession.value?.distanceTraveledMeters)
    }

    // --- User Story 6: execution mode ---

    @Test
    fun `setExecutionMode rejects a non-positive Times count`() {
        val controller = createController()

        val result = controller.setExecutionMode(ExecutionMode.Times(0))

        assertEquals(Result.Error(ExecutionModeFailure.NonPositiveCount), result)
    }

    @Test
    fun `a route set to Times restarts automatically until the count is reached, then stops`() {
        val controller = createController()
        controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
        controller.setExecutionMode(ExecutionMode.Times(2))

        controller.tick(elapsedSeconds = 1.0) // completes lap 1, restarts
        assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
        assertEquals(1, controller.mockedSession.value?.completedRuns)
        assertEquals(0.0, controller.mockedSession.value?.distanceTraveledMeters)
        assertEquals(SpeedSetting.Manual(100f), controller.mockedSession.value?.speedSetting)

        controller.tick(elapsedSeconds = 1.0) // completes lap 2, stops (count reached)
        assertEquals(SimulationStatus.COMPLETED, controller.mockedSession.value?.status)
    }

    @Test
    fun `a route set to Loop restarts at least ten consecutive times without manual intervention`() {
        val controller = createController()
        controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
        controller.setExecutionMode(ExecutionMode.Loop)

        repeat(10) { controller.tick(elapsedSeconds = 1.0) }

        assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
        assertEquals(10, controller.mockedSession.value?.completedRuns)
        assertEquals(shortRoute.geometry.first().latitude, controller.mockedSession.value?.latitude)
    }

    @Test
    fun `a route set to PingPong reverses at each end instead of teleporting back to the start`() {
        val controller = createController()
        controller.startRoute(shortRoute, speedSetting = SpeedSetting.Manual(100f))
        controller.setExecutionMode(ExecutionMode.PingPong)

        controller.tick(elapsedSeconds = 1.0) // overshoots to the far end, reverses

        assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
        assertEquals(1, controller.mockedSession.value?.completedRuns)
        assertEquals(shortRoute.geometry.last().latitude, controller.mockedSession.value?.latitude)
        assertEquals(shortRoute.geometry.last().longitude, controller.mockedSession.value?.longitude)

        controller.tick(elapsedSeconds = 1.0) // overshoots back to the start, reverses again

        assertEquals(SimulationStatus.RUNNING, controller.mockedSession.value?.status)
        assertEquals(2, controller.mockedSession.value?.completedRuns)
        assertEquals(shortRoute.geometry.first().latitude, controller.mockedSession.value?.latitude)
        assertEquals(shortRoute.geometry.first().longitude, controller.mockedSession.value?.longitude)
    }

    // --- User Story 7: joystick ---

    @Test
    fun `startJoystick fails when no real fix has ever been observed and no session exists`() {
        val controller = createController()

        val result = controller.startJoystick()

        assertEquals(Result.Error(JoystickStartFailure.NoRealLocationFixYet), result)
        assertNull(controller.mockedSession.value)
    }

    @Test
    fun `startJoystick succeeds from the last known real location when no session exists`() {
        lastKnownRealLocationHolder.set(RealLocation(latitude = 5.0, longitude = 6.0))
        val controller = createController()

        val result = controller.startJoystick()

        assertEquals(Result.Success(Unit), result)
        assertEquals(SimulationMode.JOYSTICK, controller.mockedSession.value?.mode)
        assertEquals(5.0, controller.mockedSession.value?.latitude)
        assertEquals(6.0, controller.mockedSession.value?.longitude)
    }

    @Test
    fun `startJoystick while a route is active hands off from the route's current position`() {
        val controller = createController()
        controller.startRoute(longRoute, speedSetting = SpeedSetting.Manual(10f))
        controller.tick(elapsedSeconds = 1.0)
        val positionDuringRoute = controller.mockedSession.value!!.let { LatLng(it.latitude, it.longitude) }

        val result = controller.startJoystick()

        assertEquals(Result.Success(Unit), result)
        assertEquals(SimulationMode.JOYSTICK, controller.mockedSession.value?.mode)
        assertEquals(positionDuringRoute, controller.mockedSession.value?.let { LatLng(it.latitude, it.longitude) })
    }

    @Test
    fun `joystick moves continuously in the direction set by updateJoystickDirection`() {
        lastKnownRealLocationHolder.set(RealLocation(latitude = 0.0, longitude = 0.0))
        val controller = createController()
        controller.startJoystick()
        controller.setSpeed(SpeedSetting.Manual(10f))

        controller.updateJoystickDirection(bearingDegrees = 0f)
        controller.tick(elapsedSeconds = 1.0)

        val moved = controller.mockedSession.value!!
        assertTrue(moved.latitude > 0.0)
        assertEquals(0.0, moved.longitude, 0.0000001)
    }

    @Test
    fun `releasing the joystick via pause stops movement in place`() {
        lastKnownRealLocationHolder.set(RealLocation(latitude = 0.0, longitude = 0.0))
        val controller = createController()
        controller.startJoystick()
        controller.setSpeed(SpeedSetting.Manual(10f))
        controller.updateJoystickDirection(bearingDegrees = 0f)
        controller.tick(elapsedSeconds = 1.0)
        val positionAtRelease = controller.mockedSession.value!!.let { LatLng(it.latitude, it.longitude) }

        controller.pause()
        controller.tick(elapsedSeconds = 1.0)

        assertEquals(SimulationStatus.PAUSED, controller.mockedSession.value?.status)
        assertEquals(positionAtRelease, controller.mockedSession.value?.let { LatLng(it.latitude, it.longitude) })
    }
}
