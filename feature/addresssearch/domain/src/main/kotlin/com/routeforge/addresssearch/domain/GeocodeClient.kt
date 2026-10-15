package com.routeforge.addresssearch.domain

import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.model.PlaceSearchResult

/** Geocodes a free-text query into a list of candidate places, capped at [MAX_SEARCH_RESULTS].
 *  Implemented in `feature:addresssearch:data` against the public Photon API. */
interface GeocodeClient {
    suspend fun search(query: String): Result<List<PlaceSearchResult>, DataError.Network>

    companion object {
        /** The maximum number of candidates an implementation should request from its backing
         *  service — the single source the data layer's fetch limit and
         *  [com.routeforge.addresssearch.domain.usecase.SearchPlacesUseCase]'s own contract both
         *  rely on, so they can't silently drift apart into two independently-guessed numbers. */
        const val MAX_SEARCH_RESULTS = 10
    }
}
