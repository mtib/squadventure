package dev.mtib.squadventure.core.geo

import dev.mtib.squadventure.core.model.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance between two coordinates in meters (haversine). */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Summed great-circle length of a track in meters. */
    fun pathLengthMeters(points: List<TrackPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            total += haversineMeters(a.lat, a.lon, b.lat, b.lon)
        }
        return total
    }

    /** Planar distance between two normalized world points in [0,1]² (for pixel/hit-test math). */
    fun planarDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double =
        hypot(x2 - x1, y2 - y1)

    /**
     * Douglas–Peucker simplification, dropping points that deviate from the simplified line by
     * less than [epsilonMeters]. Denoises raw 1 Hz GPS jitter before summing path length — a
     * stationary receiver's fix noise otherwise inflates [pathLengthMeters] substantially. Points
     * are projected to local planar meters via an equirectangular approximation around the track's
     * mean latitude for the perpendicular-distance test; iterative (explicit stack) to handle
     * tracks with thousands of points without recursion depth issues. Endpoints are always kept.
     */
    fun simplify(points: List<TrackPoint>, epsilonMeters: Double): List<TrackPoint> {
        if (points.size <= 2) return points

        val lat0Rad = Math.toRadians(points.sumOf { it.lat } / points.size)
        val cosLat0 = cos(lat0Rad)
        val xs = DoubleArray(points.size) { EARTH_RADIUS_METERS * cosLat0 * Math.toRadians(points[it].lon) }
        val ys = DoubleArray(points.size) { EARTH_RADIUS_METERS * Math.toRadians(points[it].lat) }

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        val stack = ArrayDeque<IntRange>()
        stack.addLast(0..(points.size - 1))
        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val start = range.first
            val end = range.last
            if (end - start < 2) continue

            var maxDist = -1.0
            var maxIdx = -1
            for (i in (start + 1) until end) {
                val d = perpendicularDistanceMeters(
                    xs[i], ys[i],
                    xs[start], ys[start],
                    xs[end], ys[end],
                )
                if (d > maxDist) {
                    maxDist = d
                    maxIdx = i
                }
            }

            if (maxDist > epsilonMeters) {
                keep[maxIdx] = true
                stack.addLast(start..maxIdx)
                stack.addLast(maxIdx..end)
            }
        }

        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun perpendicularDistanceMeters(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return hypot(px - x1, py - y1)
        return abs((px - x1) * dy - (py - y1) * dx) / sqrt(lenSq)
    }
}
