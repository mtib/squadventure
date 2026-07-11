package dev.mtib.squadventure

import dev.mtib.squadventure.core.activity.ModeGuess
import dev.mtib.squadventure.core.geo.Geo
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeGuessTest {

    /** Straight eastbound track at a constant [speedKmh], one fix per second for [seconds]. */
    private fun straightTrack(speedKmh: Double, seconds: Int, timed: Boolean = true): List<TrackPoint> {
        val mps = speedKmh / 3.6
        val lat = 52.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
        return (0..seconds).map { i ->
            val lon = 13.0 + (mps * i) / mPerDegLon
            TrackPoint(lat, lon, timeMs = if (timed) 1_000_000L + i * 1000L else null)
        }
    }

    private fun guess(points: List<TrackPoint>): TransportMode {
        val t = points.mapNotNull { it.timeMs }
        val durationMs = if (t.size >= 2) t.max() - t.min() else 0L
        return ModeGuess.guess(Geo.denoisedDistanceMeters(points), durationMs, points)
    }

    @Test fun walking_pace_is_walk() = assertEquals(TransportMode.WALK, guess(straightTrack(5.0, 180)))

    @Test fun cycling_pace_is_bike() = assertEquals(TransportMode.BIKE, guess(straightTrack(18.0, 180)))

    @Test fun driving_pace_is_car() = assertEquals(TransportMode.CAR, guess(straightTrack(60.0, 180)))

    @Test fun no_timestamps_is_other() =
        assertEquals(TransportMode.OTHER, guess(straightTrack(18.0, 180, timed = false)))

    @Test fun too_short_to_tell_is_other() =
        assertEquals(TransportMode.OTHER, guess(straightTrack(5.0, 5)))

    @Test
    fun frequent_stops_do_not_downgrade_a_cyclist() {
        val moving = straightTrack(18.0, 60)
        val last = moving.last()
        val startMs = last.timeMs!!
        val parked = (1..300).map { last.copy(timeMs = startMs + it * 1000L) }
        assertEquals(TransportMode.BIKE, guess(moving + parked))
    }
}
