package com.routeforge.addresssearch.data

import com.routeforge.coredomain.model.PlaceSearchResult
import kotlinx.serialization.Serializable

@Serializable
internal data class PhotonSearchResponseDto(val features: List<PhotonFeatureDto>)

@Serializable
internal data class PhotonFeatureDto(
    val properties: PhotonPropertiesDto = PhotonPropertiesDto(),
    val geometry: PhotonGeometryDto? = null,
)

@Serializable
internal data class PhotonPropertiesDto(
    val name: String? = null,
    val housenumber: String? = null,
    val street: String? = null,
    val locality: String? = null,
    val district: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postcode: String? = null,
)

@Serializable
internal data class PhotonGeometryDto(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

/** GeoJSON is longitude first. Invalid entries must never become route waypoints. */
internal fun PhotonFeatureDto.toPlaceSearchResult(importance: Double): PlaceSearchResult? {
    val point = geometry?.takeIf { it.type == "Point" } ?: return null
    val longitude = point.coordinates.getOrNull(0) ?: return null
    val latitude = point.coordinates.getOrNull(1) ?: return null
    if (!longitude.isFinite() || longitude !in -180.0..180.0 || !latitude.isFinite() || latitude !in -90.0..90.0) return null
    val name = listOf(properties.name, properties.street, properties.locality, properties.city, properties.country)
        .firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
    val address = listOf(
        name, properties.housenumber, properties.street, properties.locality, properties.district,
        properties.city, properties.county, properties.state, properties.country, properties.postcode,
    ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(", ")
    return PlaceSearchResult(name, address, latitude, longitude, importance)
}
