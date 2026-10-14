package com.routeforge.routing.presentation.savedroutes

import com.routeforge.coredomain.holder.LastComputedRouteHolder
import com.routeforge.coredomain.model.FavoriteRoute
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.coredomain.usecase.FreeRoamRouteBuilder
import com.routeforge.coredomain.model.Region
import com.routeforge.coredomain.model.RegionStatus
import com.routeforge.coredomain.usecase.ComputeRequiredRegionsUseCase
import com.routeforge.coredomain.usecase.ComputeRouteUseCase
import com.routeforge.coredomain.usecase.PrepareRouteOptionsUseCase
import com.routeforge.coredomain.usecase.RecordRegionUsageUseCase
import com.routeforge.routing.presentation.FakeFavoriteRoutesRepository
import com.routeforge.routing.presentation.FakeRegionCatalog
import com.routeforge.routing.presentation.FakeRegionUsageTracker
import com.routeforge.routing.presentation.FakeRoutingEngine
import com.routeforge.routing.domain.usecase.ExportRouteFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val samplePoints = listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0))

private fun sampleFavoriteRoute(id: String = "route-1") = FavoriteRoute(id = id, name = "Morning loop", points = samplePoints)

class SavedRoutesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val routingEngine = FakeRoutingEngine()
    private val regionCatalog = FakeRegionCatalog()
    private val favoriteRoutesRepository = FakeFavoriteRoutesRepository(listOf(sampleFavoriteRoute()))
    private val lastComputedRouteHolder = LastComputedRouteHolder()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SavedRoutesViewModel =
        SavedRoutesViewModel(
            favoriteRoutesRepository = favoriteRoutesRepository,
            lastComputedRouteHolder = lastComputedRouteHolder,
            exportRouteFile = ExportRouteFileUseCase(emptyMap()),
            computeRequiredRegions = ComputeRequiredRegionsUseCase(regionCatalog),
            prepareRouteOptions =
                PrepareRouteOptionsUseCase(
                    computeRoute = ComputeRouteUseCase(routingEngine, regionCatalog, RecordRegionUsageUseCase(FakeRegionUsageTracker())),
                    freeRoamRouteBuilder = FreeRoamRouteBuilder(),
                ),
            backgroundDispatcher = dispatcher,
        )

    @Test
    fun `initial state reflects the repository's current list`() {
        val viewModel = createViewModel()

        assertEquals(listOf(sampleFavoriteRoute()), viewModel.state.value.routes)
    }

    @Test
    fun `using a route whose regions are not fully downloaded shows a warning instead of resolving`() {
        regionCatalog.regions =
            listOf(
                Region(
                    id = "north-zone",
                    displayName = "North Zone",
                    minLatitude = 0.0,
                    minLongitude = 0.0,
                    maxLatitude = 5.0,
                    maxLongitude = 5.0,
                    tileIds = listOf("north.rd5"),
                    approximateSizeBytes = 1_000L,
                    status = RegionStatus.NOT_DOWNLOADED,
                    missingTileCount = 1,
                ),
            )
        val viewModel = createViewModel()

        viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

        assertNull(lastComputedRouteHolder.route.value)
        assertNotNull(viewModel.state.value.missingRegionsWarning)
    }

    @Test
    fun `opening the region catalog from the missing regions warning clears the warning and navigates`() =
        runTest(dispatcher) {
            regionCatalog.regions =
                listOf(
                    Region(
                        id = "north-zone",
                        displayName = "North Zone",
                        minLatitude = 0.0,
                        minLongitude = 0.0,
                        maxLatitude = 5.0,
                        maxLongitude = 5.0,
                        tileIds = listOf("north.rd5"),
                        approximateSizeBytes = 1_000L,
                        status = RegionStatus.NOT_DOWNLOADED,
                        missingTileCount = 1,
                    ),
                )
            val viewModel = createViewModel()
            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))
            assertNotNull(viewModel.state.value.missingRegionsWarning)

            viewModel.onAction(SavedRoutesAction.OnOpenRegionCatalog)

            assertNull(viewModel.state.value.missingRegionsWarning)
            assertEquals(SavedRoutesEvent.NavigateToRegionCatalog, viewModel.events.first())
        }

    @Test
    fun `proceeding despite missing regions resolves the route`() =
        runTest(dispatcher) {
            regionCatalog.regions =
                listOf(
                    Region(
                        id = "north-zone",
                        displayName = "North Zone",
                        minLatitude = 0.0,
                        minLongitude = 0.0,
                        maxLatitude = 5.0,
                        maxLongitude = 5.0,
                        tileIds = listOf("north.rd5"),
                        approximateSizeBytes = 1_000L,
                        status = RegionStatus.NOT_DOWNLOADED,
                        missingTileCount = 1,
                    ),
                )
            val viewModel = createViewModel()
            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

            viewModel.onAction(SavedRoutesAction.OnProceedDespiteMissingRegions)

            assertNull(viewModel.state.value.missingRegionsWarning)
            assertEquals(RoutePlaybackMode.FREE_ROAM, lastComputedRouteHolder.route.value?.mode)
            assertEquals(SavedRoutesEvent.NavigateBack, viewModel.events.first())
        }

    @Test
    fun `using a route where only Free-roam is viable resolves immediately`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

            assertEquals(RoutePlaybackMode.FREE_ROAM, lastComputedRouteHolder.route.value?.mode)
            assertNull(viewModel.state.value.routeOptions)
            assertEquals(SavedRoutesEvent.NavigateBack, viewModel.events.first())
        }

    @Test
    fun `using a route where both modes are viable asks the user to choose`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult = Route(points = samplePoints, geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 100.0)
        val viewModel = createViewModel()

        viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

        assertNull(lastComputedRouteHolder.route.value)
        assertNotNull(viewModel.state.value.routeOptions)
    }

    @Test
    fun `dismissing the mode choice (tap-outside, back, or swipe) clears it instead of leaving the sheet stuck`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult = Route(points = samplePoints, geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 100.0)
        val viewModel = createViewModel()
        viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))
        assertNotNull(viewModel.state.value.routeOptions)

        viewModel.onAction(SavedRoutesAction.OnDismissModeChoice)

        assertNull(viewModel.state.value.routeOptions)
        assertNull(viewModel.state.value.resolvingRouteId)
    }

    @Test
    fun `choosing a mode after both were viable resolves with that mode`() =
        runTest(dispatcher) {
            routingEngine.snappableLatitudes = setOf(1.0, 2.0)
            routingEngine.computePathResult =
                Route(points = samplePoints, geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 100.0)
            val viewModel = createViewModel()
            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

            viewModel.onAction(SavedRoutesAction.OnChooseMode(RoutePlaybackMode.GUIDED))

            assertEquals(RoutePlaybackMode.GUIDED, lastComputedRouteHolder.route.value?.mode)
            assertNull(viewModel.state.value.routeOptions)
            assertEquals(SavedRoutesEvent.NavigateBack, viewModel.events.first())
        }

    @Test
    fun `choosing a mode when both are viable stores the other mode's path as the alternate`() =
        runTest(dispatcher) {
            routingEngine.snappableLatitudes = setOf(1.0, 2.0)
            routingEngine.computePathResult =
                Route(points = samplePoints, geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 100.0)
            val viewModel = createViewModel()
            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))
            val freeRoam = viewModel.state.value.routeOptions?.freeRoam

            viewModel.onAction(SavedRoutesAction.OnChooseMode(RoutePlaybackMode.GUIDED))

            val resolved = lastComputedRouteHolder.route.value
            assertEquals(freeRoam?.geometry, resolved?.alternateGeometry)
            assertEquals(freeRoam?.distanceMeters, resolved?.alternateDistanceMeters)
        }

    @Test
    fun `resolving where only Free-roam is viable leaves no alternate to switch to`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onAction(SavedRoutesAction.OnUseClick("route-1"))

            val resolved = lastComputedRouteHolder.route.value
            assertEquals(RoutePlaybackMode.FREE_ROAM, resolved?.mode)
            assertNull(resolved?.alternateGeometry)
            assertNull(resolved?.alternateDistanceMeters)
        }

    @Test
    fun `editing a route seeds the name input`() {
        val viewModel = createViewModel()

        viewModel.onAction(SavedRoutesAction.OnEditClick("route-1"))

        assertEquals("route-1", viewModel.state.value.editingId)
        assertEquals("Morning loop", viewModel.state.value.editNameInput)
    }

    @Test
    fun `confirming a rename with a blank name is rejected`() {
        val viewModel = createViewModel()
        viewModel.onAction(SavedRoutesAction.OnEditClick("route-1"))
        viewModel.onAction(SavedRoutesAction.OnEditNameChange(""))

        viewModel.onAction(SavedRoutesAction.OnConfirmEdit)

        assertEquals(SavedRoutesError.INVALID_NAME, viewModel.state.value.editError)
        assertEquals("Morning loop", favoriteRoutesRepository.observeFavoriteRoutes().value.first().name)
    }

    @Test
    fun `confirming a rename with a name renames the route and closes the sheet`() {
        val viewModel = createViewModel()
        viewModel.onAction(SavedRoutesAction.OnEditClick("route-1"))
        viewModel.onAction(SavedRoutesAction.OnEditNameChange("Evening loop"))

        viewModel.onAction(SavedRoutesAction.OnConfirmEdit)

        assertEquals("Evening loop", favoriteRoutesRepository.observeFavoriteRoutes().value.first().name)
        assertNull(viewModel.state.value.editingId)
    }

    @Test
    fun `deleting a route requires confirmation`() {
        val viewModel = createViewModel()
        viewModel.onAction(SavedRoutesAction.OnDeleteClick("route-1"))

        assertEquals(1, favoriteRoutesRepository.observeFavoriteRoutes().value.size)

        viewModel.onAction(SavedRoutesAction.OnConfirmDelete)

        assertEquals(0, favoriteRoutesRepository.observeFavoriteRoutes().value.size)
    }

    @Test
    fun `dismissing delete removes nothing`() {
        val viewModel = createViewModel()
        viewModel.onAction(SavedRoutesAction.OnDeleteClick("route-1"))

        viewModel.onAction(SavedRoutesAction.OnDismissDelete)

        assertEquals(1, favoriteRoutesRepository.observeFavoriteRoutes().value.size)
        assertNull(viewModel.state.value.pendingDeleteId)
    }
}
