package dev.mtib.squadventure.core.geo

import dev.mtib.squadventure.core.model.TrackPoint
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
}
