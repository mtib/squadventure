package dev.mtib.squadventure.phone.map

import androidx.compose.ui.geometry.Offset
import dev.mtib.squadventure.core.geo.SlippyTile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val HEATMAP_BLUR_RADIUS_CELLS = 2
internal const val HEATMAP_BLUR_PASSES = 3
internal const val HEATMAP_MAX_GRID_DIM = 320
internal const val HEATMAP_MIN_ZOOM = 0
internal const val HEATMAP_MAX_ZOOM = 22
internal const val HEATMAP_EPSILON = 0.02f

/** A rasterized heatmap grid over slippy tiles at [hz]: [counts] is row-major, `width * height`. */
class HeatmapGrid(
    val hz: Int,
    val minTileX: Int,
    val minTileY: Int,
    val width: Int,
    val height: Int,
    val counts: IntArray,
)

/** One rendered heatmap cell: its hz-tile indices and blurred, normalized intensity in `[0,1]`. */
data class HeatmapCell(val tileX: Int, val tileY: Int, val intensity: Float)

/**
 * Picks the raster tile zoom so a cell spans roughly [cellPx] screen pixels at [zoomPx] (screen px
 * per world unit), then backs off until the grid covering [visible] (padded by [pad] cells for the
 * blur kernel) fits within [HEATMAP_MAX_GRID_DIM]² cells.
 */
fun chooseHeatmapZoom(
    zoomPx: Float,
    cellPx: Float,
    visible: WorldRect,
    pad: Int = HEATMAP_BLUR_RADIUS_CELLS,
): Int {
    var hz = log2(zoomPx / cellPx).roundToInt().coerceIn(HEATMAP_MIN_ZOOM, HEATMAP_MAX_ZOOM)
    while (hz > HEATMAP_MIN_ZOOM && !gridFits(visible, hz, pad)) hz--
    return hz
}

private fun gridFits(visible: WorldRect, hz: Int, pad: Int): Boolean {
    val (width, height) = gridDims(visible, hz, pad)
    return width <= HEATMAP_MAX_GRID_DIM && height <= HEATMAP_MAX_GRID_DIM
}

private fun gridDims(visible: WorldRect, hz: Int, pad: Int): Pair<Int, Int> {
    val n = SlippyTile.tilesPerAxis(hz)
    val minTileX = floor(visible.minX * n).toInt() - pad
    val maxTileX = ceil(visible.maxX * n).toInt() + pad
    val minTileY = floor(visible.minY * n).toInt() - pad
    val maxTileY = ceil(visible.maxY * n).toInt() + pad
    return (maxTileX - minTileX + 1) to (maxTileY - minTileY + 1)
}

/**
 * Stamps each activity's polyline into a tile-density grid: for every route, every hz-tile its path
 * passes through is counted once (deduped per activity), so a cell's count is "how many activities
 * passed through here" rather than point density. Returns `null` if the grid would be degenerate or
 * exceed [HEATMAP_MAX_GRID_DIM] (callers should have already backed [hz] off via
 * [chooseHeatmapZoom]).
 */
fun buildHeatmapGrid(
    routes: List<List<Offset>>,
    visible: WorldRect,
    hz: Int,
    pad: Int = HEATMAP_BLUR_RADIUS_CELLS,
): HeatmapGrid? {
    val n = SlippyTile.tilesPerAxis(hz)
    val minTileX = floor(visible.minX * n).toInt() - pad
    val maxTileX = ceil(visible.maxX * n).toInt() + pad
    val minTileY = floor(visible.minY * n).toInt() - pad
    val maxTileY = ceil(visible.maxY * n).toInt() + pad
    val width = maxTileX - minTileX + 1
    val height = maxTileY - minTileY + 1
    if (width <= 0 || height <= 0) return null
    if (width > HEATMAP_MAX_GRID_DIM + 2 * pad || height > HEATMAP_MAX_GRID_DIM + 2 * pad) return null

    val counts = IntArray(width * height)
    val visitedInRoute = HashSet<Long>()
    for (route in routes) {
        visitedInRoute.clear()
        for (i in 0 until route.size - 1) {
            walkTileLine(route[i], route[i + 1], n) { tx, ty ->
                if (tx in minTileX..maxTileX && ty in minTileY..maxTileY) {
                    if (visitedInRoute.add(SlippyTile.key(tx, ty))) {
                        counts[(ty - minTileY) * width + (tx - minTileX)]++
                    }
                }
            }
        }
    }
    return HeatmapGrid(hz, minTileX, minTileY, width, height, counts)
}

/** Dense sub-tile sampling walk from [a] to [b] (world space) over the tile grid at [n] tiles/axis. */
private inline fun walkTileLine(a: Offset, b: Offset, n: Int, visit: (Int, Int) -> Unit) {
    val x0 = a.x * n
    val y0 = a.y * n
    val x1 = b.x * n
    val y1 = b.y * n
    val dx = x1 - x0
    val dy = y1 - y0
    val steps = max(1, ceil(max(abs(dx), abs(dy)) * 2f).toInt())
    for (s in 0..steps) {
        val t = s.toFloat() / steps
        visit(floor(x0 + dx * t).toInt(), floor(y0 + dy * t).toInt())
    }
}

/** Separable box blur of [grid]'s counts, [passes] times, approximating a Gaussian. */
fun blurHeatmapGrid(grid: HeatmapGrid, radius: Int = HEATMAP_BLUR_RADIUS_CELLS, passes: Int = HEATMAP_BLUR_PASSES): FloatArray {
    var current = FloatArray(grid.counts.size) { grid.counts[it].toFloat() }
    repeat(passes) { current = boxBlurPass(current, grid.width, grid.height, radius) }
    return current
}

private fun boxBlurPass(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
    val windowSize = 2 * radius + 1
    val horizontal = FloatArray(src.size)
    for (y in 0 until height) {
        val row = y * width
        var sum = 0f
        for (x in -radius..radius) sum += src[row + x.coerceIn(0, width - 1)]
        for (x in 0 until width) {
            horizontal[row + x] = sum / windowSize
            sum += src[row + (x + radius + 1).coerceAtMost(width - 1)] - src[row + (x - radius).coerceAtLeast(0)]
        }
    }
    val vertical = FloatArray(src.size)
    for (x in 0 until width) {
        var sum = 0f
        for (y in -radius..radius) sum += horizontal[y.coerceIn(0, height - 1) * width + x]
        for (y in 0 until height) {
            vertical[y * width + x] = sum / windowSize
            sum += horizontal[(y + radius + 1).coerceAtMost(height - 1) * width + x] -
                horizontal[(y - radius).coerceAtLeast(0) * width + x]
        }
    }
    return vertical
}

/** Normalizes [blurred] by its max and drops cells below [epsilon]; `sqrt` spreads the low end. */
fun heatmapCells(grid: HeatmapGrid, blurred: FloatArray, epsilon: Float = HEATMAP_EPSILON): List<HeatmapCell> {
    var maxVal = 0f
    for (v in blurred) if (v > maxVal) maxVal = v
    if (maxVal <= 0f) return emptyList()

    val cells = ArrayList<HeatmapCell>()
    for (y in 0 until grid.height) {
        for (x in 0 until grid.width) {
            val raw = (blurred[y * grid.width + x] / maxVal).coerceIn(0f, 1f)
            val intensity = sqrt(raw)
            if (intensity < epsilon) continue
            cells.add(HeatmapCell(grid.minTileX + x, grid.minTileY + y, intensity))
        }
    }
    return cells
}
