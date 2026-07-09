package dev.mtib.squadventure

import dev.mtib.squadventure.core.gpx.Gpx
import dev.mtib.squadventure.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {

    private val sample = """
        <?xml version="1.0"?>
        <gpx version="1.1">
          <trk><name>Morning</name><trkseg>
            <trkpt lat="52.5200" lon="13.4050"><ele>34</ele><time>2024-01-02T08:00:00Z</time></trkpt>
            <trkpt lat="52.5210" lon="13.4060"><ele>35</ele><time>2024-01-02T08:01:00Z</time></trkpt>
          </trkseg></trk>
          <trk><name>Evening</name><trkseg>
            <trkpt lat="48.8566" lon="2.3522"></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parses_multiple_tracks_as_separate_activities() {
        val tracks = Gpx.parse(sample.byteInputStream())
        assertEquals(2, tracks.size)
        assertEquals("Morning", tracks[0].name)
        assertEquals(2, tracks[0].points.size)
        assertEquals(52.5200, tracks[0].points[0].lat, 1e-9)
        assertEquals(34.0, tracks[0].points[0].elevationMeters!!, 1e-9)
        assertTrue(tracks[0].points[0].timeMs!! > 0)
        assertEquals(1, tracks[1].points.size)
        assertEquals(null, tracks[1].points[0].timeMs)
    }

    @Test
    fun write_then_parse_round_trips() {
        val pts = listOf(
            TrackPoint(52.5, 13.4, 10.0, 1_700_000_000_000L),
            TrackPoint(52.6, 13.5, 12.0, 1_700_000_060_000L),
        )
        val xml = Gpx.write(pts, "Trip")
        val back = Gpx.parse(xml.byteInputStream())
        assertEquals(1, back.size)
        assertEquals(2, back[0].points.size)
        assertEquals(52.6, back[0].points[1].lat, 1e-9)
        assertEquals(1_700_000_060_000L, back[0].points[1].timeMs)
    }

    @Test
    fun content_hash_is_stable_and_discriminating() {
        val a = listOf(TrackPoint(1.0, 2.0, null, 1000L))
        val aAgain = listOf(TrackPoint(1.0, 2.0, null, 1000L))
        val b = listOf(TrackPoint(1.0, 2.0001, null, 1000L))
        assertEquals(Gpx.contentHash(a), Gpx.contentHash(aAgain))
        assertNotEquals(Gpx.contentHash(a), Gpx.contentHash(b))
    }
}
