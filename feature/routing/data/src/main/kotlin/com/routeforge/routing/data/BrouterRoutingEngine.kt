package com.routeforge.routing.data

import btools.mapaccess.OsmNode
import btools.router.OsmNodeNamed
import btools.router.OsmTrack
import btools.router.RoutingContext
import com.routeforge.coredomain.GeoMath
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.coredomain.RoutingEngine
import com.routeforge.coredomain.model.Region
import java.io.File
import android.util.Log
import btools.router.RoutingEngine as BrouterEngine

class BrouterRoutingEngine(
    private val segmentDir: File,
    private val profileFile: File,
) : RoutingEngine {
    override fun snap(
        point: RoutePoint,
        availableRegions: List<Region>,
    ): RoutePoint {
        val isWithinKnownCoverage = availableRegions.any { it.contains(point.latitude, point.longitude) }
        if (!isWithinKnownCoverage) return point

        val duplicatedWaypoint =
            listOf(
                toOsmNodeNamed(point, "snap_start"),
                toOsmNodeNamed(point, "snap_end"),
            )
        val track = runEngine(duplicatedWaypoint) ?: return point
        val matchedNode = track.nodes.firstOrNull() ?: return point
        return point.copy(
            snappedLatitude = decodeLatitude(matchedNode.getILat()),
            snappedLongitude = decodeLongitude(matchedNode.getILon()),
        )
    }

    override fun computePath(points: List<RoutePoint>): Route? {
        if (points.size < 2) return null

        val waypoints = points.mapIndexed { index, point -> toOsmNodeNamed(point, "wp$index") }
        val track = runEngine(waypoints) ?: return null

        val geometry = track.nodes.map { LatLng(decodeLatitude(it.getILat()), decodeLongitude(it.getILon())) }
        return Route(
            points = points,
            geometry = geometry,
            distanceMeters = track.distance.toDouble(),
            waypointCumulativeDistances = computeWaypointCumulativeDistances(track, points.size, geometry),
        )
    }

    /** Correlates each original input waypoint (named `wp0`, `wp1`, ... in [computePath]) back to
     *  the track-node index it landed at, via [OsmTrack.getMatchedWaypoint]'s
     *  [MatchedWaypoint.name]/[MatchedWaypoint.indexInTrack] — BRouter tracks this internally but
     *  [OsmTrack] never otherwise exposes a waypoint-to-geometry-position mapping (spec 008's
     *  research.md Decision 4). Falls back to an empty list (meaning "boundary unknown, treat
     *  everything as editable," per [Route.waypointCumulativeDistances]'s own contract) if any
     *  waypoint's track position can't be found — e.g. an older/alternate BRouter build that
     *  doesn't populate this internal matching. */
    private fun computeWaypointCumulativeDistances(
        track: OsmTrack,
        waypointCount: Int,
        geometry: List<LatLng>,
    ): List<Double> {
        val trackIndexByWaypointName =
            geometry.indices
                .mapNotNull { nodeIndex -> track.getMatchedWaypoint(nodeIndex)?.let { it.name to nodeIndex } }
                .toMap()

        val cumulativeAtNode = mutableListOf(0.0)
        for (i in 1 until geometry.size) {
            val (lat1, lon1) = geometry[i - 1]
            val (lat2, lon2) = geometry[i]
            cumulativeAtNode.add(cumulativeAtNode.last() + GeoMath.haversineMeters(lat1, lon1, lat2, lon2))
        }

        val result = mutableListOf<Double>()
        for (waypointIndex in 0 until waypointCount) {
            val nodeIndex = trackIndexByWaypointName["wp$waypointIndex"] ?: return emptyList()
            result.add(cumulativeAtNode[nodeIndex])
        }
        return result
    }

    private fun runEngine(waypoints: List<OsmNodeNamed>): OsmTrack? {
        val routingContext =
            RoutingContext().apply {
                localFunction = profileFile.absolutePath
            }
        val engine = BrouterEngine(null, null, segmentDir, ArrayList(waypoints), routingContext)
        engine.run()
        val error = engine.getErrorMessage()
        if (error != null) {
            Log.w("BrouterRoutingEngine", "Road route computation failed: $error")
            return null
        }
        return engine.getFoundTrack()
    }

    private fun toOsmNodeNamed(
        point: RoutePoint,
        waypointName: String,
    ): OsmNodeNamed {
        val latitude = point.snappedLatitude ?: point.latitude
        val longitude = point.snappedLongitude ?: point.longitude
        val ilon = Math.round((longitude + 180.0) * 1_000_000.0).toInt()
        val ilat = Math.round((latitude + 90.0) * 1_000_000.0).toInt()
        return OsmNodeNamed(OsmNode(ilon, ilat)).apply { name = waypointName }
    }

    private fun decodeLongitude(ilon: Int): Double = ilon / 1_000_000.0 - 180.0

    private fun decodeLatitude(ilat: Int): Double = ilat / 1_000_000.0 - 90.0
}
