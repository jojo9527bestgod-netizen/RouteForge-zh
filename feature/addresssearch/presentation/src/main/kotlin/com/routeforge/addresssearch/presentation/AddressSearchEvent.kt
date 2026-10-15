package com.routeforge.addresssearch.presentation

sealed interface AddressSearchEvent {
    /** Sent once an outcome (add-waypoint hand-off, teleport, or favorite save) has been
     *  committed, or when the user explicitly leaves without picking anything. */
    data object NavigateBack : AddressSearchEvent

    data object NavigateToPlan : AddressSearchEvent

    data object NavigateToSimulate : AddressSearchEvent
}
