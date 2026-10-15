package com.routeforge.coredomain.model

/** A single geocoded candidate returned by a place/address search — one row in a results list.
 *  [importance] represents service relevance, used alongside distance-to-reference-point for
 *  ranking. Services without an explicit score can derive it from their result order. */
data class PlaceSearchResult(
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val importance: Double,
    /** A locally parsed WGS84 coordinate, with no geocoding or inferred place name. */
    val isCoordinate: Boolean = false,
)
