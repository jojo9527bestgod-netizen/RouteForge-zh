package com.routeforge.routing.presentation.routerequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeforge.coredomain.FavoriteRoutesRepository
import com.routeforge.coredomain.FavoriteWaypointsRepository
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.DraftWaypointsHolder
import com.routeforge.coredomain.holder.LastComputedRouteHolder
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.holder.PendingSearchWaypointHolder
import com.routeforge.coredomain.holder.SelectedFavoriteWaypointHolder
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.routing.domain.model.RouteDraft
import com.routeforge.routing.domain.model.RouteFileFailure
import com.routeforge.routing.domain.model.RouteFileFormat
import com.routeforge.coredomain.model.RouteOptions
import com.routeforge.coredomain.usecase.ComputeRequiredRegionsUseCase
import com.routeforge.routing.domain.usecase.ExportRouteFileUseCase
import com.routeforge.routing.domain.usecase.ImportRouteFileUseCase
import com.routeforge.coredomain.usecase.ObserveMapStyleUseCase
import com.routeforge.coredomain.usecase.PrepareRouteOptionsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MIN_WAYPOINTS_TO_PLAY = 2

class RouteRequestViewModel(
    private val prepareRouteOptions: PrepareRouteOptionsUseCase,
    private val lastComputedRouteHolder: LastComputedRouteHolder,
    private val importRouteFile: ImportRouteFileUseCase,
    private val exportRouteFile: ExportRouteFileUseCase,
    lastKnownRealLocationHolder: LastKnownRealLocationHolder,
    private val computeRequiredRegions: ComputeRequiredRegionsUseCase,
    private val draftWaypointsHolder: DraftWaypointsHolder,
    private val favoriteWaypointsRepository: FavoriteWaypointsRepository,
    private val favoriteRoutesRepository: FavoriteRoutesRepository,
    private val selectedFavoriteWaypointHolder: SelectedFavoriteWaypointHolder,
    private val pendingSearchWaypointHolder: PendingSearchWaypointHolder,
    observeMapStyle: ObserveMapStyleUseCase,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteRequestState(lastKnownLocation = lastKnownRealLocationHolder.location.value))
    val state = _state.asStateFlow()

    private val _events = Channel<RouteRequestEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeMapStyle()
            .onEach { mapStyle -> _state.update { it.copy(mapStyle = mapStyle) } }
            .launchIn(viewModelScope)

        selectedFavoriteWaypointHolder.selected
            .onEach { point ->
                if (point != null) {
                    mutateDraft { it.add(point) }
                    selectedFavoriteWaypointHolder.clear()
                }
            }.launchIn(viewModelScope)

        pendingSearchWaypointHolder.selected
            .onEach { result ->
                if (result != null) {
                    _state.update {
                        it.copy(
                            pendingAddLatitude = result.latitude,
                            pendingAddLongitude = result.longitude,
                            pendingAddPlaceName = result.name,
                        )
                    }
                    pendingSearchWaypointHolder.clear()
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: RouteRequestAction) {
        when (action) {
            is RouteRequestAction.OnMapTap ->
                _state.update { it.copy(pendingAddLatitude = action.latitude, pendingAddLongitude = action.longitude) }
            is RouteRequestAction.OnMarkerDragged ->
                mutateDraft {
                    it.update(action.index, RoutePoint(latitude = action.latitude, longitude = action.longitude))
                }
            is RouteRequestAction.OnMarkerClick -> openEditDialog(action.index)
            is RouteRequestAction.OnEditLatitudeChange -> _state.update { it.copy(editLatitudeInput = action.value) }
            is RouteRequestAction.OnEditLongitudeChange -> _state.update { it.copy(editLongitudeInput = action.value) }
            RouteRequestAction.OnConfirmEdit -> confirmEdit()
            RouteRequestAction.OnDeleteEditingWaypoint -> deleteEditingWaypoint()
            RouteRequestAction.OnDismissEdit -> _state.update { it.copy(editingIndex = null) }
            RouteRequestAction.OnUndo -> mutateDraft { it.undo() }
            RouteRequestAction.OnConfirmAddWaypoint -> confirmAddWaypoint()
            RouteRequestAction.OnDismissAddWaypoint -> dismissAddWaypoint()
            RouteRequestAction.OnToggleSaveAsFavorite ->
                _state.update { it.copy(isSaveAsFavoriteChecked = !it.isSaveAsFavoriteChecked) }
            is RouteRequestAction.OnFavoriteNameInputChange -> _state.update { it.copy(favoriteNameInput = action.value) }
            is RouteRequestAction.OnRouteFileImported -> importRoute(action.bytes, action.format)
            is RouteRequestAction.OnExportRoute -> exportRoute(action.format)
            RouteRequestAction.OnRequestRoute -> requestRoute()
            RouteRequestAction.OnUseRoute -> useRoute()
            RouteRequestAction.OnOpenRegionCatalog -> {
                _state.update { it.copy(missingRegionsWarning = null, pendingModePreviewTarget = null) }
                viewModelScope.launch { _events.send(RouteRequestEvent.NavigateToRegionCatalog) }
            }
            RouteRequestAction.OnOpenFavoritesClick ->
                viewModelScope.launch { _events.send(RouteRequestEvent.NavigateToFavorites) }
            RouteRequestAction.OnProceedDespiteMissingRegions -> {
                _state.update {
                    it.copy(
                        missingRegionsWarning = null,
                        pendingModePreviewTarget = null,
                        selectedDraftMode = RoutePlaybackMode.FREE_ROAM,
                        previewMode = null,
                        previewRoute = null,
                    )
                }
                proceedWithRouteComputation(_state.value.draft.points)
            }
            RouteRequestAction.OnDismissMissingRegionsWarning ->
                _state.update { it.copy(missingRegionsWarning = null, pendingModePreviewTarget = null) }
            RouteRequestAction.OnSaveRouteClick -> _state.update { it.copy(isSaveRouteSheetOpen = true) }
            is RouteRequestAction.OnRouteNameInputChange -> _state.update { it.copy(routeNameInput = action.value) }
            RouteRequestAction.OnConfirmSaveRoute -> confirmSaveRoute()
            RouteRequestAction.OnDismissSaveRoute -> dismissSaveRoute()
            RouteRequestAction.OnSwitchRouteModeClick -> onSwitchRouteModeClick()
        }
    }

    private fun confirmSaveRoute() {
        val points = _state.value.draft.points
        val name = _state.value.routeNameInput.trim()
        if (!_state.value.isSaveRouteSheetOpen || points.size < MIN_WAYPOINTS_TO_PLAY || name.isBlank()) return
        favoriteRoutesRepository.add(name, points)
        dismissSaveRoute()
        viewModelScope.launch { _events.send(RouteRequestEvent.RouteSaved) }
    }

    private fun dismissSaveRoute() {
        _state.update { it.copy(isSaveRouteSheetOpen = false, routeNameInput = "") }
    }

    private fun confirmAddWaypoint() {
        val latitude = _state.value.pendingAddLatitude
        val longitude = _state.value.pendingAddLongitude
        if (latitude == null || longitude == null) return
        if (_state.value.isSaveAsFavoriteChecked && _state.value.favoriteNameInput.isBlank()) return
        mutateDraft { it.add(RoutePoint(latitude = latitude, longitude = longitude)) }
        if (_state.value.isSaveAsFavoriteChecked) {
            favoriteWaypointsRepository.add(_state.value.favoriteNameInput, latitude, longitude)
        }
        dismissAddWaypoint()
    }

    private fun dismissAddWaypoint() {
        _state.update {
            it.copy(
                pendingAddLatitude = null,
                pendingAddLongitude = null,
                pendingAddPlaceName = null,
                isSaveAsFavoriteChecked = false,
                favoriteNameInput = "",
            )
        }
    }


    /** Any draft mutation invalidates a previously computed/chosen route (FR-004's spirit). */
    private fun mutateDraft(transform: (RouteDraft) -> RouteDraft) {
        val wasPreviewingGuided = _state.value.previewMode == RoutePlaybackMode.GUIDED
        _state.update {
            it.copy(
                draft = transform(it.draft),
                chosenMode = null,
                route = null,
                errorType = null,
                previewMode = if (wasPreviewingGuided) RoutePlaybackMode.GUIDED else null,
                // previewRoute is deliberately left as-is here (stale, but still the right mode's
                // shape) rather than cleared to null — clearing it would flash the map to the plain
                // straight-line view for the brief recompute window below, which read as "switched
                // to Free-roam and back." refreshGuidedPreview() below replaces it with the fresh
                // result; a non-Guided preview still gets cleared since there's nothing to carry over.
                previewRoute = if (wasPreviewingGuided) it.previewRoute else null,
            )
        }
        draftWaypointsHolder.set(_state.value.draft.points)
        // A Guided preview that was already on screen should carry over and recompute with the
        // new point list, rather than silently dropping back to the default free-roam view —
        // the user has to explicitly turn it off (OnSwitchRouteModeClick) to lose it.
        if (wasPreviewingGuided) refreshGuidedPreview()
    }

    private fun refreshGuidedPreview() {
        val points = _state.value.draft.points
        if (points.size < 2) {
            _state.update { it.copy(previewMode = null, previewRoute = null) }
            return
        }
        val requiredRegions = computeRequiredRegions(points)
        if (!requiredRegions.isFullyDownloaded) {
            _state.update { it.copy(missingRegionsWarning = requiredRegions, pendingModePreviewTarget = RoutePlaybackMode.GUIDED) }
        } else {
            computeDraftPreview(points)
        }
    }

    /** Applies a committed edit from the shared reorder-list sheet (spec 008) via the same
     *  invalidation rule as every other draft mutation (FR-018) — the recompute that already ran
     *  to validate the edit is not reused here; "Finish Route" always recomputes fresh, same as
     *  after any other draft mutation today. */
    fun applyEditedWaypoints(points: List<RoutePoint>) {
        mutateDraft { RouteDraft(points = points) }
    }

    private fun openEditDialog(index: Int) {
        val point = _state.value.draft.points.getOrNull(index) ?: return
        _state.update {
            it.copy(
                editingIndex = index,
                editLatitudeInput = point.latitude.toString(),
                editLongitudeInput = point.longitude.toString(),
            )
        }
    }

    private fun confirmEdit() {
        val index = _state.value.editingIndex ?: return
        val latitude = _state.value.editLatitudeInput.toDoubleOrNull()
        val longitude = _state.value.editLongitudeInput.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            _state.update { it.copy(errorType = RouteRequestError.INVALID_EDIT_COORDINATES) }
            return
        }
        mutateDraft { it.update(index, RoutePoint(latitude = latitude, longitude = longitude)) }
        _state.update { it.copy(editingIndex = null) }
    }

    private fun deleteEditingWaypoint() {
        val index = _state.value.editingIndex ?: return
        mutateDraft { it.delete(index) }
        _state.update { it.copy(editingIndex = null) }
    }

    private fun importRoute(
        bytes: ByteArray,
        format: RouteFileFormat,
    ) {
        when (val result = importRouteFile(bytes, format)) {
            is Result.Success -> {
                _state.update {
                    it.copy(
                        draft = result.data,
                        chosenMode = null,
                        route = null,
                        selectedDraftMode = null,
                        previewMode = null,
                        previewRoute = null,
                        errorType = null,
                    )
                }
                draftWaypointsHolder.set(result.data.points)
            }
            is Result.Error -> _state.update { it.copy(errorType = result.error.toRouteRequestError()) }
        }
    }

    private fun exportRoute(format: RouteFileFormat) {
        val route = _state.value.route ?: return
        val bytes = exportRouteFile(route, format)
        viewModelScope.launch { _events.send(RouteRequestEvent.ExportReady(bytes, format)) }
    }

    /** FR + region-awareness: before computing, checks whether the map data Guided mode needs is
     *  actually downloaded, so the user learns about missing regions up front instead of Guided
     *  silently failing to compute later. */
    private fun requestRoute() {
        val points = _state.value.draft.points
        if (points.size < MIN_WAYPOINTS_TO_PLAY) {
            _state.update { it.copy(errorType = RouteRequestError.TOO_FEW_WAYPOINTS_TO_PLAY) }
            return
        }

        val requiredRegions = computeRequiredRegions(points)
        if (_state.value.selectedDraftMode != RoutePlaybackMode.FREE_ROAM && !requiredRegions.isFullyDownloaded) {
            _state.update { it.copy(missingRegionsWarning = requiredRegions) }
            return
        }
        proceedWithRouteComputation(points)
    }

    private fun proceedWithRouteComputation(points: List<RoutePoint>) {
        // A vehicle route follows roads by default. A failed road computation must not silently
        // become a valid-looking straight-line route.
        val preferredMode = _state.value.selectedDraftMode ?: RoutePlaybackMode.GUIDED
        _state.update {
            it.copy(
                isComputing = true,
                errorType = null,
                chosenMode = null,
                route = null,
                previewMode = null,
                previewRoute = null,
            )
        }
        viewModelScope.launch {
            val options = withContext(backgroundDispatcher) { prepareRouteOptions(points) }
            if (preferredMode == RoutePlaybackMode.GUIDED) {
                val guided = options.guided
                if (guided == null) {
                    _state.update { it.copy(isComputing = false, errorType = RouteRequestError.MODE_PREVIEW_NO_PATH) }
                } else {
                    finalizeRoute(guided, RoutePlaybackMode.GUIDED, options)
                }
            } else {
                finalizeRoute(options.freeRoam, RoutePlaybackMode.FREE_ROAM, options)
            }
        }
    }

    private fun finalizeRoute(
        route: Route,
        mode: RoutePlaybackMode,
        options: RouteOptions,
    ) {
        val withAlternate =
            when (mode) {
                RoutePlaybackMode.GUIDED ->
                    route.copy(alternateGeometry = options.freeRoam.geometry, alternateDistanceMeters = options.freeRoam.distanceMeters)
                RoutePlaybackMode.FREE_ROAM ->
                    route.copy(alternateGeometry = options.guided?.geometry, alternateDistanceMeters = options.guided?.distanceMeters)
            }
        _state.update { it.copy(isComputing = false, chosenMode = mode, route = withAlternate, selectedDraftMode = mode) }
        lastComputedRouteHolder.set(withAlternate)
    }

    /** The user explicitly confirms they're done previewing/exporting and wants to play this
     *  route — navigates straight to Simulate with no extra "go to the map?" confirmation; the
     *  mode and route are already fully decided by this point, so that question added nothing.
     *  The route is already in [lastComputedRouteHolder] from [finalizeRoute]. */
    private fun useRoute() {
        _state.value.route ?: return
        viewModelScope.launch { _events.send(RouteRequestEvent.GoToSimulate) }
    }

    /** Lets the user preview/switch between Guided and Free-roam at any planning-time point —
     *  before an official route is computed (toggles [RouteRequestState.previewRoute], independent
     *  of [RouteRequestState.route], never opens the ready sheet), or after ([route] already
     *  computed). Neither case needs confirmation — switching modes while planning has no real
     *  consequence (nothing is being played). Only Simulate's execution-time switch asks to
     *  confirm, since that one affects a live session. */
    private fun onSwitchRouteModeClick() {
        val route = _state.value.route
        if (route != null) {
            val targetMode = if (route.mode == RoutePlaybackMode.GUIDED) RoutePlaybackMode.FREE_ROAM else RoutePlaybackMode.GUIDED
            val alternateGeometry = route.alternateGeometry
            val alternateDistance = route.alternateDistanceMeters
            if (alternateGeometry != null && alternateDistance != null) {
                applyModeSwitch(route, targetMode, alternateGeometry, alternateDistance)
            } else {
                val requiredRegions = computeRequiredRegions(route.points)
                if (!requiredRegions.isFullyDownloaded) {
                    _state.update { it.copy(missingRegionsWarning = requiredRegions, pendingModePreviewTarget = targetMode) }
                } else {
                    attemptModePreview(targetMode)
                }
            }
            return
        }

        if (_state.value.previewMode == RoutePlaybackMode.GUIDED) {
            _state.update { it.copy(previewMode = null, previewRoute = null, selectedDraftMode = RoutePlaybackMode.FREE_ROAM, errorType = null) }
            return
        }
        refreshGuidedPreview()
    }

    /** Computes a live Guided preview for the current draft (no official route exists yet) — the
     *  missing-regions check, if needed, already ran in [onSwitchRouteModeClick] or was just
     *  dismissed via "continue anyway". On failure (genuinely no viable path), surfaces
     *  [RouteRequestError.MODE_PREVIEW_NO_PATH]. */
    private fun computeDraftPreview(points: List<RoutePoint>) {
        _state.update { it.copy(isComputingPreview = true) }
        viewModelScope.launch {
            val options = withContext(backgroundDispatcher) { prepareRouteOptions(points) }
            val guided = options.guided
            if (guided == null) {
                _state.update { it.copy(isComputingPreview = false, errorType = RouteRequestError.MODE_PREVIEW_NO_PATH) }
                return@launch
            }
            _state.update { it.copy(isComputingPreview = false, previewMode = RoutePlaybackMode.GUIDED, previewRoute = guided, selectedDraftMode = RoutePlaybackMode.GUIDED, errorType = null) }
        }
    }

    /** Computes [targetMode]'s geometry on demand (the missing-regions check, if needed, already ran
     *  in [onSwitchRouteModeClick] or was just dismissed via "continue anyway"). On success, applies
     *  it immediately via [applyModeSwitch] — no confirmation while planning, see
     *  [onSwitchRouteModeClick]. On failure (genuinely no viable path, not a download problem),
     *  surfaces [RouteRequestError.MODE_PREVIEW_NO_PATH] instead. */
    private fun attemptModePreview(targetMode: RoutePlaybackMode) {
        val route = _state.value.route ?: return
        _state.update { it.copy(isComputingPreview = true) }
        viewModelScope.launch {
            val options = withContext(backgroundDispatcher) { prepareRouteOptions(route.points) }
            val targetRoute =
                when (targetMode) {
                    RoutePlaybackMode.GUIDED -> options.guided
                    RoutePlaybackMode.FREE_ROAM -> options.freeRoam
                }
            if (targetRoute == null) {
                _state.update { it.copy(isComputingPreview = false, errorType = RouteRequestError.MODE_PREVIEW_NO_PATH) }
                return@launch
            }
            _state.update { it.copy(isComputingPreview = false) }
            applyModeSwitch(route, targetMode, targetRoute.geometry, targetRoute.distanceMeters)
        }
    }

    private fun applyModeSwitch(
        route: Route,
        targetMode: RoutePlaybackMode,
        alternateGeometry: List<LatLng>,
        alternateDistanceMeters: Double,
    ) {
        val swapped = route.swapToMode(targetMode, alternateGeometry, alternateDistanceMeters)
        _state.update { it.copy(route = swapped, chosenMode = targetMode, selectedDraftMode = targetMode) }
        lastComputedRouteHolder.set(swapped)
    }

}

private fun RouteFileFailure.toRouteRequestError(): RouteRequestError =
    when (this) {
        RouteFileFailure.TooFewWaypoints -> RouteRequestError.IMPORT_TOO_FEW_WAYPOINTS
        is RouteFileFailure.CoordinateOutOfRange -> RouteRequestError.IMPORT_COORDINATE_OUT_OF_RANGE
        RouteFileFailure.Malformed -> RouteRequestError.IMPORT_MALFORMED
    }

