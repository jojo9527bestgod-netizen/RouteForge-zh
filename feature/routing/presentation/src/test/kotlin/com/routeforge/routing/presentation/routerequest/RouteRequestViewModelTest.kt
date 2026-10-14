package com.routeforge.routing.presentation.routerequest

import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.DraftWaypointsHolder
import com.routeforge.coredomain.holder.LastComputedRouteHolder
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.holder.PendingSearchWaypointHolder
import com.routeforge.coredomain.holder.SelectedFavoriteWaypointHolder
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.PlaceSearchResult
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.coredomain.usecase.FreeRoamRouteBuilder
import com.routeforge.routing.domain.RouteFileCodec
import com.routeforge.coredomain.model.Region
import com.routeforge.coredomain.model.RegionStatus
import com.routeforge.routing.domain.model.RouteDraft
import com.routeforge.routing.domain.model.RouteFileFailure
import com.routeforge.routing.domain.model.RouteFileFormat
import com.routeforge.coredomain.usecase.ComputeRequiredRegionsUseCase
import com.routeforge.coredomain.usecase.ComputeRouteUseCase
import com.routeforge.routing.domain.usecase.ExportRouteFileUseCase
import com.routeforge.routing.domain.usecase.ImportRouteFileUseCase
import com.routeforge.coredomain.usecase.ObserveMapStyleUseCase
import com.routeforge.coredomain.usecase.PrepareRouteOptionsUseCase
import com.routeforge.coredomain.usecase.RecordRegionUsageUseCase
import com.routeforge.routing.presentation.FakeFavoriteRoutesRepository
import com.routeforge.routing.presentation.FakeFavoriteWaypointsRepository
import com.routeforge.routing.presentation.FakeMapStyleStore
import com.routeforge.routing.presentation.FakeRegionCatalog
import com.routeforge.routing.presentation.FakeRegionUsageTracker
import com.routeforge.routing.presentation.FakeRoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

private class FakeRouteFileCodec(
    var parseResult: Result<RouteDraft, RouteFileFailure> = Result.Success(RouteDraft()),
) : RouteFileCodec {
    var lastSerialized: Route? = null

    override fun parse(bytes: ByteArray): Result<RouteDraft, RouteFileFailure> = parseResult

    override fun serialize(route: Route): ByteArray {
        lastSerialized = route
        return byteArrayOf(1, 2, 3)
    }
}

class RouteRequestViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val routingEngine = FakeRoutingEngine()
    private val regionCatalog = FakeRegionCatalog()
    private val lastComputedRouteHolder = LastComputedRouteHolder()
    private val jsonCodec = FakeRouteFileCodec()
    private val gpxCodec = FakeRouteFileCodec()
    private val codecs = mapOf(RouteFileFormat.JSON to jsonCodec, RouteFileFormat.GPX to gpxCodec)
    private val favoriteWaypointsRepository = FakeFavoriteWaypointsRepository()
    private val favoriteRoutesRepository = FakeFavoriteRoutesRepository()
    private val selectedFavoriteWaypointHolder = SelectedFavoriteWaypointHolder()
    private val pendingSearchWaypointHolder = PendingSearchWaypointHolder()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RouteRequestViewModel =
        RouteRequestViewModel(
            prepareRouteOptions =
                PrepareRouteOptionsUseCase(
                    computeRoute = ComputeRouteUseCase(routingEngine, regionCatalog, RecordRegionUsageUseCase(FakeRegionUsageTracker())),
                    freeRoamRouteBuilder = FreeRoamRouteBuilder(),
                ),
            lastComputedRouteHolder = lastComputedRouteHolder,
            importRouteFile = ImportRouteFileUseCase(codecs),
            exportRouteFile = ExportRouteFileUseCase(codecs),
            lastKnownRealLocationHolder = LastKnownRealLocationHolder(),
            computeRequiredRegions = ComputeRequiredRegionsUseCase(regionCatalog),
            draftWaypointsHolder = DraftWaypointsHolder(),
            favoriteWaypointsRepository = favoriteWaypointsRepository,
            favoriteRoutesRepository = favoriteRoutesRepository,
            selectedFavoriteWaypointHolder = selectedFavoriteWaypointHolder,
            pendingSearchWaypointHolder = pendingSearchWaypointHolder,
            observeMapStyle = ObserveMapStyleUseCase(FakeMapStyleStore()),
            backgroundDispatcher = dispatcher,
        )

    private fun RouteRequestViewModel.tapAndConfirm(
        latitude: Double,
        longitude: Double,
    ) {
        onAction(RouteRequestAction.OnMapTap(latitude, longitude))
        onAction(RouteRequestAction.OnConfirmAddWaypoint)
    }

    private fun RouteRequestViewModel.tapTwoPoints() {
        tapAndConfirm(1.0, 1.0)
        tapAndConfirm(2.0, 2.0)
    }

    // --- User Story 1: confirm before adding, optionally saving as a favorite ---

    @Test
    fun `tapping the map sets a pending add without adding the waypoint yet`() {
        val viewModel = createViewModel()

        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 1.0))

        assertEquals(emptyList<RoutePoint>(), viewModel.state.value.draft.points)
        assertEquals(1.0, viewModel.state.value.pendingAddLatitude)
        assertEquals(1.0, viewModel.state.value.pendingAddLongitude)
    }

    @Test
    fun `confirming without saving as a favorite adds only the waypoint`() {
        val viewModel = createViewModel()
        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 1.0))

        viewModel.onAction(RouteRequestAction.OnConfirmAddWaypoint)

        assertEquals(listOf(RoutePoint(1.0, 1.0)), viewModel.state.value.draft.points)
        assertEquals(emptyList<Any>(), favoriteWaypointsRepository.observeFavorites().value)
        assertNull(viewModel.state.value.pendingAddLatitude)
    }

    @Test
    fun `confirming with save-as-favorite checked and a name adds the waypoint and creates a favorite`() {
        val viewModel = createViewModel()
        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 2.0))
        viewModel.onAction(RouteRequestAction.OnToggleSaveAsFavorite)
        viewModel.onAction(RouteRequestAction.OnFavoriteNameInputChange("Home"))

        viewModel.onAction(RouteRequestAction.OnConfirmAddWaypoint)

        assertEquals(listOf(RoutePoint(1.0, 2.0)), viewModel.state.value.draft.points)
        val favorites = favoriteWaypointsRepository.observeFavorites().value
        assertEquals(1, favorites.size)
        assertEquals("Home", favorites[0].name)
        assertEquals(1.0, favorites[0].latitude)
        assertEquals(2.0, favorites[0].longitude)
    }

    @Test
    fun `confirming with save-as-favorite checked but a blank name does not proceed`() {
        val viewModel = createViewModel()
        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 1.0))
        viewModel.onAction(RouteRequestAction.OnToggleSaveAsFavorite)

        viewModel.onAction(RouteRequestAction.OnConfirmAddWaypoint)

        assertEquals(emptyList<RoutePoint>(), viewModel.state.value.draft.points)
        assertEquals(emptyList<Any>(), favoriteWaypointsRepository.observeFavorites().value)
        assertNotNull(viewModel.state.value.pendingAddLatitude)
    }

    @Test
    fun `dismissing the add-waypoint confirmation adds nothing and saves nothing`() {
        val viewModel = createViewModel()
        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 1.0))
        viewModel.onAction(RouteRequestAction.OnToggleSaveAsFavorite)
        viewModel.onAction(RouteRequestAction.OnFavoriteNameInputChange("Home"))

        viewModel.onAction(RouteRequestAction.OnDismissAddWaypoint)

        assertEquals(emptyList<RoutePoint>(), viewModel.state.value.draft.points)
        assertEquals(emptyList<Any>(), favoriteWaypointsRepository.observeFavorites().value)
        assertNull(viewModel.state.value.pendingAddLatitude)
        assertNull(viewModel.state.value.pendingAddLongitude)
        assertTrue(!viewModel.state.value.isSaveAsFavoriteChecked)
        assertEquals("", viewModel.state.value.favoriteNameInput)
    }

    @Test
    fun `selecting a favorite from the shared holder appends it to the draft`() {
        val viewModel = createViewModel()
        viewModel.onAction(RouteRequestAction.OnMapTap(1.0, 1.0))
        viewModel.onAction(RouteRequestAction.OnConfirmAddWaypoint)

        selectedFavoriteWaypointHolder.set(RoutePoint(9.0, 9.0))

        assertEquals(
            listOf(RoutePoint(1.0, 1.0), RoutePoint(9.0, 9.0)),
            viewModel.state.value.draft.points,
        )
        assertNull(selectedFavoriteWaypointHolder.selected.value)
    }

    @Test
    fun `a place picked from address search becomes a pending add, named, not a silent append`() {
        val viewModel = createViewModel()

        pendingSearchWaypointHolder.set(
            PlaceSearchResult(name = "Eiffel Tower", formattedAddress = "Paris, France", latitude = 48.8584, longitude = 2.2945, importance = 0.8),
        )

        val state = viewModel.state.value
        assertEquals(48.8584, state.pendingAddLatitude)
        assertEquals(2.2945, state.pendingAddLongitude)
        assertEquals("Eiffel Tower", state.pendingAddPlaceName)
        assertEquals(emptyList<RoutePoint>(), state.draft.points)
        assertNull(pendingSearchWaypointHolder.selected.value)
    }

    // --- Favorite Routes: save a planned route (spec 005-favorite-routes, US1) ---

    @Test
    fun `save-route click opens the sheet`() {
        val viewModel = createViewModel()

        viewModel.onAction(RouteRequestAction.OnSaveRouteClick)

        assertTrue(viewModel.state.value.isSaveRouteSheetOpen)
    }

    @Test
    fun `confirming save-route with a blank name does not save and keeps the sheet open`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnSaveRouteClick)

        viewModel.onAction(RouteRequestAction.OnConfirmSaveRoute)

        assertEquals(emptyList<Any>(), favoriteRoutesRepository.observeFavoriteRoutes().value)
        assertTrue(viewModel.state.value.isSaveRouteSheetOpen)
    }

    @Test
    fun `confirming save-route with a name saves the draft's current points in order and closes the sheet`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnSaveRouteClick)
        viewModel.onAction(RouteRequestAction.OnRouteNameInputChange("Morning loop"))

        viewModel.onAction(RouteRequestAction.OnConfirmSaveRoute)

        val savedRoutes = favoriteRoutesRepository.observeFavoriteRoutes().value
        assertEquals(1, savedRoutes.size)
        assertEquals("Morning loop", savedRoutes[0].name)
        assertEquals(listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0)), savedRoutes[0].points)
        assertTrue(!viewModel.state.value.isSaveRouteSheetOpen)
    }

    @Test
    fun `dismissing save-route saves nothing and resets the name input`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnSaveRouteClick)
        viewModel.onAction(RouteRequestAction.OnRouteNameInputChange("Morning loop"))

        viewModel.onAction(RouteRequestAction.OnDismissSaveRoute)

        assertEquals(emptyList<Any>(), favoriteRoutesRepository.observeFavoriteRoutes().value)
        assertTrue(!viewModel.state.value.isSaveRouteSheetOpen)
        assertEquals("", viewModel.state.value.routeNameInput)
    }

    // --- User Story 2: manual point management ---

    @Test
    fun `tapping the map and confirming adds a numbered waypoint connected in order`() {
        val viewModel = createViewModel()

        viewModel.tapAndConfirm(1.0, 1.0)
        viewModel.tapAndConfirm(2.0, 2.0)

        assertEquals(
            listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0)),
            viewModel.state.value.draft.points,
        )
    }

    @Test
    fun `dragging a marker updates its position`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnMarkerDragged(index = 1, latitude = 9.0, longitude = 9.0))

        assertEquals(
            listOf(RoutePoint(1.0, 1.0), RoutePoint(9.0, 9.0)),
            viewModel.state.value.draft.points,
        )
    }

    @Test
    fun `clicking a marker opens the edit dialog prefilled with its coordinates`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnMarkerClick(0))

        val state = viewModel.state.value
        assertEquals(0, state.editingIndex)
        assertEquals("1.0", state.editLatitudeInput)
        assertEquals("1.0", state.editLongitudeInput)
    }

    @Test
    fun `confirming an edit replaces the waypoint's coordinates and closes the dialog`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnMarkerClick(0))

        viewModel.onAction(RouteRequestAction.OnEditLatitudeChange("5.0"))
        viewModel.onAction(RouteRequestAction.OnEditLongitudeChange("5.0"))
        viewModel.onAction(RouteRequestAction.OnConfirmEdit)

        val state = viewModel.state.value
        assertEquals(RoutePoint(5.0, 5.0), state.draft.points[0])
        assertNull(state.editingIndex)
    }

    @Test
    fun `deleting the waypoint being edited removes it and keeps the rest connected`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.tapAndConfirm(3.0, 3.0)
        viewModel.onAction(RouteRequestAction.OnMarkerClick(1))

        viewModel.onAction(RouteRequestAction.OnDeleteEditingWaypoint)

        assertEquals(
            listOf(RoutePoint(1.0, 1.0), RoutePoint(3.0, 3.0)),
            viewModel.state.value.draft.points,
        )
        assertNull(viewModel.state.value.editingIndex)
    }

    @Test
    fun `undo reverts the most recent change and repeated undo is a no-op once history is empty`() {
        val viewModel = createViewModel()
        viewModel.tapAndConfirm(1.0, 1.0)
        viewModel.tapAndConfirm(2.0, 2.0)

        viewModel.onAction(RouteRequestAction.OnUndo)
        assertEquals(listOf(RoutePoint(1.0, 1.0)), viewModel.state.value.draft.points)

        viewModel.onAction(RouteRequestAction.OnUndo)
        assertEquals(emptyList<RoutePoint>(), viewModel.state.value.draft.points)

        viewModel.onAction(RouteRequestAction.OnUndo)
        assertEquals(emptyList<RoutePoint>(), viewModel.state.value.draft.points)
    }

    @Test
    fun `proceeding to play with fewer than 2 points is blocked with a clear message`() {
        val viewModel = createViewModel()
        viewModel.tapAndConfirm(1.0, 1.0)

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertNotNull(viewModel.state.value.errorType)
        assertNull(viewModel.state.value.route)
    }

    // --- Region-awareness before play ---

    @Test
    fun `waypoints without road coverage do not silently become a straight-line route`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertNull(viewModel.state.value.missingRegionsWarning)
        assertNull(viewModel.state.value.route)
        assertEquals(RouteRequestError.MODE_PREVIEW_NO_PATH, viewModel.state.value.errorType)
    }

    @Test
    fun `an undownloaded region covering a waypoint blocks play with a warning instead of computing`() {
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
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertNotNull(viewModel.state.value.missingRegionsWarning)
        assertNull(viewModel.state.value.route)
    }

    @Test
    fun `explicitly choosing straight lines from the missing regions warning computes free-roam`() {
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
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        viewModel.onAction(RouteRequestAction.OnProceedDespiteMissingRegions)

        assertNull(viewModel.state.value.missingRegionsWarning)
        assertNotNull(viewModel.state.value.route)
        assertEquals(RoutePlaybackMode.FREE_ROAM, viewModel.state.value.chosenMode)
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
            viewModel.tapTwoPoints()
            viewModel.onAction(RouteRequestAction.OnRequestRoute)
            assertNotNull(viewModel.state.value.missingRegionsWarning)

            val eventDeferred = async { viewModel.events.first() }
            viewModel.onAction(RouteRequestAction.OnOpenRegionCatalog)

            assertNull(viewModel.state.value.missingRegionsWarning)
            assertEquals(RouteRequestEvent.NavigateToRegionCatalog, eventDeferred.await())
        }

    // --- User Story 1: mode gate ---

    @Test
    fun `requesting a route with no preview selected follows roads by default`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(
                points = listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0)),
                geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)),
                distanceMeters = 100.0,
            )
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        val state = viewModel.state.value
        assertEquals(RoutePlaybackMode.GUIDED, state.route?.mode)
        assertEquals(RoutePlaybackMode.GUIDED, state.chosenMode)
        assertTrue(!state.isComputing)
    }

    @Test
    fun `requesting a route after previewing guided finalizes guided directly, without asking again`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(
                points = listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0)),
                geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)),
                distanceMeters = 100.0,
            )
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnSwitchRouteModeClick)
        assertEquals(RoutePlaybackMode.GUIDED, viewModel.state.value.previewMode)

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        val state = viewModel.state.value
        assertEquals(RoutePlaybackMode.GUIDED, state.route?.mode)
        assertEquals(RoutePlaybackMode.GUIDED, state.chosenMode)
    }

    @Test
    fun `finalizing a mode when both are viable stores the other mode's path as the alternate`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(
                points = listOf(RoutePoint(1.0, 1.0), RoutePoint(2.0, 2.0)),
                geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)),
                distanceMeters = 100.0,
            )
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnSwitchRouteModeClick)

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        val state = viewModel.state.value
        assertEquals(RoutePlaybackMode.GUIDED, state.route?.mode)
        assertNotNull(state.route?.alternateGeometry)
        assertNotNull(state.route?.alternateDistanceMeters)
    }

    @Test
    fun `unavailable road routing leaves no playable straight-line fallback`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        val state = viewModel.state.value
        assertNull(state.chosenMode)
        assertNull(state.route)
        assertNull(state.route?.alternateGeometry)
        assertNull(state.route?.alternateDistanceMeters)
    }

    @Test
    fun `an unroutable point shows an error without producing a straight-line route`() {
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        val state = viewModel.state.value
        assertNull(state.chosenMode)
        assertNull(state.route)
        assertEquals(RouteRequestError.MODE_PREVIEW_NO_PATH, state.errorType)
    }

    @Test
    fun `disconnected roads show an error without a straight-line fallback`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult = null
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertNull(viewModel.state.value.chosenMode)
        assertNull(viewModel.state.value.route)
        assertEquals(RouteRequestError.MODE_PREVIEW_NO_PATH, viewModel.state.value.errorType)
    }

    @Test
    fun `requesting a route again after finalizing one recomputes and finalizes again`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(points = emptyList(), geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 10.0)
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnRequestRoute)
        assertEquals(RoutePlaybackMode.GUIDED, viewModel.state.value.chosenMode)

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertEquals(RoutePlaybackMode.GUIDED, viewModel.state.value.chosenMode)
        assertNotNull(viewModel.state.value.route)
    }

    // --- User Story 3: file import/export ---

    @Test
    fun `a successful import replaces the current draft and clears any prior route`() {
        val imported = RouteDraft().add(RoutePoint(9.0, 9.0)).add(RoutePoint(8.0, 8.0))
        jsonCodec.parseResult = Result.Success(imported)
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRouteFileImported(byteArrayOf(0), RouteFileFormat.JSON))

        val state = viewModel.state.value
        assertEquals(imported.points, state.draft.points)
        assertNull(state.errorType)
    }

    @Test
    fun `a failed import surfaces a clear error and leaves the draft untouched`() {
        jsonCodec.parseResult = Result.Error(RouteFileFailure.TooFewWaypoints)
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        val pointsBefore = viewModel.state.value.draft.points

        viewModel.onAction(RouteRequestAction.OnRouteFileImported(byteArrayOf(0), RouteFileFormat.JSON))

        assertNotNull(viewModel.state.value.errorType)
        assertEquals(pointsBefore, viewModel.state.value.draft.points)
    }

    @Test
    fun `exporting a finalized route serializes it through the matching codec`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(points = emptyList(), geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 10.0)
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()
        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        viewModel.onAction(RouteRequestAction.OnExportRoute(RouteFileFormat.GPX))

        assertEquals(viewModel.state.value.route, gpxCodec.lastSerialized)
        assertNull(jsonCodec.lastSerialized)
    }

    @Test
    fun `requesting a route finalizes it but does not navigate away by itself`() {
        routingEngine.snappableLatitudes = setOf(1.0, 2.0)
        routingEngine.computePathResult =
            Route(points = emptyList(), geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 10.0)
        val viewModel = createViewModel()
        viewModel.tapTwoPoints()

        viewModel.onAction(RouteRequestAction.OnRequestRoute)

        assertNotNull(viewModel.state.value.route)
    }

    @Test
    fun `OnUseRoute navigates to Simulate directly, without an extra confirmation`() =
        runTest(dispatcher) {
            routingEngine.snappableLatitudes = setOf(1.0, 2.0)
            routingEngine.computePathResult =
                Route(points = emptyList(), geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 10.0)
            val viewModel = createViewModel()
            viewModel.tapTwoPoints()
            viewModel.onAction(RouteRequestAction.OnRequestRoute)

            val eventDeferred = async { viewModel.events.first() }
            viewModel.onAction(RouteRequestAction.OnUseRoute)

            assertEquals(RouteRequestEvent.GoToSimulate, eventDeferred.await())
            assertNotNull(lastComputedRouteHolder.route.value)
        }

    @Test
    fun `applying a committed reorder-list edit invalidates an already-computed route, per FR-018`() =
        runTest(dispatcher) {
            routingEngine.snappableLatitudes = setOf(1.0, 2.0)
            routingEngine.computePathResult =
                Route(points = emptyList(), geometry = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), distanceMeters = 10.0)
            val viewModel = createViewModel()
            viewModel.tapTwoPoints()
            viewModel.onAction(RouteRequestAction.OnRequestRoute)
            assertNotNull(viewModel.state.value.route)

            viewModel.applyEditedWaypoints(listOf(RoutePoint(2.0, 2.0), RoutePoint(1.0, 1.0)))

            assertNull(viewModel.state.value.route)
            assertEquals(listOf(RoutePoint(2.0, 2.0), RoutePoint(1.0, 1.0)), viewModel.state.value.draft.points)
        }
}
