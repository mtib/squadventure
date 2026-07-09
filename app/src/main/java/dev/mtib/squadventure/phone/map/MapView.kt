package dev.mtib.squadventure.phone.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.mtib.squadventure.core.geo.SlippyTile
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.phone.ui.Squadrat
import dev.mtib.squadventure.phone.ui.Squadratinho
import dev.mtib.squadventure.phone.ui.Trail
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.android.maps.MapView as NativeMapView

/** Claimed squares to overlay: [squadratinhos] (z17) and [squadrats] (z14), as packed tile keys. */
data class MapClaims(
    val squadrats: Set<Long> = emptySet(),
    val squadratinhos: Set<Long> = emptySet(),
)

/**
 * The app's map surface — a [NativeMapView] rendering the bundled offline PMTiles basemap
 * (`assets/map/basemap.pmtiles` + `assets/map/style.json`) via MapLibre Native. Public contract
 * shared by the screens; app data (claimed squares, routes, heatmap, current-location marker) is
 * drawn as runtime GeoJSON sources/layers added above the basemap's own style layers.
 *
 * @param claims claimed squares to fill.
 * @param routes GPX paths to draw. On the global map these feed the point-density heatmap when
 *   [showHeatmap] is on; on a detail/record screen this is the single activity's route, drawn as
 *   a solid line.
 * @param showHeatmap toggles the heatmap layer (mutually exclusive with the route line layer).
 * @param showSquares toggles the claimed-square overlay.
 * @param focus optional point to center/zoom on initially (e.g. an activity's start).
 * @param currentLocation optional live position to mark with a "you are here" dot, drawn above
 *   every other layer; the marker layer is hidden when this is null.
 */
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    claims: MapClaims = MapClaims(),
    routes: List<List<TrackPoint>> = emptyList(),
    showHeatmap: Boolean = false,
    showSquares: Boolean = true,
    focus: TrackPoint? = null,
    currentLocation: TrackPoint? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    val styleJson = remember { MapAssets.styleJson(context) }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            mapView.getMapAsync { map ->
                maplibreMap = map
                map.setStyle(Style.Builder().fromJson(styleJson))
                map.getStyle { loadedStyle -> style = loadedStyle }
            }
            mapView
        },
    )

    LaunchedEffect(maplibreMap) {
        val map = maplibreMap
        if (map != null && !cameraInitialized) {
            cameraInitialized = true
            map.cameraPosition = initialCameraPosition(focus)
        }
    }

    LaunchedEffect(style, claims, routes, showHeatmap, showSquares, currentLocation) {
        val loadedStyle = style ?: return@LaunchedEffect
        ensureOverlayLayers(loadedStyle)
        updateSquareLayers(loadedStyle, claims, showSquares)
        updateTrailLayers(loadedStyle, routes, showHeatmap)
        updateCurrentLocationLayer(loadedStyle, currentLocation)
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): NativeMapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        NativeMapView(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose { context.applicationContext.unregisterComponentCallbacks(callbacks) }
    }

    return mapView
}

/**
 * Prepares the offline basemap for MapLibre. The PMTiles reader does random-access byte-range reads,
 * which are unreliable through `asset://` on Android (they return wrong bytes and the gzip decode
 * fails with "incorrect header check"), so the bundled `.pmtiles` is copied once into internal
 * storage and referenced via `pmtiles://file://…`. The style JSON is read from assets and its
 * placeholder source URL rewritten to that path.
 */
private object MapAssets {
    private const val PMTILES_ASSET = "map/basemap.pmtiles"
    private const val STYLE_ASSET = "map/style.json"
    private const val ASSET_PMTILES_URL = "pmtiles://asset://map/basemap.pmtiles"

    fun styleJson(context: android.content.Context): String {
        val pmtilesFile = copyPmtilesToStorage(context)
        val template = context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
        return template.replace(ASSET_PMTILES_URL, "pmtiles://file://${pmtilesFile.absolutePath}")
    }

    private fun copyPmtilesToStorage(context: android.content.Context): java.io.File {
        val out = java.io.File(context.filesDir, "basemap.pmtiles")
        val expectedSize = context.assets.openFd(PMTILES_ASSET).use { it.length }
        if (out.exists() && out.length() == expectedSize) return out
        context.assets.open(PMTILES_ASSET).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}
private const val WORLD_ZOOM = 1.0
private const val FOCUS_ZOOM = 14.0
private const val HEATMAP_RADIUS_PX = 18f
private const val HEATMAP_INTENSITY = 1f
private const val HEATMAP_OPACITY = 0.7f
private const val ROUTE_LINE_WIDTH_PX = 4f
private const val CURRENT_LOCATION_RADIUS_PX = 7f
private const val CURRENT_LOCATION_STROKE_WIDTH_PX = 2f

private const val SOURCE_SQUADRATS = "squadventure-squadrats"
private const val SOURCE_SQUADRATINHOS = "squadventure-squadratinhos"
private const val SOURCE_HEATMAP = "squadventure-heatmap"
private const val SOURCE_ROUTE = "squadventure-route"
private const val SOURCE_CURRENT_LOCATION = "squadventure-current-location"

private const val LAYER_SQUADRATS = "squadventure-squadrats-fill"
private const val LAYER_SQUADRATINHOS = "squadventure-squadratinhos-fill"
private const val LAYER_HEATMAP = "squadventure-heatmap"
private const val LAYER_ROUTE = "squadventure-route"
private const val LAYER_CURRENT_LOCATION = "squadventure-current-location"

private fun initialCameraPosition(focus: TrackPoint?): CameraPosition =
    if (focus != null) {
        CameraPosition.Builder().target(LatLng(focus.lat, focus.lon)).zoom(FOCUS_ZOOM).build()
    } else {
        CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(WORLD_ZOOM).build()
    }

/** Idempotent: adds each source/layer once (checked by source id), above whatever the basemap style already has. */
private fun ensureOverlayLayers(style: Style) {
    if (style.getSource(SOURCE_SQUADRATS) == null) {
        style.addSource(GeoJsonSource(SOURCE_SQUADRATS))
        style.addLayer(
            FillLayer(LAYER_SQUADRATS, SOURCE_SQUADRATS).withProperties(
                PropertyFactory.fillColor(Squadrat.copy(alpha = 0.12f).toArgb()),
                PropertyFactory.fillOutlineColor(Squadrat.copy(alpha = 0.8f).toArgb()),
            ),
        )
    }
    if (style.getSource(SOURCE_SQUADRATINHOS) == null) {
        style.addSource(GeoJsonSource(SOURCE_SQUADRATINHOS))
        style.addLayer(
            FillLayer(LAYER_SQUADRATINHOS, SOURCE_SQUADRATINHOS).withProperties(
                PropertyFactory.fillColor(Squadratinho.copy(alpha = 0.35f).toArgb()),
            ),
        )
    }
    if (style.getSource(SOURCE_HEATMAP) == null) {
        style.addSource(GeoJsonSource(SOURCE_HEATMAP))
        style.addLayer(
            HeatmapLayer(LAYER_HEATMAP, SOURCE_HEATMAP).withProperties(
                PropertyFactory.heatmapRadius(HEATMAP_RADIUS_PX),
                PropertyFactory.heatmapIntensity(HEATMAP_INTENSITY),
                PropertyFactory.heatmapOpacity(HEATMAP_OPACITY),
                PropertyFactory.heatmapColor(heatmapColorRamp()),
            ),
        )
    }
    if (style.getSource(SOURCE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE))
        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(Trail.toArgb()),
                PropertyFactory.lineWidth(ROUTE_LINE_WIDTH_PX),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }
    if (style.getSource(SOURCE_CURRENT_LOCATION) == null) {
        style.addSource(GeoJsonSource(SOURCE_CURRENT_LOCATION))
        style.addLayer(
            CircleLayer(LAYER_CURRENT_LOCATION, SOURCE_CURRENT_LOCATION).withProperties(
                PropertyFactory.circleColor(Trail.toArgb()),
                PropertyFactory.circleRadius(CURRENT_LOCATION_RADIUS_PX),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeWidth(CURRENT_LOCATION_STROKE_WIDTH_PX),
            ),
        )
    }
}

/** transparent -> Trail (cool, low density) -> Squadratinho (warm, high density). */
private fun heatmapColorRamp(): Expression {
    val trail = channelBytes(Trail)
    val hot = channelBytes(Squadratinho)
    return Expression.interpolate(
        Expression.linear(),
        Expression.heatmapDensity(),
        Expression.stop(0f, Expression.rgba(trail.first, trail.second, trail.third, 0f)),
        Expression.stop(0.5f, Expression.rgba(trail.first, trail.second, trail.third, 0.7f)),
        Expression.stop(1f, Expression.rgba(hot.first, hot.second, hot.third, 1f)),
    )
}

private fun channelBytes(color: Color): Triple<Float, Float, Float> =
    Triple(color.red * 255f, color.green * 255f, color.blue * 255f)

private fun updateSquareLayers(style: Style, claims: MapClaims, showSquares: Boolean) {
    val visibility = PropertyFactory.visibility(if (showSquares) Property.VISIBLE else Property.NONE)
    style.getLayerAs<FillLayer>(LAYER_SQUADRATS)?.setProperties(visibility)
    style.getLayerAs<FillLayer>(LAYER_SQUADRATINHOS)?.setProperties(visibility)
    style.getSourceAs<GeoJsonSource>(SOURCE_SQUADRATS)
        ?.setGeoJson(tileFeatureCollection(claims.squadrats, SlippyTile.ZOOM_SQUADRAT))
    style.getSourceAs<GeoJsonSource>(SOURCE_SQUADRATINHOS)
        ?.setGeoJson(tileFeatureCollection(claims.squadratinhos, SlippyTile.ZOOM_SQUADRATINHO))
}

private fun updateTrailLayers(style: Style, routes: List<List<TrackPoint>>, showHeatmap: Boolean) {
    style.getLayerAs<HeatmapLayer>(LAYER_HEATMAP)
        ?.setProperties(PropertyFactory.visibility(if (showHeatmap) Property.VISIBLE else Property.NONE))
    style.getLayerAs<LineLayer>(LAYER_ROUTE)
        ?.setProperties(PropertyFactory.visibility(if (showHeatmap) Property.NONE else Property.VISIBLE))
    if (showHeatmap) {
        style.getSourceAs<GeoJsonSource>(SOURCE_HEATMAP)?.setGeoJson(heatmapFeatureCollection(routes))
    } else {
        style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)?.setGeoJson(routeFeatureCollection(routes))
    }
}

private fun updateCurrentLocationLayer(style: Style, currentLocation: TrackPoint?) {
    style.getLayerAs<CircleLayer>(LAYER_CURRENT_LOCATION)
        ?.setProperties(PropertyFactory.visibility(if (currentLocation != null) Property.VISIBLE else Property.NONE))
    style.getSourceAs<GeoJsonSource>(SOURCE_CURRENT_LOCATION)?.setGeoJson(currentLocationFeatureCollection(currentLocation))
}

private fun tileFeatureCollection(keys: Set<Long>, zoom: Int): FeatureCollection {
    val features = keys.map { key -> Feature.fromGeometry(tilePolygon(SlippyTile.keyX(key), SlippyTile.keyY(key), zoom)) }
    return FeatureCollection.fromFeatures(features)
}

private fun tilePolygon(x: Int, y: Int, zoom: Int): Polygon {
    val nw = SlippyTile.tileNorthWest(x, y, zoom)
    val se = SlippyTile.tileNorthWest(x + 1, y + 1, zoom)
    val ring = listOf(
        Point.fromLngLat(nw[1], nw[0]),
        Point.fromLngLat(se[1], nw[0]),
        Point.fromLngLat(se[1], se[0]),
        Point.fromLngLat(nw[1], se[0]),
        Point.fromLngLat(nw[1], nw[0]),
    )
    return Polygon.fromLngLats(listOf(ring))
}

private fun routeFeatureCollection(routes: List<List<TrackPoint>>): FeatureCollection {
    val features = routes.filter { it.size >= 2 }
        .map { points -> Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })) }
    return FeatureCollection.fromFeatures(features)
}

private fun heatmapFeatureCollection(routes: List<List<TrackPoint>>): FeatureCollection {
    val features = routes.asSequence().flatten().map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }.toList()
    return FeatureCollection.fromFeatures(features)
}

private fun currentLocationFeatureCollection(point: TrackPoint?): FeatureCollection =
    if (point == null) {
        FeatureCollection.fromFeatures(emptyList())
    } else {
        FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat)))
    }
