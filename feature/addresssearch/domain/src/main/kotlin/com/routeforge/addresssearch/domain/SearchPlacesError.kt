package com.routeforge.addresssearch.domain

import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.DomainError

sealed interface SearchPlacesError : DomainError {
    data object InvalidCoordinates : SearchPlacesError
    data class Network(val error: DataError.Network) : SearchPlacesError
}
