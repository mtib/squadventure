package dev.mtib.squadventure

import dev.mtib.squadventure.core.geo.Geo
import dev.mtib.squadventure.core.model.TrackPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private val earthRadiusMeters = 6_371_008.8

    /** Straight line at [baseLat] with [jitterMeters] alternating perpendicular jitter per point. */
    private fun jitteryStraightLine(
        baseLat: Double,
        lonSpanDegrees: Double,
        steps: Int,
        jitterMeters: Double,
    ): List<TrackPoint> {
        val baseLatRad = Math.toRadians(baseLat)
        val cosLat = cos(baseLatRad)
        return (0..steps).map { i ->
            val lon = i * lonSpanDegrees / steps
            val jitter = if (i == 0 || i == steps) 0.0 else if (i % 2 == 0) jitterMeters else -jitterMeters
            val lat = baseLat + Math.toDegrees(jitter / earthRadiusMeters)
            TrackPoint(lat, lon)
        }
    }

    /** Quarter-circle arc of [radiusMeters] sampled every [stepDegrees], via a local planar projection. */
    private fun arc(baseLat: Double, baseLon: Double, radiusMeters: Double, stepDegrees: Double): List<TrackPoint> {
        val lat0Rad = Math.toRadians(baseLat)
        val cosLat0 = cos(lat0Rad)
        val points = ArrayList<TrackPoint>()
        var angle = 0.0
        while (angle <= 90.0 + 1e-9) {
            val angleRad = Math.toRadians(angle)
            val x = radiusMeters * sin(angleRad)
            val y = radiusMeters * (1 - cos(angleRad))
            val lat = baseLat + Math.toDegrees(y / earthRadiusMeters)
            val lon = baseLon + Math.toDegrees(x / (earthRadiusMeters * cosLat0))
            points.add(TrackPoint(lat, lon))
            angle += stepDegrees
        }
        return points
    }

    @Test
    fun simplify_collapses_jitter_on_a_straight_line() {
        val points = jitteryStraightLine(baseLat = 52.0, lonSpanDegrees = 0.01, steps = 200, jitterMeters = 2.0)
        val straightMeters = Geo.haversineMeters(
            points.first().lat, points.first().lon,
            points.last().lat, points.last().lon,
        )
        val rawMeters = Geo.pathLengthMeters(points)
        assertTrue("raw jittery path should be noticeably inflated", rawMeters > straightMeters * 1.2)

        val simplified = Geo.simplify(points, 5.0)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue("should collapse to near-endpoints only", simplified.size <= 4)

        val simplifiedMeters = Geo.pathLengthMeters(simplified)
        assertTrue(
            "simplified length ($simplifiedMeters) should be close to the straight distance ($straightMeters)",
            abs(simplifiedMeters - straightMeters) < straightMeters * 0.05,
        )
    }

    @Test
    fun simplify_preserves_length_of_a_genuinely_curved_path() {
        val points = arc(baseLat = 50.0, baseLon = 10.0, radiusMeters = 500.0, stepDegrees = 0.5)
        val rawMeters = Geo.pathLengthMeters(points)

        val simplified = Geo.simplify(points, 5.0)
        assertTrue("curve should keep more than just its endpoints", simplified.size > 4)
        assertTrue("curve should not keep every raw point", simplified.size < points.size)

        val simplifiedMeters = Geo.pathLengthMeters(simplified)
        assertTrue(
            "simplified curve length ($simplifiedMeters) should stay within a few % of raw ($rawMeters)",
            abs(simplifiedMeters - rawMeters) < rawMeters * 0.05,
        )
    }

    @Test
    fun simplify_is_a_noop_for_two_or_fewer_points() {
        assertEquals(emptyList<TrackPoint>(), Geo.simplify(emptyList(), 5.0))
        val single = listOf(TrackPoint(1.0, 2.0))
        assertEquals(single, Geo.simplify(single, 5.0))
        val two = listOf(TrackPoint(1.0, 2.0), TrackPoint(1.1, 2.1))
        assertEquals(two, Geo.simplify(two, 5.0))
    }
}
