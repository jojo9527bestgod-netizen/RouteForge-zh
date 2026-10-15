package com.routeforge.addresssearch.presentation

import com.routeforge.coredomain.model.PlaceSearchResult

enum class AddressSearchError {
    NETWORK_FAILURE,
    TIMEOUT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
    INVALID_COORDINATES,
}

data class AddressSearchState(
    val sourceContext: SearchSourceContext = SearchSourceContext.TELEPORT_OR_FAVORITE,
    val query: String = "",
    val isLoading: Boolean = false,
    /** Distinguishes "haven't searched yet" (plain empty field) from "searched, zero results". */
    val hasSearched: Boolean = false,
    val results: List<PlaceSearchResult> = emptyList(),
    val errorType: AddressSearchError? = null,
    /** Non-null while the Teleport/Save-as-favorite choice is showing for this result
     *  ([sourceContext] == [SearchSourceContext.TELEPORT_OR_FAVORITE] only). */
    val pendingChoiceResult: PlaceSearchResult? = null,
    val isNamingFavorite: Boolean = false,
    val favoriteNameInput: String = "",
)
