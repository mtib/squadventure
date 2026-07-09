package dev.mtib.squadventure

import dev.mtib.squadventure.core.geo.SlippyTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlippyTileTest {

    @Test
    fun squadratinho_is_contained_by_the_squadrat_of_the_same_point() {
        val samples = listOf(
            52.5200 to 13.4050,   // Berlin
            0.0 to 0.0,           // null island
            -33.8688 to 151.2093, // Sydney
            64.1466 to -21.9426,  // Reykjavik
        )
        for ((lat, lon) in samples) {
            val small = SlippyTile.squadratinhoOf(lat, lon)
            val big = SlippyTile.squadratOf(lat, lon)
            assertEquals("squadrat of $lat,$lon", big, SlippyTile.squadratForSquadratinho(small))
        }
    }

    @Test
    fun one_squadrat_holds_64_squadratinhos() {
        val shift = SlippyTile.ZOOM_SQUADRATINHO - SlippyTile.ZOOM_SQUADRAT
        assertEquals(3, shift)
        assertEquals(64, (1 shl shift) * (1 shl shift))
    }

    @Test
    fun forward_and_reverse_projection_are_consistent() {
        val zoom = SlippyTile.ZOOM_SQUADRAT
        val lat = 48.8566
        val lon = 2.3522 // Paris
        val key = SlippyTile.tileOf(lat, lon, zoom)
        val x = SlippyTile.keyX(key)
        val y = SlippyTile.keyY(key)
        val nw = SlippyTile.tileNorthWest(x, y, zoom)
        val se = SlippyTile.tileNorthWest(x + 1, y + 1, zoom)
        // The point must fall inside the tile bounds it was assigned to.
        assertTrue("lon within tile", lon >= nw[1] && lon < se[1])
        assertTrue("lat within tile", lat <= nw[0] && lat > se[0])
    }

    @Test
    fun tile_edge_sizes_match_known_figures() {
        // Equator: z14 ≈ 2446 m, z17 ≈ 306 m.
        assertEquals(2446.0, SlippyTile.tileEdgeMeters(14, 0.0), 2.0)
        assertEquals(305.75, SlippyTile.tileEdgeMeters(17, 0.0), 1.0)
        // ~52.5°N (Berlin): z14 ≈ 1490 m, z17 ≈ 186 m.
        assertEquals(1490.0, SlippyTile.tileEdgeMeters(14, 52.52), 15.0)
        assertEquals(186.0, SlippyTile.tileEdgeMeters(17, 52.52), 3.0)
    }

    @Test
    fun longitude_wraps_and_latitude_clamps() {
        // 190°E wraps to -170°; must still produce a valid in-range tile.
        val wrapped = SlippyTile.tileOf(0.0, 190.0, 14)
        val n = SlippyTile.tilesPerAxis(14)
        assertTrue(SlippyTile.keyX(wrapped) in 0 until n)
        // Beyond the Mercator limit clamps rather than producing NaN.
        val polar = SlippyTile.tileOf(89.9, 10.0, 14)
        assertTrue(SlippyTile.keyY(polar) in 0 until n)
    }
}
