package com.routeforge.simulation.presentation

import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.LastComputedRouteHolder
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.holder.PendingTeleportTargetHolder
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.RealLocation
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.coredomain.usecase.ObserveMapStyleUseCase
import com.routeforge.simulation.domain.JoystickStartFailure
import com.routeforge.simulation.domain.model.ExecutionMode
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationSession
import com.routeforge.simulation.domain.model.SimulationStatus
import com.routeforge.simulation.domain.model.SpeedSetting
import com.routeforge.simulation.domain.usecase.ConfirmJoystickInterruptUseCase
import com.routeforge.simulation.domain.usecase.IsNetworkConnectedUseCase
import com.routeforge.simulation.domain.usecase.ObserveAuthorizationLostUseCase
import com.routeforge.simulation.domain.usecase.ObserveMockedSessionUseCase
import com.routeforge.simulation.domain.usecase.ObserveRealLocationUseCase
import com.routeforge.simulation.domain.usecase.PauseSimulationUseCase
import com.routeforge.simulation.domain.usecase.RequestJoystickInterruptUseCase
import com.routeforge.simulation.domain.usecase.ResumeSimulationUseCase
import com.routeforge.simulation.domain.usecase.SetSpeedUseCase
import com.routeforge.simulation.domain.usecase.StartRouteSimulationUseCase
import com.routeforge.simulation.domain.usecase.StopSimulationUseCase
import com.routeforge.simulation.domain.usecase.TeleportUseCase
import com.routeforge.simulation.domain.usecase.UpdateActiveRouteUseCase
import com.routeforge.simulation.domain.usecase.UpdateJoystickDirectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val sampleRoute =
    Route(
        points = emptyList(),
        geometry = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0)),
        distanceMeters = 100.0,
    )

private val routeWithAlternate =
    sampleRoute.copy(
        mode = RoutePlaybackMode.GUIDED,
        alternateGeometry = listOf(LatLng(2.0, 2.0), LatLng(3.0, 3.0)),
        alternateDistanceMeters = 50.0,
    )

private fun stationarySession(
    latitude: Double = 10.0,
    longitude: Double = 20.0,
) = SimulationSession(
    mode = SimulationMode.STATIONARY,
    status = SimulationStatus.RUNNING,
    latitude = latitude,
    longitude = longitude,
    bearingDegrees = 0f,
    speedMetersPerSecond = 0f,
    route = null,
    distanceTraveledMeters = 0.0,
)

class SimulationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = FakeSimulationController()
    private val authorizationChecker = FakeMockLocationAuthorizationChecker()
    private val lastComputedRouteHolder = LastComputedRouteHolder()
    private val realLocationDataSource = FakeRealLocationDataSource()
    private val networkConnectivityChecker = FakeNetworkConnectivityChecker()
    private val lastKnownRealLocationHolder = LastKnownRealLocationHolder()
    private val pendingTeleportTargetHolder = PendingTeleportTargetHolder()
    private val favoriteRoutesRepository = FakeFavoriteRoutesRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SimulationViewModel =
        SimulationViewModel(
            teleportUseCase = TeleportUseCase(controller),
            startRouteSimulationUseCase = StartRouteSimulationUseCase(controller),
            pauseSimulationUseCase = PauseSimulationUseCase(controller),
            resumeSimulationUseCase = ResumeSimulationUseCase(controller),
            stopSimulationUseCase = StopSimulationUseCase(controller),
            observeMockedSessionUseCase = ObserveMockedSessionUseCase(controller),
            observeRealLocationUseCase = ObserveRealLocationUseCase(realLocationDataSource),
            observeAuthorizationLostUseCase = ObserveAuthorizationLostUseCase(controller),
            isNetworkConnectedUseCase = IsNetworkConnectedUseCase(networkConnectivityChecker),
            setSpeedUseCase = SetSpeedUseCase(controller),
            requestJoystickInterruptUseCase = RequestJoystickInterruptUseCase(controller),
            confirmJoystickInterruptUseCase = ConfirmJoystickInterruptUseCase(controller),
            updateJoystickDirectionUseCase = UpdateJoystickDirectionUseCase(controller),
            updateActiveRouteUseCase = UpdateActiveRouteUseCase(controller),
            mockLocationAuthorizationChecker = authorizationChecker,
            lastComputedRouteHolder = lastComputedRouteHolder,
            lastKnownRealLocationHolder = lastKnownRealLocationHolder,
            pendingTeleportTargetHolder = pendingTeleportTargetHolder,
            observeMapStyle = ObserveMapStyleUseCase(FakeMapStyleStore()),
            favoriteRoutesRepository = favoriteRoutesRepository,
        )

    @Test
    fun `tapping the map sets a pending teleport point without calling the controller`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))

        assertEquals(10.0, viewModel.state.value.pendingTeleportLatitude)
        assertEquals(20.0, viewModel.state.value.pendingTeleportLongitude)
        assertTrue(controller.teleportCalls.isEmpty())
    }

    @Test
    fun `confirming a pending teleport calls the controller and clears the pending point`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))

        viewModel.onAction(SimulationAction.OnConfirmTeleport)

        assertEquals(1, controller.teleportCalls.size)
        assertEquals(10.0, controller.teleportCalls.first().latitude)
        assertEquals(20.0, controller.teleportCalls.first().longitude)
        assertNull(viewModel.state.value.pendingTeleportLatitude)
        assertNull(viewModel.state.value.pendingTeleportLongitude)
    }

    @Test
    fun `canceling a pending teleport clears it without calling the controller`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))

        viewModel.onAction(SimulationAction.OnCancelTeleport)

        assertTrue(controller.teleportCalls.isEmpty())
        assertNull(viewModel.state.value.pendingTeleportLatitude)
        assertNull(viewModel.state.value.pendingTeleportLongitude)
    }

    @Test
    fun `confirming a teleport while unauthorized reports an error and never calls the controller`() {
        authorizationChecker.authorized = false
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))

        viewModel.onAction(SimulationAction.OnConfirmTeleport)

        assertTrue(controller.teleportCalls.isEmpty())
        assertTrue(viewModel.state.value.isBlockedByAuthorization)
        assertEquals(SimulationErrorType.NOT_AUTHORIZED, viewModel.state.value.errorType)
    }

    @Test
    fun `a value appearing in the pending teleport target holder teleports immediately without a confirmation sheet`() {
        val viewModel = createViewModel()

        pendingTeleportTargetHolder.set(RoutePoint(latitude = 5.0, longitude = 6.0))

        assertEquals(1, controller.teleportCalls.size)
        assertEquals(5.0, controller.teleportCalls.first().latitude)
        assertEquals(6.0, controller.teleportCalls.first().longitude)
        assertNull(viewModel.state.value.pendingTeleportLatitude)
        assertNull(viewModel.state.value.pendingTeleportLongitude)
        assertNull(pendingTeleportTargetHolder.target.value)
    }

    @Test
    fun `a holder-driven teleport while unauthorized reports an error and never calls the controller`() {
        authorizationChecker.authorized = false
        val viewModel = createViewModel()

        pendingTeleportTargetHolder.set(RoutePoint(latitude = 5.0, longitude = 6.0))

        assertTrue(controller.teleportCalls.isEmpty())
        assertTrue(viewModel.state.value.isBlockedByAuthorization)
        assertEquals(SimulationErrorType.NOT_AUTHORIZED, viewModel.state.value.errorType)
    }

    @Test
    fun `a favorite teleport while a route is loaded asks for confirmation instead of teleporting immediately`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()

        pendingTeleportTargetHolder.set(RoutePoint(latitude = 5.0, longitude = 6.0))

        assertTrue(controller.teleportCalls.isEmpty())
        assertEquals(RoutePoint(latitude = 5.0, longitude = 6.0), viewModel.state.value.pendingFavoriteTeleportTarget)
        assertNotNull(viewModel.state.value.loadedRoute)
        assertNull(pendingTeleportTargetHolder.target.value)
    }

    @Test
    fun `confirming a favorite teleport cancels the loaded route and teleports`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        pendingTeleportTargetHolder.set(RoutePoint(latitude = 5.0, longitude = 6.0))

        viewModel.onAction(SimulationAction.OnConfirmFavoriteTeleportCancelRoute)

        assertEquals(1, controller.teleportCalls.size)
        assertEquals(5.0, controller.teleportCalls.first().latitude)
        assertEquals(6.0, controller.teleportCalls.first().longitude)
        assertNull(viewModel.state.value.loadedRoute)
        assertNull(viewModel.state.value.pendingFavoriteTeleportTarget)
    }

    @Test
    fun `dismissing a favorite teleport confirmation keeps the route loaded and teleports nowhere`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        pendingTeleportTargetHolder.set(RoutePoint(latitude = 5.0, longitude = 6.0))

        viewModel.onAction(SimulationAction.OnDismissFavoriteTeleportCancelRoute)

        assertTrue(controller.teleportCalls.isEmpty())
        assertNotNull(viewModel.state.value.loadedRoute)
        assertNull(viewModel.state.value.pendingFavoriteTeleportTarget)
    }

    // --- Route mode switching (Guided <-> Free-roam) ---

    @Test
    fun `clicking switch mode with no alternate available does nothing`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        assertNull(viewModel.state.value.pendingRouteModeSwitch)
    }

    @Test
    fun `clicking switch mode on a guided route with an alternate proposes switching to free-roam`() {
        lastComputedRouteHolder.set(routeWithAlternate)
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        assertEquals(RoutePlaybackMode.FREE_ROAM, viewModel.state.value.pendingRouteModeSwitch)
    }

    @Test
    fun `clicking switch mode on a free-roam route with an alternate proposes switching to guided`() {
        lastComputedRouteHolder.set(routeWithAlternate.copy(mode = RoutePlaybackMode.FREE_ROAM))
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        assertEquals(RoutePlaybackMode.GUIDED, viewModel.state.value.pendingRouteModeSwitch)
    }

    @Test
    fun `confirming the switch swaps geometry and distance reciprocally and updates the loaded route`() {
        lastComputedRouteHolder.set(routeWithAlternate)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        viewModel.onAction(SimulationAction.OnConfirmSwitchRouteMode)

        val route = viewModel.state.value.loadedRoute
        assertEquals(RoutePlaybackMode.FREE_ROAM, route?.mode)
        assertEquals(routeWithAlternate.alternateGeometry, route?.geometry)
        assertEquals(routeWithAlternate.alternateDistanceMeters, route?.distanceMeters)
        assertEquals(routeWithAlternate.geometry, route?.alternateGeometry)
        assertEquals(routeWithAlternate.distanceMeters, route?.alternateDistanceMeters)
        assertNull(viewModel.state.value.pendingRouteModeSwitch)
    }

    @Test
    fun `confirming the switch while a route session is active also updates the controller's live session`() {
        lastComputedRouteHolder.set(routeWithAlternate)
        val viewModel = createViewModel()
        controller.emit(stationarySession().copy(mode = SimulationMode.ROUTE, route = routeWithAlternate))
        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        viewModel.onAction(SimulationAction.OnConfirmSwitchRouteMode)

        assertEquals(1, controller.updateActiveRouteCalls.size)
        assertEquals(RoutePlaybackMode.FREE_ROAM, controller.updateActiveRouteCalls.first().mode)
    }

    @Test
    fun `dismissing the switch leaves the loaded route unchanged`() {
        lastComputedRouteHolder.set(routeWithAlternate)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnSwitchRouteModeClick)

        viewModel.onAction(SimulationAction.OnDismissSwitchRouteMode)

        assertNull(viewModel.state.value.pendingRouteModeSwitch)
        assertEquals(routeWithAlternate, viewModel.state.value.loadedRoute)
        assertTrue(controller.updateActiveRouteCalls.isEmpty())
    }

    @Test
    fun `tapping play with nothing running opens the start route dialog without starting anything`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnStartRouteSimulation)

        assertTrue(viewModel.state.value.isStartRouteDialogOpen)
        assertTrue(controller.startRouteCalls.isEmpty())
    }

    @Test
    fun `dismissing the start route dialog closes it without starting anything`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)

        viewModel.onAction(SimulationAction.OnDismissStartRouteDialog)

        assertTrue(!viewModel.state.value.isStartRouteDialogOpen)
        assertTrue(controller.startRouteCalls.isEmpty())
    }

    @Test
    fun `confirming the start route dialog while unauthorized reports a distinct error, closes the dialog, and never calls the controller`() {
        authorizationChecker.authorized = false
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)

        viewModel.onAction(SimulationAction.OnConfirmStartRoute)

        assertTrue(controller.startRouteCalls.isEmpty())
        assertTrue(!viewModel.state.value.isStartRouteDialogOpen)
        assertTrue(viewModel.state.value.isBlockedByAuthorization)
        assertEquals(SimulationErrorType.NOT_AUTHORIZED, viewModel.state.value.errorType)
    }

    @Test
    fun `resuming the screen while still authorized does not navigate away`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val receivedEvents = mutableListOf<SimulationEvent>()
            val collectJob = launch { viewModel.events.toList(receivedEvents) }

            viewModel.onAction(SimulationAction.OnScreenResumed)

            assertTrue(receivedEvents.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `resuming the screen after authorization was revoked navigates back to setup`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val eventDeferred = async { viewModel.events.first() }

            authorizationChecker.authorized = false
            viewModel.onAction(SimulationAction.OnScreenResumed)

            assertEquals(SimulationEvent.NavigateToSetup, eventDeferred.await())
        }

    @Test
    fun `the controller reporting authorization lost mid-session navigates back to setup immediately`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val eventDeferred = async { viewModel.events.first() }

            controller.emitAuthorizationLost()

            assertEquals(SimulationEvent.NavigateToSetup, eventDeferred.await())
        }

    @Test
    fun `confirming the start route dialog with a loaded route and valid speed calls the controller`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnPlaybackSpeedChange(18f))
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)

        viewModel.onAction(SimulationAction.OnConfirmStartRoute)

        assertTrue(!viewModel.state.value.isStartRouteDialogOpen)
        assertEquals(1, controller.startRouteCalls.size)
        assertEquals(sampleRoute, controller.startRouteCalls.first().route)
        assertEquals(5.0f, controller.startRouteCalls.first().speedSetting.metersPerSecond, 0.001f)
        assertEquals(ExecutionMode.Once, controller.startRouteCalls.first().executionMode)
    }

    @Test
    fun `pause resume and stop actions delegate directly to the controller`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnPauseSimulation)
        viewModel.onAction(SimulationAction.OnResumeSimulation)
        viewModel.onAction(SimulationAction.OnStopSimulation)

        assertEquals(1, controller.pauseCallCount)
        assertEquals(1, controller.resumeCallCount)
        assertEquals(1, controller.stopCallCount)
    }

    @Test
    fun `clicking cancel mock sets a pending confirmation without stopping the controller`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnCancelMockClick)

        assertTrue(viewModel.state.value.isPendingCancelMock)
        assertEquals(0, controller.stopCallCount)
    }

    @Test
    fun `confirming cancel mock stops the controller and clears the pending confirmation`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnCancelMockClick)

        viewModel.onAction(SimulationAction.OnConfirmCancelMock)

        assertEquals(1, controller.stopCallCount)
        assertTrue(!viewModel.state.value.isPendingCancelMock)
    }

    @Test
    fun `dismissing cancel mock clears the pending confirmation without stopping the controller`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnCancelMockClick)

        viewModel.onAction(SimulationAction.OnDismissCancelMock)

        assertEquals(0, controller.stopCallCount)
        assertTrue(!viewModel.state.value.isPendingCancelMock)
    }

    @Test
    fun `clicking cancel route sets a pending confirmation without clearing the route`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnCancelRouteClick)

        assertTrue(viewModel.state.value.isPendingCancelRoute)
        assertEquals(0, controller.stopCallCount)
        assertNotNull(viewModel.state.value.loadedRoute)
    }

    @Test
    fun `confirming cancel route stops the controller, clears the route, and closes the confirmation`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnCancelRouteClick)

        viewModel.onAction(SimulationAction.OnStopSimulation)

        assertEquals(1, controller.stopCallCount)
        assertTrue(!viewModel.state.value.isPendingCancelRoute)
        assertNull(viewModel.state.value.loadedRoute)
    }

    @Test
    fun `dismissing cancel route keeps the route and clears the pending confirmation`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnCancelRouteClick)

        viewModel.onAction(SimulationAction.OnDismissCancelRoute)

        assertEquals(0, controller.stopCallCount)
        assertTrue(!viewModel.state.value.isPendingCancelRoute)
        assertNotNull(viewModel.state.value.loadedRoute)
    }

    @Test
    fun `tapping the map while offline blocks the pending teleport from being confirmed`() {
        networkConnectivityChecker.connected = false
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))
        assertTrue(viewModel.state.value.isPendingTeleportBlockedOffline)

        viewModel.onAction(SimulationAction.OnConfirmTeleport)

        assertTrue(controller.teleportCalls.isEmpty())
    }

    @Test
    fun `tapping the map while online never blocks the pending teleport`() {
        networkConnectivityChecker.connected = true
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))

        assertTrue(!viewModel.state.value.isPendingTeleportBlockedOffline)
    }

    @Test
    fun `on startup with no active mock session the real location is searched and cached`() {
        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 2.0)

        val viewModel = createViewModel()

        assertEquals(RealLocation(latitude = 1.0, longitude = 2.0), viewModel.state.value.realLocation)
        assertTrue(!viewModel.state.value.isSearchingRealLocation)
    }

    @Test
    fun `real location keeps updating continuously while no mock is active`() {
        val viewModel = createViewModel()
        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 1.0)
        assertEquals(RealLocation(latitude = 1.0, longitude = 1.0), viewModel.state.value.realLocation)

        realLocationDataSource.location = RealLocation(latitude = 2.0, longitude = 2.0)

        assertEquals(RealLocation(latitude = 2.0, longitude = 2.0), viewModel.state.value.realLocation)
    }

    @Test
    fun `stopping an active mock session triggers a fresh real location search`() {
        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 2.0)
        val viewModel = createViewModel()
        controller.emit(stationarySession())
        realLocationDataSource.location = RealLocation(latitude = 3.0, longitude = 4.0)

        controller.emit(null)

        assertEquals(RealLocation(latitude = 3.0, longitude = 4.0), viewModel.state.value.realLocation)
    }

    @Test
    fun `real location updates while a mock is active are ignored`() {
        val viewModel = createViewModel()
        controller.emit(stationarySession())

        realLocationDataSource.location = RealLocation(latitude = 9.0, longitude = 9.0)

        assertNull(viewModel.state.value.realLocation)
    }

    @Test
    fun `granting location permission after an initial failure restarts the real location observation`() {
        realLocationDataSource.permissionGranted = false
        val viewModel = createViewModel()
        realLocationDataSource.permissionGranted = true
        realLocationDataSource.location = RealLocation(latitude = 5.0, longitude = 6.0)

        viewModel.onAction(SimulationAction.OnLocationPermissionGranted)

        assertEquals(RealLocation(latitude = 5.0, longitude = 6.0), viewModel.state.value.realLocation)
    }

    @Test
    fun `an unavailable real location signal surfaces a distinct error and stops searching`() {
        realLocationDataSource.permissionGranted = false

        val viewModel = createViewModel()

        assertTrue(!viewModel.state.value.isSearchingRealLocation)
        assertEquals(SimulationErrorType.REAL_LOCATION_PERMISSION_DENIED, viewModel.state.value.errorType)
    }

    @Test
    fun `the real location search times out and surfaces an error if nothing arrives`() {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.isSearchingRealLocation)

        dispatcher.scheduler.advanceTimeBy(30_001L)
        dispatcher.scheduler.runCurrent()

        assertTrue(!viewModel.state.value.isSearchingRealLocation)
        assertEquals(SimulationErrorType.REAL_LOCATION_TIMED_OUT, viewModel.state.value.errorType)
    }

    @Test
    fun `a fix arriving quickly cancels the pending timeout so no error appears later`() {
        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 2.0)
        val viewModel = createViewModel()

        dispatcher.scheduler.advanceTimeBy(30_001L)
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.state.value.errorType)
    }

    @Test
    fun `granting location permission while a mock is active does not search the real location`() {
        val viewModel = createViewModel()
        controller.emit(stationarySession())
        realLocationDataSource.location = RealLocation(latitude = 5.0, longitude = 6.0)

        viewModel.onAction(SimulationAction.OnLocationPermissionGranted)

        assertNull(viewModel.state.value.realLocation)
    }

    @Test
    fun `a fix arriving after a timeout clears the lingering real location error`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceTimeBy(30_001L)
        dispatcher.scheduler.runCurrent()
        assertEquals(SimulationErrorType.REAL_LOCATION_TIMED_OUT, viewModel.state.value.errorType)

        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 2.0)

        assertNull(viewModel.state.value.errorType)
    }

    @Test
    fun `an unrelated error is not cleared when the real location search resolves`() {
        authorizationChecker.authorized = false
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnMapTap(latitude = 10.0, longitude = 20.0))
        viewModel.onAction(SimulationAction.OnConfirmTeleport)
        assertEquals(SimulationErrorType.NOT_AUTHORIZED, viewModel.state.value.errorType)

        realLocationDataSource.location = RealLocation(latitude = 1.0, longitude = 2.0)
        assertEquals(SimulationErrorType.NOT_AUTHORIZED, viewModel.state.value.errorType)
    }

    // --- User Story 4: speed control ---

    @Test
    fun `changing the playback speed while a session is active applies it immediately in meters per second`() {
        val viewModel = createViewModel()
        controller.emit(stationarySession())

        viewModel.onAction(SimulationAction.OnPlaybackSpeedChange(36f))

        assertEquals(listOf(SpeedSetting.Manual(10f)), controller.setSpeedCalls)
        assertEquals(36f, viewModel.state.value.playbackSpeedKmh)
    }

    @Test
    fun `changing the playback speed with no active session only stages the value`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnPlaybackSpeedChange(36f))

        assertTrue(controller.setSpeedCalls.isEmpty())
        assertEquals(36f, viewModel.state.value.playbackSpeedKmh)
    }

    // --- User Story 6: execution mode ---

    @Test
    fun `starting a route with Times and an invalid count reports an error, never calls the controller, and keeps the dialog open`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)
        viewModel.onAction(SimulationAction.OnExecutionModeSelected(ExecutionModeSelection.TIMES))
        viewModel.onAction(SimulationAction.OnExecutionTimesInputChange("0"))

        viewModel.onAction(SimulationAction.OnConfirmStartRoute)

        assertTrue(controller.startRouteCalls.isEmpty())
        assertTrue(viewModel.state.value.isStartRouteDialogOpen)
        assertEquals(SimulationErrorType.INVALID_EXECUTION_TIMES, viewModel.state.value.errorType)
    }

    @Test
    fun `starting a route with a valid Times count passes it through to the controller`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)
        viewModel.onAction(SimulationAction.OnExecutionModeSelected(ExecutionModeSelection.TIMES))
        viewModel.onAction(SimulationAction.OnExecutionTimesInputChange("3"))

        viewModel.onAction(SimulationAction.OnConfirmStartRoute)

        assertEquals(ExecutionMode.Times(3), controller.startRouteCalls.first().executionMode)
    }

    @Test
    fun `starting a route with PingPong passes it through to the controller`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnStartRouteSimulation)
        viewModel.onAction(SimulationAction.OnExecutionModeSelected(ExecutionModeSelection.PING_PONG))

        viewModel.onAction(SimulationAction.OnConfirmStartRoute)

        assertEquals(ExecutionMode.PingPong, controller.startRouteCalls.first().executionMode)
    }

    // --- User Story 7: joystick (toggle-based) ---

    @Test
    fun `toggling the joystick on with nothing loaded or running reveals it immediately`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnToggleJoystick)

        assertTrue(viewModel.state.value.isJoystickVisible)
        assertTrue(!viewModel.state.value.isJoystickInterruptPending)
        assertEquals(0, controller.startJoystickCallCount)
    }

    @Test
    fun `toggling the joystick on while a route is only loaded, not yet playing, asks for confirmation`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnToggleJoystick)

        assertTrue(viewModel.state.value.isJoystickInterruptPending)
        assertTrue(!viewModel.state.value.isJoystickVisible)
    }

    @Test
    fun `toggling the joystick on while a route is actively playing asks for confirmation`() {
        val viewModel = createViewModel()
        controller.emit(stationarySession().copy(mode = SimulationMode.ROUTE, route = sampleRoute))

        viewModel.onAction(SimulationAction.OnToggleJoystick)

        assertTrue(viewModel.state.value.isJoystickInterruptPending)
        assertTrue(!viewModel.state.value.isJoystickVisible)
    }

    @Test
    fun `dismissing the interrupt leaves the loaded route and session untouched`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)

        viewModel.onAction(SimulationAction.OnDismissJoystickInterrupt)

        assertTrue(!viewModel.state.value.isJoystickInterruptPending)
        assertTrue(!viewModel.state.value.isJoystickVisible)
        assertEquals(sampleRoute, viewModel.state.value.loadedRoute)
        assertEquals(0, controller.stopCallCount)
    }

    @Test
    fun `confirming the interrupt clears the loaded route, stops any session, and reveals the joystick`() {
        lastComputedRouteHolder.set(sampleRoute)
        val viewModel = createViewModel()
        controller.emit(stationarySession().copy(mode = SimulationMode.ROUTE, route = sampleRoute))
        viewModel.onAction(SimulationAction.OnToggleJoystick)

        viewModel.onAction(SimulationAction.OnConfirmJoystickInterrupt)

        assertNull(viewModel.state.value.loadedRoute)
        assertEquals(1, controller.stopCallCount)
        assertTrue(viewModel.state.value.isJoystickVisible)
        assertTrue(!viewModel.state.value.isJoystickInterruptPending)
    }

    @Test
    fun `toggling an already-visible joystick off, while actively mocking, stops the session`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)
        controller.emit(stationarySession().copy(mode = SimulationMode.JOYSTICK))

        viewModel.onAction(SimulationAction.OnToggleJoystick)

        assertTrue(!viewModel.state.value.isJoystickVisible)
        assertEquals(1, controller.stopCallCount)
    }

    @Test
    fun `toggling an already-visible joystick off, before it was ever dragged, does not stop anything`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)

        viewModel.onAction(SimulationAction.OnToggleJoystick)

        assertTrue(!viewModel.state.value.isJoystickVisible)
        assertEquals(0, controller.stopCallCount)
    }

    @Test
    fun `the first drag after revealing the joystick starts the session`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)

        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 45f))

        assertEquals(1, controller.startJoystickCallCount)
        assertEquals(listOf(45f), controller.updateJoystickDirectionCalls)
    }

    @Test
    fun `dragging again after a release resumes the paused session instead of leaving it stuck`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)
        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 45f))
        controller.emit(stationarySession().copy(mode = SimulationMode.JOYSTICK, status = SimulationStatus.PAUSED))

        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 90f))

        assertEquals(1, controller.resumeCallCount)
        assertEquals(1, controller.startJoystickCallCount)
        assertEquals(listOf(45f, 90f), controller.updateJoystickDirectionCalls)
    }

    @Test
    fun `multiple drag events within the same gesture only start the joystick session once`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 10f))
        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 15f))
        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 20f))

        assertEquals(1, controller.startJoystickCallCount)
        assertEquals(listOf(10f, 15f, 20f), controller.updateJoystickDirectionCalls)
    }

    @Test
    fun `a failed drag-start surfaces the no-real-fix error and never updates direction`() {
        controller.startJoystickResult = Result.Error(JoystickStartFailure.NoRealLocationFixYet)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)

        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 0f))

        assertEquals(SimulationErrorType.JOYSTICK_NO_REAL_FIX, viewModel.state.value.errorType)
        assertTrue(controller.updateJoystickDirectionCalls.isEmpty())
    }

    @Test
    fun `releasing the joystick pauses the session in place`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnJoystickReleased)

        assertEquals(1, controller.pauseCallCount)
    }

    @Test
    fun `changing the joystick speed slider updates the label and applies live while active`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)
        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 0f))
        controller.emit(stationarySession().copy(mode = SimulationMode.JOYSTICK))
        controller.setSpeedCalls.clear()

        viewModel.onAction(SimulationAction.OnJoystickSpeedChange(kmh = 36f))

        assertEquals(36f, viewModel.state.value.joystickSpeedKmh)
        assertEquals(listOf(SpeedSetting.Manual(10f)), controller.setSpeedCalls)
    }

    @Test
    fun `starting the joystick session applies the current speed slider value`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnToggleJoystick)
        viewModel.onAction(SimulationAction.OnJoystickSpeedChange(kmh = 18f))
        controller.setSpeedCalls.clear()

        viewModel.onAction(SimulationAction.OnJoystickDrag(bearingDegrees = 0f))

        assertEquals(listOf(SpeedSetting.Manual(5f)), controller.setSpeedCalls)
    }

    @Test
    fun `applying a committed waypoint edit updates the loaded route and the live session`() {
        val viewModel = createViewModel()
        val edited = sampleRoute.copy(distanceMeters = 999.0)

        viewModel.applyEditedRoute(edited)

        assertEquals(edited, lastComputedRouteHolder.route.value)
        assertEquals(listOf(edited), controller.updateActiveRouteCalls)
    }

    @Test
    fun `an edit-triggered auto-pause request invokes the same pause path as the manual control`() {
        val viewModel = createViewModel()

        viewModel.onAction(SimulationAction.OnPauseSimulation)

        assertEquals(1, controller.pauseCallCount)
    }

    @Test
    fun `saving the loaded route persists every waypoint without changing playback and emits confirmation`() = runTest {
        val route = sampleRoute.copy(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(11.0, 21.0), RoutePoint(12.0, 22.0)))
        lastComputedRouteHolder.set(route)
        val viewModel = createViewModel()
        val confirmation = async { viewModel.events.first() }
        viewModel.onAction(SimulationAction.OnSaveRouteClick)
        viewModel.onAction(SimulationAction.OnRouteNameInputChange("  Beijing trial  "))
        viewModel.onAction(SimulationAction.OnConfirmSaveRoute)
        // A repeated confirmation after the sheet closes must not create a duplicate.
        viewModel.onAction(SimulationAction.OnConfirmSaveRoute)
        val saved = favoriteRoutesRepository.observeFavoriteRoutes().value.single()
        assertEquals("Beijing trial", saved.name)
        assertEquals(route.points, saved.points)
        assertEquals(route, lastComputedRouteHolder.route.value)
        assertEquals(0, controller.pauseCallCount)
        assertEquals(SimulationEvent.RouteSaved, confirmation.await())
        assertNull(viewModel.state.value.routeToSave)
    }

    @Test
    fun `dismissing save or retaining a route does not add a favorite`() {
        lastComputedRouteHolder.set(sampleRoute.copy(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(11.0, 21.0))))
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnSaveRouteClick)
        viewModel.onAction(SimulationAction.OnRouteNameInputChange("Unused"))
        viewModel.onAction(SimulationAction.OnDismissSaveRoute)
        viewModel.onAction(SimulationAction.OnCancelRouteClick)
        viewModel.onAction(SimulationAction.OnDismissCancelRoute)
        assertTrue(favoriteRoutesRepository.observeFavoriteRoutes().value.isEmpty())
        assertNull(viewModel.state.value.routeToSave)
        assertEquals("", viewModel.state.value.routeNameInput)
    }

    @Test
    fun `saving requires a loaded route and a nonblank name`() {
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnSaveRouteClick)
        viewModel.onAction(SimulationAction.OnRouteNameInputChange("No route"))
        viewModel.onAction(SimulationAction.OnConfirmSaveRoute)
        assertTrue(favoriteRoutesRepository.observeFavoriteRoutes().value.isEmpty())
        lastComputedRouteHolder.set(sampleRoute.copy(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(11.0, 21.0))))
        viewModel.onAction(SimulationAction.OnSaveRouteClick)
        viewModel.onAction(SimulationAction.OnRouteNameInputChange("  "))
        viewModel.onAction(SimulationAction.OnConfirmSaveRoute)
        assertTrue(favoriteRoutesRepository.observeFavoriteRoutes().value.isEmpty())
        assertNotNull(viewModel.state.value.routeToSave)
    }

    @Test
    fun `save sheet retains the route snapshot if the loaded route changes`() = runTest {
        val route = sampleRoute.copy(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(11.0, 21.0)))
        lastComputedRouteHolder.set(route)
        val viewModel = createViewModel()
        viewModel.onAction(SimulationAction.OnSaveRouteClick)
        lastComputedRouteHolder.set(route.copy(points = listOf(RoutePoint(30.0, 40.0), RoutePoint(31.0, 41.0))))
        viewModel.onAction(SimulationAction.OnRouteNameInputChange("Original"))
        viewModel.onAction(SimulationAction.OnConfirmSaveRoute)
        assertEquals(route.points, favoriteRoutesRepository.observeFavoriteRoutes().value.single().points)
    }
}
