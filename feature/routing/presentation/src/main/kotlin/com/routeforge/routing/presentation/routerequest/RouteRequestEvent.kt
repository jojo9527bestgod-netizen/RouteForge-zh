package com.routeforge.routing.presentation.routerequest

import com.routeforge.routing.domain.model.RouteFileFormat

sealed interface RouteRequestEvent {
    data object GoToSimulate : RouteRequestEvent

    class ExportReady(
        val bytes: ByteArray,
        val format: RouteFileFormat,
    ) : RouteRequestEvent

    data object NavigateToRegionCatalog : RouteRequestEvent

    data object NavigateToFavorites : RouteRequestEvent
    data object RouteSaved : RouteRequestEvent
}
