package com.routeforge.simulation.presentation.waypointedit

import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.Region
import com.routeforge.coredomain.model.RegionStatus
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.coredomain.usecase.ComputeEditableWaypointRangeUseCase
import com.routeforge.coredomain.usecase.ComputeRequiredRegionsUseCase
import com.routeforge.coredomain.usecase.ComputeRouteUseCase
import com.routeforge.coredomain.usecase.FreeRoamRouteBuilder
import com.routeforge.coredomain.usecase.PrepareRouteOptionsUseCase
import com.routeforge.coredomain.usecase.RecomputeRouteForBothModesUseCase
import com.routeforge.coredomain.usecase.RecordRegionUsageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WaypointEditViewModelTest {
    private val routingEngine = FakeRoutingEngine()
    private val regionCatalog = FakeRegionCatalog()
    private val computeRoute = ComputeRouteUseCase(routingEngine, regionCatalog, RecordRegionUsageUseCase(FakeRegionUsageTracker()))
    private val recomputeRouteForBothModes =
        RecomputeRouteForBothModesUseCase(
            ComputeRequiredRegionsUseCase(regionCatalog),
            PrepareRouteOptionsUseCase(computeRoute, FreeRoamRouteBuilder()),
            computeRoute,
        )
    private val computeEditableWaypointRange = ComputeEditableWaypointRangeUseCase()
    private val favoriteWaypointsRepository = FakeFavoriteWaypointsRepository()
    private val viewModel = WaypointEditViewModel(recomputeRouteForBothModes, computeEditableWaypointRange, favoriteWaypointsRepository)

    private val points =
        listOf(
            RoutePoint(latitude = 0.0, longitude = 0.0),
            RoutePoint(latitude = 1.0, longitude = 0.0),
            RoutePoint(latitude = 2.0, longitude = 0.0),
        )
    private val loadedRoute =
        Route(
            points = points,
            geometry = points.map { LatLng(it.latitude, it.longitude) },
            distanceMeters = 200.0,
            mode = RoutePlaybackMode.FREE_ROAM,
            waypointCumulativeDistances = listOf(0.0, 100.0, 200.0),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        routingEngine.computePathResult = Route(points = points, geometry = points.map { LatLng(it.latitude, it.longitude) }, distanceMeters = 1.0)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening while loaded-not-running makes every waypoint editable`() {
        viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = null))

        assertEquals(0, viewModel.state.value.firstEditableIndex)
        assertEquals(points, viewModel.state.value.points)
    }

    @Test
    fun `opening while a session is active computes the boundary from waypointCumulativeDistances`() {
        viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = 150.0))

        assertEquals(2, viewModel.state.value.firstEditableIndex)
        assertTrue(viewModel.state.value.isSessionActive)
    }

    @Test
    fun `reordering alone only updates the displayed order, without recomputing`() {
        viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = null))

        viewModel.onAction(WaypointEditAction.OnReorder(fromIndex = 0, toIndex = 2))

        assertEquals(listOf(points[1], points[2], points[0]), viewModel.state.value.points)
        assertTrue(viewModel.state.value.hasUnappliedReorder)
    }

    @Test
    fun `a successful reorder updates the working list and emits Committed only once applied`() =
        runTest {
            viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = null))
            viewModel.onAction(WaypointEditAction.OnReorder(fromIndex = 0, toIndex = 2))
            val eventDeferred = async { viewModel.events.first() }

            viewModel.onAction(WaypointEditAction.OnApplyChanges)

            val event = eventDeferred.await() as WaypointEditEvent.Committed
            assertEquals(listOf(points[1], points[2], points[0]), event.points)
            assertEquals(event.points, viewModel.state.value.points)
            assertFalse(viewModel.state.value.hasUnappliedReorder)
        }

    @Test
    fun `an active-mode failure reverts the working list and surfaces an error`() =
        runTest {
            // Free-roam (loadedRoute's mode) always succeeds via its own builder, so force the
            // active-mode failure path via Guided instead, whose geometry depends on routingEngine.
            routingEngine.computePathResult = null
            val guidedRoute = loadedRoute.copy(mode = RoutePlaybackMode.GUIDED)
            viewModel.onAction(WaypointEditAction.OnOpen(guidedRoute, distanceTraveledMeters = null))

            viewModel.onAction(WaypointEditAction.OnReorder(fromIndex = 0, toIndex = 2))
            viewModel.onAction(WaypointEditAction.OnApplyChanges)

            assertEquals(points, viewModel.state.value.points) // unchanged
            assertNotNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `missing regions surfaces the summary instead of applying`() =
        runTest {
            regionCatalog.regions =
                listOf(
                    Region(
                        id = "r1",
                        displayName = "Region 1",
                        minLatitude = -1.0,
                        minLongitude = -1.0,
                        maxLatitude = 3.0,
                        maxLongitude = 3.0,
                        tileIds = listOf("t1"),
                        approximateSizeBytes = 100L,
                        status = RegionStatus.NOT_DOWNLOADED,
                    ),
                )
            viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = null))

            viewModel.onAction(WaypointEditAction.OnReorder(fromIndex = 0, toIndex = 2))
            viewModel.onAction(WaypointEditAction.OnApplyChanges)

            assertNotNull(viewModel.state.value.missingRegionsWarning)
        }

    @Test
    fun `closing the sheet clears all edit-mode state`() {
        viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = null))

        viewModel.onAction(WaypointEditAction.OnClose)

        assertEquals(WaypointEditState(), viewModel.state.value)
    }

    @Test
    fun `a delete that would drop the editable count to 1 is rejected without a recompute`() =
        runTest {
            val twoPointRoute = loadedRoute.copy(points = points.take(2), waypointCumulativeDistances = listOf(0.0, 100.0))
            viewModel.onAction(WaypointEditAction.OnOpen(twoPointRoute, distanceTraveledMeters = null))

            viewModel.onAction(WaypointEditAction.OnDelete(0))

            assertEquals(2, viewModel.state.value.points.size) // unchanged — rejected before any recompute
        }

    @Test
    fun `an edit targeting a read-only index below the boundary is rejected`() {
        viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = 150.0)) // firstEditableIndex == 2

        viewModel.onAction(WaypointEditAction.OnMarkerDragged(index = 0, latitude = 9.0, longitude = 9.0))

        assertEquals(points, viewModel.state.value.points) // unchanged
    }

    @Test
    fun `a missing-regions result during an active session requests an auto-pause on go-to-downloads`() =
        runTest {
            regionCatalog.regions =
                listOf(
                    Region(
                        id = "r1",
                        displayName = "Region 1",
                        minLatitude = -1.0,
                        minLongitude = -1.0,
                        maxLatitude = 3.0,
                        maxLongitude = 3.0,
                        tileIds = listOf("t1"),
                        approximateSizeBytes = 100L,
                        status = RegionStatus.NOT_DOWNLOADED,
                    ),
                )
            viewModel.onAction(WaypointEditAction.OnOpen(loadedRoute, distanceTraveledMeters = 50.0))
            viewModel.onAction(WaypointEditAction.OnReorder(fromIndex = 1, toIndex = 2))
            viewModel.onAction(WaypointEditAction.OnApplyChanges)
            assertNotNull(viewModel.state.value.missingRegionsWarning)

            val events = mutableListOf<WaypointEditEvent>()
            val job = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onAction(WaypointEditAction.OnGoToDownloads)

            runCurrent()

            assertTrue(events.contains(WaypointEditEvent.GoToDownloads))
            assertTrue(events.contains(WaypointEditEvent.AutoPauseRequested))
            assertNull(viewModel.state.value.missingRegionsWarning)
            job.cancel()
        }
}
