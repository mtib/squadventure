package dev.mtib.squadventure.phone.map

import android.graphics.Bitmap
import dev.mtib.squadventure.core.geo.WebMercator
import dev.mtib.squadventure.core.model.TrackPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rasterized trail-density image geo-anchored via [quad] for a MapLibre `ImageSource`. */
data class HeatmapRaster(val bitmap: Bitmap, val quad: LatLngQuad)

/**
 * Strava-style trail heatmap, rasterized offline into an RGBA [Bitmap] instead of relying on
 * MapLibre's `HeatmapLayer` (no tile server to callback into on a fully offline app). Cells are
 * counted once per activity via a per-activity visited set, so laps don't inflate density; a fixed
 * normalization max keeps brightness stable across pans/zooms instead of rescaling per frame.
 */
object HeatmapRenderer {
    private const val MAX_BITMAP_DIMENSION = 768
    private const val MARGIN_PX = 48
    private const val STAMP_RADIUS_PX = 2
    private const val BLUR_PASSES = 3
    private const val HEATMAP_MAX_COUNT = 6f

    /** Cool ramp aligned to the app palette (Trail blue → Squadrat green → near-white). */
    private val HEAT_STOPS = listOf(
        0.0f to Triple(12, 60, 90),
        0.3f to Triple(124, 199, 255),
        0.65f to Triple(91, 217, 138),
        1.0f to Triple(233, 255, 240),
    )

    /**
     * Renders [routes] visible within [bounds] (already expanded by the caller's desired margin at
     * the map-camera level; this adds its own inner [MARGIN_PX] so stamps/blur don't clip). Returns
     * null for a degenerate (zero-area) extent.
     */
    fun render(bounds: LatLngBounds, routes: List<List<TrackPoint>>): HeatmapRaster? {
        val west = WebMercator.lonToX(bounds.longitudeWest)
        var east = WebMercator.lonToX(bounds.longitudeEast)
        if (east <= west) east += 1.0
        val north = WebMercator.latToY(bounds.latitudeNorth)
        val south = WebMercator.latToY(bounds.latitudeSouth)
        val width = east - west
        val height = south - north
        if (width <= 0.0 || height <= 0.0) return null

        val aspect = width / height
        val baseW = if (aspect >= 1.0) {
            MAX_BITMAP_DIMENSION
        } else {
            (MAX_BITMAP_DIMENSION * aspect).roundToInt().coerceAtLeast(1)
        }
        val baseH = if (aspect >= 1.0) {
            (MAX_BITMAP_DIMENSION / aspect).roundToInt().coerceAtLeast(1)
        } else {
            MAX_BITMAP_DIMENSION
        }

        val scaleX = width / baseW
        val scaleY = height / baseH
        val marginX = MARGIN_PX * scaleX
        val marginY = MARGIN_PX * scaleY

        val mxMin = west - marginX
        val mxMax = east + marginX
        val myMin = north - marginY
        val myMax = south + marginY

        val w = baseW + 2 * MARGIN_PX
        val h = baseH + 2 * MARGIN_PX
        if (w <= 0 || h <= 0) return null

        val count = IntArray(w * h)
        for (activity in routes) {
            rasterizeActivity(activity, count, w, h, mxMin, mxMax, myMin, myMax)
        }

        var field = FloatArray(w * h) { i -> if (count[i] > 0) ln(count[i] + 1f) else 0f }
        repeat(BLUR_PASSES) { field = boxBlur3x3(field, w, h) }

        val logMax = ln(HEATMAP_MAX_COUNT + 1f)
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val t = sqrt((field[i] / logMax).coerceIn(0f, 1f))
            pixels[i] = heatArgb(t)
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)

        val topLeft = LatLng(WebMercator.yToLat(myMin), WebMercator.xToLon(mxMin))
        val topRight = LatLng(WebMercator.yToLat(myMin), WebMercator.xToLon(mxMax))
        val bottomRight = LatLng(WebMercator.yToLat(myMax), WebMercator.xToLon(mxMax))
        val bottomLeft = LatLng(WebMercator.yToLat(myMax), WebMercator.xToLon(mxMin))
        return HeatmapRaster(bitmap, LatLngQuad(topLeft, topRight, bottomRight, bottomLeft))
    }

    private fun rasterizeActivity(
        activity: List<TrackPoint>,
        count: IntArray,
        w: Int,
        h: Int,
        mxMin: Double,
        mxMax: Double,
        myMin: Double,
        myMax: Double,
    ) {
        if (activity.isEmpty()) return
        val visited = HashSet<Int>()
        var prevPx = Int.MIN_VALUE
        var prevPy = Int.MIN_VALUE
        for (point in activity) {
            val projected = WebMercator.project(point.lat, point.lon)
            val px = ((projected[0] - mxMin) / (mxMax - mxMin) * w).roundToInt()
            val py = ((projected[1] - myMin) / (myMax - myMin) * h).roundToInt()
            if (prevPx == Int.MIN_VALUE) {
                stampDisk(count, visited, w, h, px, py, STAMP_RADIUS_PX)
            } else {
                walkAndStamp(count, visited, w, h, prevPx, prevPy, px, py, STAMP_RADIUS_PX)
            }
            prevPx = px
            prevPy = py
        }
    }

    private fun stampDisk(count: IntArray, visited: HashSet<Int>, w: Int, h: Int, cx: Int, cy: Int, radius: Int) {
        val r2 = radius * radius
        for (dy in -radius..radius) {
            val y = cy + dy
            if (y < 0 || y >= h) continue
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > r2) continue
                val x = cx + dx
                if (x < 0 || x >= w) continue
                val idx = y * w + x
                if (visited.add(idx)) count[idx]++
            }
        }
    }

    /** Bresenham line walk between two pixels, stamping a disk at every step. */
    private fun walkAndStamp(
        count: IntArray,
        visited: HashSet<Int>,
        w: Int,
        h: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        radius: Int,
    ) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x1 >= x0) 1 else -1
        val sy = if (y1 >= y0) 1 else -1
        var err = dx - dy
        stampDisk(count, visited, w, h, x, y, radius)
        while (x != x1 || y != y1) {
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
            stampDisk(count, visited, w, h, x, y, radius)
        }
    }

    private fun boxBlur3x3(field: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (ny in max(0, y - 1)..min(h - 1, y + 1)) {
                    for (nx in max(0, x - 1)..min(w - 1, x + 1)) {
                        sum += field[ny * w + nx]
                    }
                }
                out[y * w + x] = sum / 9f
            }
        }
        return out
    }

    private fun heatArgb(t: Float): Int {
        var lo = HEAT_STOPS.first()
        var hi = HEAT_STOPS.last()
        for (i in 0 until HEAT_STOPS.size - 1) {
            if (t >= HEAT_STOPS[i].first && t <= HEAT_STOPS[i + 1].first) {
                lo = HEAT_STOPS[i]
                hi = HEAT_STOPS[i + 1]
                break
            }
        }
        val span = hi.first - lo.first
        val f = if (span > 0f) (t - lo.first) / span else 0f
        val r = lerpChannel(lo.second.first, hi.second.first, f)
        val g = lerpChannel(lo.second.second, hi.second.second, f)
        val b = lerpChannel(lo.second.third, hi.second.third, f)
        val a = (t * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(a: Int, b: Int, f: Float): Int = (a + (b - a) * f).roundToInt().coerceIn(0, 255)
}
