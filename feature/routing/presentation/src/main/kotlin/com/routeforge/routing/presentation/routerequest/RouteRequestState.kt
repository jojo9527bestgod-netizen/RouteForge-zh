package com.routeforge.routing.presentation.routerequest

import com.routeforge.coredomain.model.MapStyle
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.RealLocation
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RequiredRegionsSummary
import com.routeforge.routing.domain.model.RouteDraft

data class RouteRequestState(
    val draft: RouteDraft = RouteDraft(),
    val editingIndex: Int? = null,
    val editLatitudeInput: String = "",
    val editLongitudeInput: String = "",
    val isComputing: Boolean = false,
    val chosenMode: RoutePlaybackMode? = null,
    val route: Route? = null,
    val errorType: RouteRequestError? = null,
    val lastKnownLocation: RealLocation? = null,
    /** Active simulated position, otherwise the latest known real position. */
    val currentLocation: LatLng? = null,
    val missingRegionsWarning: RequiredRegionsSummary? = null,
    val pendingAddLatitude: Double? = null,
    val pendingAddLongitude: Double? = null,
    /** Set when the pending add came from address search (not a map tap) — lets the confirmation
     *  sheet name the place instead of only showing raw coordinates. */
    val pendingAddPlaceName: String? = null,
    /** Last confirmed search point; lets the map reveal distant results without following map taps. */
    val searchCameraTarget: LatLng? = null,
    val isSaveAsFavoriteChecked: Boolean = false,
    val favoriteNameInput: String = "",
    val isSaveRouteSheetOpen: Boolean = false,
    val routeNameInput: String = "",
    val pendingModePreviewTarget: RoutePlaybackMode? = null,
    /** A live preview of the draft in Guided mode, before any official "request route" computation
     *  (independent of [route], so it never opens the ready sheet). Null means the map shows the
     *  default straight-line draft preview. */
    val previewMode: RoutePlaybackMode? = null,
    /** Null means the default road-following mode; straight lines require an explicit choice. */
    val selectedDraftMode: RoutePlaybackMode? = null,
    val previewRoute: Route? = null,
    /** True while [computeDraftPreview]/[attemptModePreview] is running — drives a loading spinner
     *  on whichever button triggered it, instead of leaving the user guessing why nothing changed
     *  yet. */
    val isComputingPreview: Boolean = false,
    val mapStyle: MapStyle = MapStyle.DEFAULT,
)
