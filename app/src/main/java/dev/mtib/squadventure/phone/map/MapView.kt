package dev.mtib.squadventure.phone.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.mtib.squadventure.core.geo.SlippyTile
import dev.mtib.squadventure.core.geo.WebMercator
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.phone.ui.Squadrat
import dev.mtib.squadventure.phone.ui.Squadratinho
import dev.mtib.squadventure.phone.ui.Trail

/** Claimed squares to overlay: [squadratinhos] (z17) and [squadrats] (z14), as packed tile keys. */
data class MapClaims(
    val squadrats: Set<Long> = emptySet(),
    val squadratinhos: Set<Long> = emptySet(),
)

/**
 * The app's map surface — a Compose Canvas that projects everything through
 * [dev.mtib.squadventure.core.geo.WebMercator] onto a bundled low-poly world basemap. Public
 * contract shared by the screens; the rendering (basemap polygons, square overlay, trail heatmap,
 * route) lives in this package.
 *
 * @param claims claimed squares to fill.
 * @param routes GPX paths to draw. On the global map these feed the Strava-style trail heatmap
 *   (line-density, brighter where routes overlap) when [showHeatmap] is on; on a detail screen this
 *   is the single activity's route.
 * @param showHeatmap toggles the trail heatmap layer.
 * @param showSquares toggles the claimed-square overlay.
 * @param focus optional point to center/zoom on initially (e.g. an activity's start).
 */
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    claims: MapClaims = MapClaims(),
    routes: List<List<TrackPoint>> = emptyList(),
    showHeatmap: Boolean = false,
    showSquares: Boolean = true,
    focus: TrackPoint? = null,
) {
    val context = LocalContext.current
    var basemap by remember { mutableStateOf<WorldBasemap?>(null) }
    LaunchedEffect(Unit) { basemap = WorldBasemap.load(context) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    LaunchedEffect(canvasSize) {
        if (viewport == null && canvasSize.width > 0 && canvasSize.height > 0) {
            viewport = if (focus != null) {
                val xy = WebMercator.project(focus.lat, focus.lon)
                MapViewport.centeredOn(canvasSize, Offset(xy[0].toFloat(), xy[1].toFloat()))
            } else {
                MapViewport.fitWorld(canvasSize)
            }
        }
    }

    val landColor = MaterialTheme.colorScheme.surfaceVariant
    val projectedRoutes = remember(routes) { routes.mapNotNull(::projectRoute) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport = viewport?.applyGesture(size, centroid, pan, zoom)
                }
            },
    ) {
        val vp = viewport ?: return@Canvas
        val visible = vp.visibleWorldRect(canvasSize)

        scale(scaleX = vp.zoomPx, scaleY = vp.zoomPx, pivot = Offset.Zero) {
            translate(left = -vp.origin.x, top = -vp.origin.y) {
                basemap?.let { drawPath(it.landPath, color = landColor) }

                if (showSquares) {
                    drawClaimedTiles(
                        keys = claims.squadratinhos,
                        zoom = SlippyTile.ZOOM_SQUADRATINHO,
                        visible = visible,
                        fillColor = Squadratinho.copy(alpha = 0.35f),
                    )
                    drawClaimedTiles(
                        keys = claims.squadrats,
                        zoom = SlippyTile.ZOOM_SQUADRAT,
                        visible = visible,
                        fillColor = Squadrat.copy(alpha = 0.12f),
                        strokeColor = Squadrat.copy(alpha = 0.8f),
                        strokeWidthPx = SQUARE_OUTLINE_DP.dp.toPx() / vp.zoomPx,
                    )
                }

                if (showHeatmap) {
                    drawHeatmap(projectedRoutes, visible, vp.zoomPx)
                } else {
                    drawRoutes(projectedRoutes, visible, vp.zoomPx)
                }
            }
        }
    }
}

private const val SQUARE_OUTLINE_DP = 1.5f
private const val HEATMAP_LINE_ALPHA = 0.12f
private const val HEATMAP_LINE_WIDTH_DP = 5f
private const val ROUTE_LINE_WIDTH_DP = 3f

private data class ProjectedRoute(val points: List<Offset>, val bounds: WorldRect)

private fun projectRoute(points: List<TrackPoint>): ProjectedRoute? {
    if (points.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    val offsets = ArrayList<Offset>(points.size)
    for (p in points) {
        val xy = WebMercator.project(p.lat, p.lon)
        val x = xy[0].toFloat()
        val y = xy[1].toFloat()
        offsets.add(Offset(x, y))
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    return ProjectedRoute(offsets, WorldRect(minX, minY, maxX, maxY))
}

private fun DrawScope.drawClaimedTiles(
    keys: Set<Long>,
    zoom: Int,
    visible: WorldRect,
    fillColor: Color,
    strokeColor: Color? = null,
    strokeWidthPx: Float = 0f,
) {
    if (keys.isEmpty()) return
    val span = WebMercator.tileSpan(zoom).toFloat()
    for (key in keys) {
        val x = WebMercator.tileEdge(SlippyTile.keyX(key), zoom).toFloat()
        val y = WebMercator.tileEdge(SlippyTile.keyY(key), zoom).toFloat()
        if (!visible.intersects(x, y, span)) continue
        val topLeft = Offset(x, y)
        val tileSize = Size(span, span)
        drawRect(color = fillColor, topLeft = topLeft, size = tileSize)
        if (strokeColor != null) {
            drawRect(color = strokeColor, topLeft = topLeft, size = tileSize, style = Stroke(width = strokeWidthPx))
        }
    }
}

private fun DrawScope.drawHeatmap(routes: List<ProjectedRoute>, visible: WorldRect, zoomPx: Float) {
    val strokeWidth = HEATMAP_LINE_WIDTH_DP.dp.toPx() / zoomPx
    for (route in routes) {
        if (!visible.intersects(route.bounds)) continue
        drawPolyline(
            points = route.points,
            color = Trail,
            alpha = HEATMAP_LINE_ALPHA,
            strokeWidth = strokeWidth,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawRoutes(routes: List<ProjectedRoute>, visible: WorldRect, zoomPx: Float) {
    val strokeWidth = ROUTE_LINE_WIDTH_DP.dp.toPx() / zoomPx
    for (route in routes) {
        if (!visible.intersects(route.bounds)) continue
        drawPolyline(points = route.points, color = Trail, alpha = 1f, strokeWidth = strokeWidth)
    }
}

private fun DrawScope.drawPolyline(
    points: List<Offset>,
    color: Color,
    alpha: Float,
    strokeWidth: Float,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (points.size < 2) return
    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = points[i],
            end = points[i + 1],
            strokeWidth = strokeWidth,
            alpha = alpha,
            blendMode = blendMode,
        )
    }
}
