package com.routeforge.coredomain.model

/** OSM-based map styles sharing the same WGS84 coordinates as routing and simulation. */
enum class MapStyle {
    /** Alternative OSM rendering, reachable on the validation phone's network. */
    OSM_FR,

    /** The standard OpenStreetMap "Mapnik" style — what the map looked like before this setting existed. */
    STANDARD,

    /** Terrain/elevation-shaded style (OpenTopoMap). */
    TOPOGRAPHIC,
    ;

    companion object {
        val DEFAULT = OSM_FR
    }
}
