package com.routeforge.addresssearch.domain.usecase

import com.routeforge.addresssearch.domain.GeocodeClient
import com.routeforge.addresssearch.domain.CoordinateQuery
import com.routeforge.addresssearch.domain.CoordinateQueryParser
import com.routeforge.addresssearch.domain.SearchPlacesError
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.model.PlaceSearchResult

/** Parses coordinate pairs locally, otherwise calls [GeocodeClient] (which caps result count at
 *  [GeocodeClient.MAX_SEARCH_RESULTS]), then ranks via [RankSearchResultsUseCase]
 *  using the user's current real location if known, else [fallbackReferenceLatitude]/
 *  [fallbackReferenceLongitude] (the caller's current map center), else no reference point at all
 *  (research.md Decision 5). */
class SearchPlacesUseCase(
    private val geocodeClient: GeocodeClient,
    private val rankSearchResults: RankSearchResultsUseCase,
    private val lastKnownRealLocationHolder: LastKnownRealLocationHolder,
) {
    suspend operator fun invoke(
        query: String,
        fallbackReferenceLatitude: Double?,
        fallbackReferenceLongitude: Double?,
    ): Result<List<PlaceSearchResult>, SearchPlacesError> {
        when (val coordinate = CoordinateQueryParser.parse(query)) {
            CoordinateQuery.InvalidCoordinates -> return Result.Error(SearchPlacesError.InvalidCoordinates)
            is CoordinateQuery.Position -> {
                val label = "${coordinate.latitude}, ${coordinate.longitude}"
                return Result.Success(listOf(PlaceSearchResult(
                    name = label,
                    formattedAddress = label,
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                    importance = 1.0,
                    isCoordinate = true,
                )))
            }
            CoordinateQuery.NotCoordinates -> Unit
        }
        return when (val result = geocodeClient.search(query)) {
            is Result.Success -> {
                val realLocation = lastKnownRealLocationHolder.location.value
                val referenceLatitude = realLocation?.latitude ?: fallbackReferenceLatitude
                val referenceLongitude = realLocation?.longitude ?: fallbackReferenceLongitude
                Result.Success(rankSearchResults(result.data, referenceLatitude, referenceLongitude))
            }
            is Result.Error -> Result.Error(SearchPlacesError.Network(result.error))
        }
    }
}
