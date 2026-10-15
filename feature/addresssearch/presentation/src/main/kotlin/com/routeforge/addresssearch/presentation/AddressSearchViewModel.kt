package com.routeforge.addresssearch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeforge.addresssearch.domain.usecase.SearchPlacesUseCase
import com.routeforge.addresssearch.domain.SearchPlacesError
import com.routeforge.coredomain.FavoriteWaypointsRepository
import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.PendingSearchWaypointHolder
import com.routeforge.coredomain.holder.PendingTeleportTargetHolder
import com.routeforge.coredomain.model.RoutePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [sourceContext]/[referenceLatitude]/[referenceLongitude] come from the [AddressSearchRoute] the
 *  screen was opened with — passed as plain constructor parameters (via Koin's parameterized
 *  `viewModel { ... }` DSL) rather than a `SavedStateHandle`, since they're immutable for this
 *  screen's whole lifetime. */
class AddressSearchViewModel(
    sourceContext: SearchSourceContext,
    private val referenceLatitude: Double?,
    private val referenceLongitude: Double?,
    private val searchPlaces: SearchPlacesUseCase,
    private val pendingSearchWaypointHolder: PendingSearchWaypointHolder,
    private val pendingTeleportTargetHolder: PendingTeleportTargetHolder,
    private val favoriteWaypointsRepository: FavoriteWaypointsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddressSearchState(sourceContext = sourceContext))
    val state = _state.asStateFlow()

    private val _events = Channel<AddressSearchEvent>()
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    fun onAction(action: AddressSearchAction) {
        when (action) {
            is AddressSearchAction.OnQueryChange -> _state.update { it.copy(query = action.value) }
            AddressSearchAction.OnSubmit -> submit()
            is AddressSearchAction.OnSelectResult -> selectResult(action.index)
            AddressSearchAction.OnDismissChoice -> _state.update { it.copy(pendingChoiceResult = null) }
            AddressSearchAction.OnChooseTeleport -> chooseTeleport()
            AddressSearchAction.OnChooseAddToRoute -> chooseAddToRoute()
            AddressSearchAction.OnChooseSaveAsFavorite ->
                _state.update {
                    it.copy(isNamingFavorite = true, favoriteNameInput = it.pendingChoiceResult?.name.orEmpty())
                }
            is AddressSearchAction.OnFavoriteNameChange -> _state.update { it.copy(favoriteNameInput = action.value) }
            AddressSearchAction.OnConfirmFavoriteName -> confirmFavoriteName()
            AddressSearchAction.OnDismissFavoriteName -> _state.update { it.copy(isNamingFavorite = false) }
        }
    }

    /** Cancels any still-pending prior search before launching a new one (FR-005) — a slow,
     *  now-stale response can never land after a newer query's results. */
    private fun submit() {
        val query = _state.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(isLoading = true, errorType = null) }
        searchJob =
            viewModelScope.launch {
                when (val result = searchPlaces(query, referenceLatitude, referenceLongitude)) {
                    is Result.Success ->
                        _state.update {
                            it.copy(isLoading = false, hasSearched = true, results = result.data, errorType = null)
                        }
                    is Result.Error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                results = emptyList(),
                                errorType = when (val error = result.error) {
                                    SearchPlacesError.InvalidCoordinates -> AddressSearchError.INVALID_COORDINATES
                                    is SearchPlacesError.Network -> when (error.error) {
                                        DataError.Network.REQUEST_TIMEOUT -> AddressSearchError.TIMEOUT
                                        DataError.Network.TOO_MANY_REQUESTS -> AddressSearchError.RATE_LIMITED
                                        DataError.Network.SERIALIZATION -> AddressSearchError.INVALID_RESPONSE
                                        DataError.Network.SERVER_ERROR, DataError.Network.SERVICE_UNAVAILABLE,
                                        DataError.Network.UNAUTHORIZED, DataError.Network.FORBIDDEN,
                                        DataError.Network.NOT_FOUND -> AddressSearchError.SERVICE_UNAVAILABLE
                                        else -> AddressSearchError.NETWORK_FAILURE
                                    }
                                },
                            )
                        }
                }
            }
    }

    private fun selectResult(index: Int) {
        val result = _state.value.results.getOrNull(index) ?: return
        if (_state.value.sourceContext == SearchSourceContext.PLAN_ROUTE) {
            pendingSearchWaypointHolder.set(result)
            viewModelScope.launch { _events.send(AddressSearchEvent.NavigateBack) }
        } else {
            _state.update { it.copy(pendingChoiceResult = result) }
        }
    }

    private fun chooseTeleport() {
        val result = _state.value.pendingChoiceResult ?: return
        _state.update { it.copy(pendingChoiceResult = null) }
        pendingTeleportTargetHolder.set(RoutePoint(latitude = result.latitude, longitude = result.longitude))
        viewModelScope.launch { _events.send(AddressSearchEvent.NavigateToSimulate) }
    }

    private fun chooseAddToRoute() {
        val result = _state.value.pendingChoiceResult ?: return
        _state.update { it.copy(pendingChoiceResult = null) }
        pendingSearchWaypointHolder.set(result)
        viewModelScope.launch { _events.send(AddressSearchEvent.NavigateToPlan) }
    }

    private fun confirmFavoriteName() {
        val result = _state.value.pendingChoiceResult ?: return
        val name = _state.value.favoriteNameInput
        if (name.isBlank()) return
        favoriteWaypointsRepository.add(name, result.latitude, result.longitude)
        viewModelScope.launch { _events.send(AddressSearchEvent.NavigateBack) }
    }
}
