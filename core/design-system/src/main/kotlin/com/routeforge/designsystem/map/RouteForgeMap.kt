package com.routeforge.designsystem.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.MapStyle
import com.routeforge.designsystem.R
import com.routeforge.designsystem.theme.MapColors
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private const val OSMDROID_PREFS_NAME = "osmdroid_config_routeforge"
private const val DEFAULT_MAP_ZOOM = 15.0
private const val FALLBACK_WORLD_MAP_ZOOM = 3.0
private const val ROUTE_LINE_WIDTH_PX = 6f
private const val TRAVELED_LINE_ALPHA = 55
private const val ARROW_BORDER_WIDTH_FRACTION = 0.06f
private const val REGION_OVERLAY_OUTLINE_WIDTH_PX = 3f
private const val REGION_OVERLAY_FILL_ALPHA = 0.35f
private const val GHOST_MARKER_ALPHA = 0.4f
private val MarkerDotSize = 20.dp
private val MarkerBadgeSize = 28.dp
private val MarkerArrowSize = 32.dp

// Free, non-profit mobile apps with moderate traffic and attribution are permitted:
// https://www.openstreetmap.fr/usage/ . This is a separate cache namespace from Mapnik.
private val OsmFranceTileSource =
    XYTileSource(
        "RouteForge-OSM-France-HOT", 1, 19, 256, ".png",
        arrayOf("https://a.tile.openstreetmap.fr/hot/"),
        "© OpenStreetMap contributors · HOT · OSM France",
    )

private fun MapStyle.toTileSource(): ITileSource =
    when (this) {
        MapStyle.OSM_FR -> OsmFranceTileSource
        MapStyle.STANDARD -> TileSourceFactory.MAPNIK
        MapStyle.TOPOGRAPHIC -> TileSourceFactory.OpenTopo
    }

private fun buildDotDrawable(
    context: Context,
    colorArgb: Int,
    sizePx: Int,
): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb; style = Paint.Style.FILL }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun buildNumberedDrawable(
    context: Context,
    number: Int,
    backgroundColorArgb: Int,
    textColorArgb: Int,
    sizePx: Int,
): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColorArgb; style = Paint.Style.FILL }
    canvas.drawCircle(radius, radius, radius, backgroundPaint)
    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.55f
        }
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(number.toString(), radius, textY, textPaint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun buildArrowDrawable(
    context: Context,
    colorArgb: Int,
    sizePx: Int,
): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // A north-facing chevron (Waze/Google-Maps-style nav puck); rotated per-marker via
    // Marker.rotation to reflect the mocked position's current bearing.
    val path =
        Path().apply {
            moveTo(sizePx * 0.5f, 0f)
            lineTo(sizePx * 0.9f, sizePx * 0.9f)
            lineTo(sizePx * 0.5f, sizePx * 0.65f)
            lineTo(sizePx * 0.1f, sizePx * 0.9f)
            close()
        }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb; style = Paint.Style.FILL }
    canvas.drawPath(path, fillPaint)
    val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * ARROW_BORDER_WIDTH_FRACTION
        }
    canvas.drawPath(path, borderPaint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun RouteForgeMapMarkerIcon.toDrawable(
    context: Context,
    density: Density,
): BitmapDrawable =
    when (this) {
        is RouteForgeMapMarkerIcon.Dot -> {
            val sizePx = with(density) { MarkerDotSize.roundToPx() }
            buildDotDrawable(context, colorArgb, sizePx)
        }
        is RouteForgeMapMarkerIcon.Numbered -> {
            val sizePx = with(density) { MarkerBadgeSize.roundToPx() }
            buildNumberedDrawable(context, number, backgroundColorArgb, textColorArgb, sizePx)
        }
        is RouteForgeMapMarkerIcon.Arrow -> {
            val sizePx = with(density) { MarkerArrowSize.roundToPx() }
            buildArrowDrawable(context, colorArgb, sizePx)
        }
    }

private data class RouteForgeMapComponents(
    val mapView: MapView,
    val routeOverlay: PulsingRouteOverlay,
    val traveledOverlay: Polyline,
)

/** [RouteForgeMapMarker] minus its onClick/onDrag/onDragEnd closures — used as a [LaunchedEffect]
 *  key instead of the marker itself, since those closures get a new identity on every
 *  recomposition (e.g. from a caller-side `onAction` reference changing), which would make the
 *  whole marker's own equals() unstable for this purpose. */
private data class MarkerStableKey(
    val id: Any,
    val latitude: Double,
    val longitude: Double,
    val icon: RouteForgeMapMarkerIcon,
    val draggable: Boolean,
    val rotationDegrees: Float,
)

private fun buildMapComponents(
    context: Context,
    onTap: (Double, Double) -> Unit,
): RouteForgeMapComponents {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences(OSMDROID_PREFS_NAME, Context.MODE_PRIVATE))
        userAgentValue = "RouteForge-zh/1.2.2-zh (${context.packageName}; https://github.com/jojo9527bestgod-netizen/RouteForge-zh)"
    }
    val mapView =
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(FALLBACK_WORLD_MAP_ZOOM)
        }
    val tapOverlay =
        MapEventsOverlay(
            object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    onTap(point.latitude, point.longitude)
                    return true
                }

                override fun longPressHelper(point: GeoPoint): Boolean = false
            },
        )
    mapView.overlays.add(tapOverlay)
    val routeOverlay = buildPulsingRouteOverlay(mapView)
    val traveledOverlay = Polyline().apply { outlinePaint.strokeWidth = ROUTE_LINE_WIDTH_PX }
    mapView.overlays.add(traveledOverlay)
    return RouteForgeMapComponents(mapView, routeOverlay, traveledOverlay)
}

/**
 * Shared OSM map surface used by every RouteForge screen that renders a map (Simulation, Route
 * creation). Callers describe *what* to show (markers, an optional polyline, a tap callback);
 * this component owns the OSMDroid `MapView` lifecycle and overlay bookkeeping.
 *
 * Camera movement is caller-driven: pass a new [cameraTarget] value whenever the screen decides
 * the map should recenter (e.g. "a mocked session just started" or "the first waypoint was just
 * placed") — the map does not decide this on its own, since different screens want different
 * auto-centering policies (Simulation recenters per new session; route-building recenters once).
 */
@Composable
fun RouteForgeMap(
    markers: List<RouteForgeMapMarker>,
    modifier: Modifier = Modifier,
    polylinePoints: List<LatLng> = emptyList(),
    traveledPolylinePoints: List<LatLng> = emptyList(),
    regions: List<RouteForgeMapRegionOverlay> = emptyList(),
    cameraTarget: LatLng? = null,
    mapStyle: MapStyle = MapStyle.DEFAULT,
    onMapTap: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTap by rememberUpdatedState(onMapTap)
    val routeColorArgb = MapColors.RouteLine.toArgb()
    val regionFillColorArgb = MaterialTheme.colorScheme.primary.copy(alpha = REGION_OVERLAY_FILL_ALPHA).toArgb()

    val components =
        remember {
            buildMapComponents(
                context = context,
                onTap = { latitude, longitude -> currentOnTap?.invoke(latitude, longitude) },
            )
        }

    LaunchedEffect(mapStyle) {
        components.mapView.setTileSource(mapStyle.toTileSource())
        components.mapView.invalidate()
    }

    LaunchedEffect(routeColorArgb) {
        components.routeOverlay.setColor(routeColorArgb, ROUTE_LINE_WIDTH_PX)
        components.traveledOverlay.outlinePaint.color = (TRAVELED_LINE_ALPHA shl 24) or (routeColorArgb and 0x00FFFFFF)
        components.mapView.invalidate()
    }

    AnimateRoutePulse(overlay = components.routeOverlay, onPulse = { components.mapView.invalidate() })

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> components.mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> components.mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            components.mapView.onDetach()
        }
    }

    // Keyed on a representation that excludes onClick/onDrag/onDragEnd (closures that get a new
    // identity on every recomposition, e.g. from a caller-side `onAction` reference changing) —
    // otherwise every recomposition during an active drag (onDrag fires continuously) would tear
    // down and rebuild every Marker, including the one the user's finger is still on.
    val markersKey =
        markers.map { MarkerStableKey(it.id, it.latitude, it.longitude, it.icon, it.draggable, it.rotationDegrees) }
    LaunchedEffect(markersKey) {
        val mapView = components.mapView
        mapView.overlays.removeAll { it is Marker }
        for (marker in markers) {
            val osmMarker =
                Marker(mapView).apply {
                    position = GeoPoint(marker.latitude, marker.longitude)
                    icon = marker.icon.toDrawable(context, density)
                    isDraggable = marker.draggable
                    if (marker.icon is RouteForgeMapMarkerIcon.Arrow) {
                        // Non-flat markers ignore rotation in osmdroid — flat mode is required
                        // for the icon to actually turn to face marker.rotationDegrees.
                        setFlat(true)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    // osmdroid negates rotation for flat markers internally (Marker.java:
                    // rotationOnScreen = -mBearing), so negate here to keep rotationDegrees'
                    // documented clockwise-from-north contract true on screen.
                    rotation = -marker.rotationDegrees
                    marker.onClick?.let { onClick ->
                        setOnMarkerClickListener { _, _ ->
                            onClick()
                            true
                        }
                    }
                    marker.onDragEnd?.let { onDragEnd ->
                        val onDragCallback = marker.onDrag
                        var ghostMarker: Marker? = null
                        setOnMarkerDragListener(
                            object : Marker.OnMarkerDragListener {
                                override fun onMarkerDragStart(marker: Marker) {
                                    ghostMarker =
                                        Marker(mapView).apply {
                                            position = GeoPoint(marker.position.latitude, marker.position.longitude)
                                            icon = marker.icon
                                            setAlpha(GHOST_MARKER_ALPHA)
                                            isDraggable = false
                                        }
                                    mapView.overlays.add(ghostMarker)
                                    mapView.invalidate()
                                }

                                override fun onMarkerDrag(marker: Marker) {
                                    onDragCallback?.invoke(marker.position.latitude, marker.position.longitude)
                                }

                                override fun onMarkerDragEnd(marker: Marker) {
                                    ghostMarker?.let { mapView.overlays.remove(it) }
                                    ghostMarker = null
                                    mapView.invalidate()
                                    onDragEnd(marker.position.latitude, marker.position.longitude)
                                }
                            },
                        )
                    }
                }
            mapView.overlays.add(osmMarker)
        }
        mapView.invalidate()
    }

    LaunchedEffect(regions, regionFillColorArgb) {
        val mapView = components.mapView
        mapView.overlays.removeAll { it is Polygon }
        for (region in regions) {
            val polygon =
                Polygon(mapView).apply {
                    setPoints(
                        listOf(
                            GeoPoint(region.minLatitude, region.minLongitude),
                            GeoPoint(region.minLatitude, region.maxLongitude),
                            GeoPoint(region.maxLatitude, region.maxLongitude),
                            GeoPoint(region.maxLatitude, region.minLongitude),
                        ),
                    )
                    outlinePaint.color = Color.BLACK
                    outlinePaint.strokeWidth = REGION_OVERLAY_OUTLINE_WIDTH_PX
                    fillPaint.color = if (region.filled) regionFillColorArgb else Color.TRANSPARENT
                    // Without this, Polygon's built-in tap handling (PolyOverlayWithIW.onSingleTapConfirmed)
                    // consumes the tap itself and pops its own blank info-window bubble, so onMapTap below
                    // would never fire for taps landing inside a region.
                    setOnClickListener { _, _, _ -> false }
                }
            mapView.overlays.add(polygon)
        }
        mapView.invalidate()
    }

    LaunchedEffect(polylinePoints) {
        components.routeOverlay.setPoints(polylinePoints)
        components.mapView.invalidate()
    }

    LaunchedEffect(traveledPolylinePoints) {
        components.traveledOverlay.setPoints(traveledPolylinePoints.map { (latitude, longitude) -> GeoPoint(latitude, longitude) })
        components.mapView.invalidate()
    }

    val hasSetInitialZoom = remember { mutableStateOf(false) }
    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        if (!hasSetInitialZoom.value) {
            components.mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
            hasSetInitialZoom.value = true
        }
        components.mapView.controller.setCenter(GeoPoint(target.latitude, target.longitude))
    }

    val uriHandler = LocalUriHandler.current
    val attributionResource =
        when (mapStyle) {
            MapStyle.OSM_FR -> R.string.map_attribution_osm_fr
            MapStyle.STANDARD -> R.string.map_attribution_standard
            MapStyle.TOPOGRAPHIC -> R.string.map_attribution_topographic
        }
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { components.mapView }, modifier = Modifier.fillMaxSize())
        Text(
            text = stringResource(attributionResource),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
