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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
 * Every layer is drawn directly in screen pixels (`[MapViewport.worldToScreen]`) after culling and
 * clipping to the visible viewport, rather than scaling world-space geometry by `zoomPx` (which can
 * reach hundreds of millions at max zoom — far past Skia's rasterizer limits). See
 * [dev.mtib.squadventure.phone.map.clipRingToRect] / [dev.mtib.squadventure.phone.map.clipSegmentToRect].
 *
 * @param claims claimed squares to fill.
 * @param routes GPX paths to draw. On the global map these feed the tile-density trail heatmap
 *   (see [HeatmapGrid]) when [showHeatmap] is on; on a detail screen this is the single activity's
 *   route, drawn as a solid line.
 * @param showHeatmap toggles the trail heatmap layer.
 * @param showSquares toggles the claimed-square overlay.
 * @param focus optional point to center/zoom on initially (e.g. an activity's start).
 * @param currentLocation optional live position to mark with a "you are here" dot, drawn on top of
 *   every other layer and never culled — only skipped if it falls outside the canvas.
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
    val seaColor = MaterialTheme.colorScheme.surface
    val projectedRoutes = remember(routes) { routes.mapNotNull(::projectRoute) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(seaColor)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport = viewport?.applyGesture(size, centroid, pan, zoom)
                }
            },
    ) {
        val vp = viewport ?: return@Canvas
        val visible = vp.visibleWorldRect(canvasSize)
        val clipRect = visible.expanded(CLIP_MARGIN_FRACTION)

        basemap?.let { drawBasemap(it, clipRect, vp, landColor, seaColor) }

        if (showSquares) {
            drawClaimedTiles(
                keys = claims.squadratinhos,
                zoom = SlippyTile.ZOOM_SQUADRATINHO,
                visible = visible,
                vp = vp,
                fillColor = Squadratinho.copy(alpha = 0.35f),
            )
            drawClaimedTiles(
                keys = claims.squadrats,
                zoom = SlippyTile.ZOOM_SQUADRAT,
                visible = visible,
                vp = vp,
                fillColor = Squadrat.copy(alpha = 0.12f),
                strokeColor = Squadrat.copy(alpha = 0.8f),
                strokeWidthPx = SQUARE_OUTLINE_DP.dp.toPx(),
            )
        }

        if (showHeatmap) {
            drawHeatmap(projectedRoutes, clipRect, vp)
        } else {
            drawRoutes(projectedRoutes, clipRect, vp)
        }

        currentLocation?.let { drawCurrentLocationMarker(it, canvasSize, vp) }
    }
}

private const val CLIP_MARGIN_FRACTION = 0.1f
private const val SQUARE_OUTLINE_DP = 1.5f
private const val ROUTE_LINE_WIDTH_DP = 3f
private const val HEATMAP_CELL_DP = 12f
private const val CURRENT_LOCATION_RADIUS_DP = 7f
private const val CURRENT_LOCATION_RING_DP = 2f

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

private fun DrawScope.drawBasemap(
    basemap: WorldBasemap,
    clipRect: WorldRect,
    vp: MapViewport,
    landColor: Color,
    seaColor: Color,
) {
    for (polygon in basemap.land) {
        if (!clipRect.intersects(polygon.bounds)) continue
        drawClippedPolygon(polygon, clipRect, vp, landColor)
    }
    for (lake in basemap.lakes) {
        if (!clipRect.intersects(lake.bounds)) continue
        drawClippedPolygon(lake, clipRect, vp, seaColor)
    }
}

private fun DrawScope.drawClippedPolygon(polygon: WorldPolygon, clipRect: WorldRect, vp: MapViewport, color: Color) {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    var hasRing = false
    for (ring in polygon.rings) {
        val clipped = clipRingToRect(ring, clipRect)
        if (clipped.size < 3) continue
        hasRing = true
        val first = vp.worldToScreen(clipped[0])
        path.moveTo(first.x, first.y)
        for (i in 1 until clipped.size) {
            val screen = vp.worldToScreen(clipped[i])
            path.lineTo(screen.x, screen.y)
        }
        path.close()
    }
    if (hasRing) drawPath(path, color = color)
}

private fun DrawScope.drawClaimedTiles(
    keys: Set<Long>,
    zoom: Int,
    visible: WorldRect,
    vp: MapViewport,
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
        val topLeft = vp.worldToScreen(Offset(x, y))
        val tileSize = Size(span * vp.zoomPx, span * vp.zoomPx)
        drawRect(color = fillColor, topLeft = topLeft, size = tileSize)
        if (strokeColor != null) {
            drawRect(color = strokeColor, topLeft = topLeft, size = tileSize, style = Stroke(width = strokeWidthPx))
        }
    }
}

private fun DrawScope.drawRoutes(routes: List<ProjectedRoute>, clipRect: WorldRect, vp: MapViewport) {
    val strokeWidth = ROUTE_LINE_WIDTH_DP.dp.toPx()
    for (route in routes) {
        if (!clipRect.intersects(route.bounds)) continue
        drawClippedPolyline(route.points, clipRect, vp, Trail, alpha = 1f, strokeWidth = strokeWidth)
    }
}

private fun DrawScope.drawClippedPolyline(
    points: List<Offset>,
    clipRect: WorldRect,
    vp: MapViewport,
    color: Color,
    alpha: Float,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    for (i in 0 until points.size - 1) {
        val clipped = clipSegmentToRect(points[i], points[i + 1], clipRect) ?: continue
        drawLine(
            color = color,
            start = vp.worldToScreen(clipped.first),
            end = vp.worldToScreen(clipped.second),
            strokeWidth = strokeWidth,
            alpha = alpha,
        )
    }
}

/**
 * Draws the trail heatmap as a rasterized tile-density grid (see [HeatmapGrid]) rather than
 * additive translucent lines: cells where more activities' paths overlap get a hotter color, and
 * the cell size tracks screen pixels so it looks consistent across zoom levels.
 */
private fun DrawScope.drawHeatmap(routes: List<ProjectedRoute>, clipRect: WorldRect, vp: MapViewport) {
    val visibleRoutes = routes.filter { clipRect.intersects(it.bounds) }
    if (visibleRoutes.isEmpty()) return

    val cellPx = HEATMAP_CELL_DP.dp.toPx()
    val hz = chooseHeatmapZoom(vp.zoomPx, cellPx, clipRect)
    val grid = buildHeatmapGrid(visibleRoutes.map { it.points }, clipRect, hz) ?: return
    val blurred = blurHeatmapGrid(grid)
    val cells = heatmapCells(grid, blurred)
    if (cells.isEmpty()) return

    val sizePx = WebMercator.tileSpan(hz).toFloat() * vp.zoomPx
    val cellSize = Size(sizePx, sizePx)
    for (cell in cells) {
        val worldX = WebMercator.tileEdge(cell.tileX, hz).toFloat()
        val worldY = WebMercator.tileEdge(cell.tileY, hz).toFloat()
        drawRect(color = heatColor(cell.intensity), topLeft = vp.worldToScreen(Offset(worldX, worldY)), size = cellSize)
    }
}

/** transparent -> Trail (cool, low traffic) -> Squadratinho (warm, high traffic). */
private fun heatColor(intensity: Float): Color {
    val alpha = (0.15f + 0.65f * intensity).coerceIn(0f, 1f)
    return lerp(Trail, Squadratinho, intensity).copy(alpha = alpha)
}

private fun DrawScope.drawCurrentLocationMarker(point: TrackPoint, canvasSize: IntSize, vp: MapViewport) {
    val xy = WebMercator.project(point.lat, point.lon)
    val screen = vp.worldToScreen(Offset(xy[0].toFloat(), xy[1].toFloat()))
    if (screen.x < 0f || screen.x > canvasSize.width || screen.y < 0f || screen.y > canvasSize.height) return

    val radius = CURRENT_LOCATION_RADIUS_DP.dp.toPx()
    val ringWidth = CURRENT_LOCATION_RING_DP.dp.toPx()
    drawCircle(color = Color.White, radius = radius + ringWidth, center = screen)
    drawCircle(color = Trail, radius = radius, center = screen)
}
