package com.routeforge.addresssearch.presentation

import com.routeforge.addresssearch.domain.GeocodeClient
import com.routeforge.addresssearch.domain.usecase.RankSearchResultsUseCase
import com.routeforge.addresssearch.domain.usecase.SearchPlacesUseCase
import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.FavoriteWaypointsRepository
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.holder.PendingSearchWaypointHolder
import com.routeforge.coredomain.holder.PendingTeleportTargetHolder
import com.routeforge.coredomain.model.FavoriteWaypoint
import com.routeforge.coredomain.model.PlaceSearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private fun place(
    name: String = "Place",
    latitude: Double = 1.0,
    longitude: Double = 2.0,
) = PlaceSearchResult(name = name, formattedAddress = name, latitude = latitude, longitude = longitude, importance = 0.5)

private class FakeGeocodeClient(
    var result: Result<List<PlaceSearchResult>, DataError.Network> = Result.Success(listOf(place())),
) : GeocodeClient {
    /** Queued responses that only resolve when the test completes them — lets a test hold a
     *  search suspended mid-flight instead of [search] returning [result] immediately. Falls back
     *  to [result] once empty, so every other test (which never touches this) is unaffected. */
    val queuedResponses = mutableListOf<CompletableDeferred<Result<List<PlaceSearchResult>, DataError.Network>>>()

    override suspend fun search(query: String): Result<List<PlaceSearchResult>, DataError.Network> =
        if (queuedResponses.isNotEmpty()) queuedResponses.removeAt(0).await() else result
}

private class FakeFavoriteWaypointsRepository : FavoriteWaypointsRepository {
    val added = mutableListOf<Triple<String, Double, Double>>()
    private val _favorites = MutableStateFlow(emptyList<FavoriteWaypoint>())

    override fun observeFavorites(): StateFlow<List<FavoriteWaypoint>> = _favorites

    override fun add(
        name: String,
        latitude: Double,
        longitude: Double,
    ): FavoriteWaypoint {
        added += Triple(name, latitude, longitude)
        return FavoriteWaypoint(id = "fake", name = name, latitude = latitude, longitude = longitude)
    }

    override fun update(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
    ) = Unit

    override fun delete(id: String) = Unit
}

class AddressSearchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val geocodeClient = FakeGeocodeClient()
    private val pendingSearchWaypointHolder = PendingSearchWaypointHolder()
    private val pendingTeleportTargetHolder = PendingTeleportTargetHolder()
    private val favoriteWaypointsRepository = FakeFavoriteWaypointsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(sourceContext: SearchSourceContext = SearchSourceContext.TELEPORT_OR_FAVORITE): AddressSearchViewModel =
        AddressSearchViewModel(
            sourceContext = sourceContext,
            referenceLatitude = null,
            referenceLongitude = null,
            searchPlaces = SearchPlacesUseCase(geocodeClient, RankSearchResultsUseCase(), LastKnownRealLocationHolder()),
            pendingSearchWaypointHolder = pendingSearchWaypointHolder,
            pendingTeleportTargetHolder = pendingTeleportTargetHolder,
            favoriteWaypointsRepository = favoriteWaypointsRepository,
        )

    @Test
    fun `a blank query does not trigger a search`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(AddressSearchAction.OnSubmit)

            assertFalse(viewModel.state.value.hasSearched)
        }

    @Test
    fun `a successful search populates results`() =
        runTest {
            val result = place(name = "Found place")
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel()

            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)

            assertEquals(listOf(result), viewModel.state.value.results)
            assertTrue(viewModel.state.value.hasSearched)
            assertNull(viewModel.state.value.errorType)
        }

    @Test
    fun `a failed search surfaces a network error`() =
        runTest {
            geocodeClient.result = Result.Error(DataError.Network.NO_INTERNET)
            val viewModel = createViewModel()

            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)

            assertEquals(AddressSearchError.NETWORK_FAILURE, viewModel.state.value.errorType)
            assertTrue(viewModel.state.value.results.isEmpty())
        }

    /** FR-005: a stale search response must never land after a newer query's results —
     *  [StandardTestDispatcher] (not this class's usual [UnconfinedTestDispatcher]) so both
     *  searches' suspension points are reached under this test's own explicit control, proving
     *  the first search is genuinely cancelled while in-flight rather than merely losing a race. */
    @Test
    fun `submitting a new search cancels a still-pending prior one`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val staleResponse = CompletableDeferred<Result<List<PlaceSearchResult>, DataError.Network>>()
            val freshResponse = CompletableDeferred<Result<List<PlaceSearchResult>, DataError.Network>>()
            geocodeClient.queuedResponses += staleResponse
            geocodeClient.queuedResponses += freshResponse
            val viewModel = createViewModel()

            viewModel.onAction(AddressSearchAction.OnQueryChange("stale query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            runCurrent()
            viewModel.onAction(AddressSearchAction.OnQueryChange("fresh query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            runCurrent()

            val freshResult = place(name = "Fresh")
            staleResponse.complete(Result.Success(listOf(place(name = "Stale"))))
            freshResponse.complete(Result.Success(listOf(freshResult)))
            advanceUntilIdle()

            assertEquals(listOf(freshResult), viewModel.state.value.results)
        }

    @Test
    fun `selecting a result when opened from Plan Route hands it off without a choice prompt`() =
        runTest {
            val result = place()
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel(sourceContext = SearchSourceContext.PLAN_ROUTE)
            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            val eventDeferred = async { viewModel.events.first() }

            viewModel.onAction(AddressSearchAction.OnSelectResult(0))

            assertEquals(result, pendingSearchWaypointHolder.selected.value)
            assertNull(viewModel.state.value.pendingChoiceResult)
            assertEquals(AddressSearchEvent.NavigateBack, eventDeferred.await())
        }

    @Test
    fun `selecting a result when opened from elsewhere prompts a teleport-or-favorite choice`() =
        runTest {
            val result = place()
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel(sourceContext = SearchSourceContext.TELEPORT_OR_FAVORITE)
            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)

            viewModel.onAction(AddressSearchAction.OnSelectResult(0))

            assertEquals(result, viewModel.state.value.pendingChoiceResult)
            assertNull(pendingSearchWaypointHolder.selected.value)
        }

    @Test
    fun `selecting an out-of-range result index is a no-op`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(AddressSearchAction.OnSelectResult(0))

            assertNull(viewModel.state.value.pendingChoiceResult)
        }

    @Test
    fun `general search can hand a foreign place to route planning without teleporting`() = runTest {
        val result = place("Eiffel Tower", 48.8584, 2.2945)
        geocodeClient.result = Result.Success(listOf(result))
        val viewModel = createViewModel()
        viewModel.onAction(AddressSearchAction.OnQueryChange("Eiffel Tower"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        viewModel.onAction(AddressSearchAction.OnSelectResult(0))
        val event = async { viewModel.events.first() }

        viewModel.onAction(AddressSearchAction.OnChooseAddToRoute)

        assertEquals(result, pendingSearchWaypointHolder.selected.value)
        assertNull(pendingTeleportTargetHolder.target.value)
        assertNull(viewModel.state.value.pendingChoiceResult)
        assertEquals(AddressSearchEvent.NavigateToPlan, event.await())
        assertTrue(favoriteWaypointsRepository.added.isEmpty())
    }

    @Test
    fun `dismissed result cannot later be added to a route`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        viewModel.onAction(AddressSearchAction.OnSelectResult(0))
        viewModel.onAction(AddressSearchAction.OnDismissChoice)
        viewModel.onAction(AddressSearchAction.OnChooseAddToRoute)
        assertNull(pendingSearchWaypointHolder.selected.value)
    }

    @Test
    fun `timeout rate limit and response failures have actionable error states`() = runTest {
        for ((networkError, expected) in listOf(
            DataError.Network.REQUEST_TIMEOUT to AddressSearchError.TIMEOUT,
            DataError.Network.TOO_MANY_REQUESTS to AddressSearchError.RATE_LIMITED,
            DataError.Network.SERVER_ERROR to AddressSearchError.SERVICE_UNAVAILABLE,
            DataError.Network.FORBIDDEN to AddressSearchError.SERVICE_UNAVAILABLE,
            DataError.Network.SERIALIZATION to AddressSearchError.INVALID_RESPONSE,
        )) {
            geocodeClient.result = Result.Error(networkError)
            val viewModel = createViewModel()
            viewModel.onAction(AddressSearchAction.OnQueryChange("Paris"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            assertEquals(expected, viewModel.state.value.errorType)
            assertFalse(viewModel.state.value.isLoading)
        }
    }

    @Test
    fun `choosing teleport hands off the result's coordinates as a route point`() =
        runTest {
            val result = place(latitude = 10.0, longitude = 20.0)
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel()
            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            viewModel.onAction(AddressSearchAction.OnSelectResult(0))
            val eventDeferred = async { viewModel.events.first() }

            viewModel.onAction(AddressSearchAction.OnChooseTeleport)

            assertEquals(10.0, pendingTeleportTargetHolder.target.value?.latitude)
            assertEquals(20.0, pendingTeleportTargetHolder.target.value?.longitude)
            assertEquals(AddressSearchEvent.NavigateToSimulate, eventDeferred.await())
        }

    @Test
    fun `coordinate search from planner uses the exact local point for waypoint confirmation`() = runTest {
        geocodeClient.result = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = createViewModel(SearchSourceContext.PLAN_ROUTE)
        viewModel.onAction(AddressSearchAction.OnQueryChange("35.68309115595934, 139.75222770699756"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        assertTrue(viewModel.state.value.results.single().isCoordinate)
        assertNull(viewModel.state.value.errorType)
        val event = async { viewModel.events.first() }
        viewModel.onAction(AddressSearchAction.OnSelectResult(0))
        assertEquals(35.68309115595934, pendingSearchWaypointHolder.selected.value?.latitude)
        assertEquals(139.75222770699756, pendingSearchWaypointHolder.selected.value?.longitude)
        assertEquals(AddressSearchEvent.NavigateBack, event.await())
    }

    @Test
    fun `scaled coordinate result can navigate to the simulator without an online search`() = runTest {
        geocodeClient.result = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = createViewModel()
        viewModel.onAction(AddressSearchAction.OnQueryChange("(13975223, 3568309)"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        viewModel.onAction(AddressSearchAction.OnSelectResult(0))
        val event = async { viewModel.events.first() }
        viewModel.onAction(AddressSearchAction.OnChooseTeleport)
        assertEquals(35.68309, pendingTeleportTargetHolder.target.value?.latitude)
        assertEquals(139.75223, pendingTeleportTargetHolder.target.value?.longitude)
        assertNull(viewModel.state.value.pendingChoiceResult)
        assertEquals(AddressSearchEvent.NavigateToSimulate, event.await())
    }

    @Test
    fun `invalid coordinate error clears after entering valid coordinates`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(AddressSearchAction.OnQueryChange("139.75,35.68"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        assertEquals(AddressSearchError.INVALID_COORDINATES, viewModel.state.value.errorType)
        assertTrue(viewModel.state.value.results.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
        viewModel.onAction(AddressSearchAction.OnQueryChange("35.68,139.75"))
        viewModel.onAction(AddressSearchAction.OnSubmit)
        assertNull(viewModel.state.value.errorType)
        assertEquals(1, viewModel.state.value.results.size)
    }

    @Test
    fun `confirming a blank favorite name does not save it`() =
        runTest {
            val result = place()
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel()
            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            viewModel.onAction(AddressSearchAction.OnSelectResult(0))
            viewModel.onAction(AddressSearchAction.OnChooseSaveAsFavorite)
            viewModel.onAction(AddressSearchAction.OnFavoriteNameChange("   "))

            viewModel.onAction(AddressSearchAction.OnConfirmFavoriteName)

            assertTrue(favoriteWaypointsRepository.added.isEmpty())
        }

    @Test
    fun `confirming a favorite name saves it under the result's coordinates`() =
        runTest {
            val result = place(latitude = 5.0, longitude = 6.0)
            geocodeClient.result = Result.Success(listOf(result))
            val viewModel = createViewModel()
            viewModel.onAction(AddressSearchAction.OnQueryChange("query"))
            viewModel.onAction(AddressSearchAction.OnSubmit)
            viewModel.onAction(AddressSearchAction.OnSelectResult(0))
            viewModel.onAction(AddressSearchAction.OnChooseSaveAsFavorite)
            viewModel.onAction(AddressSearchAction.OnFavoriteNameChange("Home"))
            val eventDeferred = async { viewModel.events.first() }

            viewModel.onAction(AddressSearchAction.OnConfirmFavoriteName)

            assertEquals(listOf(Triple("Home", 5.0, 6.0)), favoriteWaypointsRepository.added)
            assertEquals(AddressSearchEvent.NavigateBack, eventDeferred.await())
        }
}
