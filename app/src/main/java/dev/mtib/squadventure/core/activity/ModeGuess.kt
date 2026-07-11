package dev.mtib.squadventure.core.activity

import dev.mtib.squadventure.core.geo.Geo
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode

/**
 * Guesses the [TransportMode] of an imported track from its distance, duration and speed profile —
 * a starting point the user can re-tag. Classifies between [TransportMode.WALK], [TransportMode.BIKE]
 * and [TransportMode.CAR]; returns [TransportMode.OTHER] when there isn't enough timing to tell.
 *
 * [TransportMode.BOAT] is intentionally not guessed: telling water from land per point needs either
 * decoding the bundled vector basemap or a separate water mask, neither of which is cheap offline.
 *
 * The decision is driven by a cruising speed — the 85th percentile of per-segment speeds on the
 * smoothed track — rather than the raw average, because stops depress the average of an otherwise
 * faster mode. The overall average is used as a floor so a fast-but-choppy trace isn't underclassified.
 */
object ModeGuess {

    private const val MIN_DISTANCE_METERS = 50.0
    private const val WALK_MAX_KMH = 7.0
    private const val BIKE_MAX_KMH = 25.0
    private const val IMPLAUSIBLE_KMH = 200.0

    fun guess(distanceMeters: Double, durationMs: Long, points: List<TrackPoint>): TransportMode {
        if (durationMs <= 0L || distanceMeters < MIN_DISTANCE_METERS) return TransportMode.OTHER
        val avgKmh = distanceMeters / 1000.0 / (durationMs / 3_600_000.0)
        val kmh = maxOf(avgKmh, cruisingSpeedKmh(points))
        return when {
            kmh < WALK_MAX_KMH -> TransportMode.WALK
            kmh < BIKE_MAX_KMH -> TransportMode.BIKE
            else -> TransportMode.CAR
        }
    }

    private fun cruisingSpeedKmh(points: List<TrackPoint>): Double {
        val timed = Geo.smooth(points).filter { it.timeMs != null }
        if (timed.size < 3) return 0.0
        val speeds = ArrayList<Double>(timed.size)
        for (i in 1 until timed.size) {
            val dtHours = (timed[i].timeMs!! - timed[i - 1].timeMs!!) / 3_600_000.0
            if (dtHours <= 0.0) continue
            val km = Geo.haversineMeters(timed[i - 1].lat, timed[i - 1].lon, timed[i].lat, timed[i].lon) / 1000.0
            val kmh = km / dtHours
            if (kmh < IMPLAUSIBLE_KMH) speeds.add(kmh)
        }
        if (speeds.isEmpty()) return 0.0
        speeds.sort()
        return speeds[(speeds.size * 85 / 100).coerceIn(0, speeds.size - 1)]
    }
}
