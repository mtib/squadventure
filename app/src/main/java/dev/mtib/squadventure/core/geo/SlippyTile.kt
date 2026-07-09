package dev.mtib.squadventure.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * OpenStreetMap "slippy map" tile math — the grid Squadrats is built on.
 *
 * A **squadrat** (big square) is a zoom-14 tile; a **squadratinho** (small square) is a zoom-17
 * tile. Three zoom levels apart means one squadrat contains exactly 8×8 = 64 squadratinhos, so
 * `squadratOf(squadratinho)` is a bit-shift by [ZOOM_SQUADRATINHO] − [ZOOM_SQUADRAT].
 *
 * Tiles are packed into a single [Long] key (`x` in the high 32 bits, `y` in the low 32) so claimed
 * sets are plain `Set<Long>` with no per-tile allocation.
 */
object SlippyTile {
    const val ZOOM_SQUADRAT = 14
    const val ZOOM_SQUADRATINHO = 17

    /** Earth circumference at the equator in the Web Mercator projection (meters). */
    const val EQUATOR_METERS = 40_075_016.686

    fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    fun keyX(key: Long): Int = (key shr 32).toInt()
    fun keyY(key: Long): Int = (key and 0xFFFFFFFFL).toInt()

    /** Number of tiles per axis at [zoom] (2^zoom). */
    fun tilesPerAxis(zoom: Int): Int = 1 shl zoom

    /**
     * Maps a coordinate to its tile at [zoom] using the standard slippy-map formulas. Longitude is
     * wrapped to [-180, 180); latitude is clamped to the Web Mercator limit (~±85.0511°).
     */
    fun tileOf(lat: Double, lon: Double, zoom: Int): Long {
        val n = tilesPerAxis(zoom)
        val wrappedLon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val clampedLat = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val latRad = Math.toRadians(clampedLat)
        var x = floor((wrappedLon + 180.0) / 360.0 * n).toInt()
        var y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt()
        x = x.coerceIn(0, n - 1)
        y = y.coerceIn(0, n - 1)
        return key(x, y)
    }

    fun squadratOf(lat: Double, lon: Double): Long = tileOf(lat, lon, ZOOM_SQUADRAT)
    fun squadratinhoOf(lat: Double, lon: Double): Long = tileOf(lat, lon, ZOOM_SQUADRATINHO)

    /** The squadrat (z14) key containing a squadratinho (z17) key. */
    fun squadratForSquadratinho(squadratinhoKey: Long): Long {
        val shift = ZOOM_SQUADRATINHO - ZOOM_SQUADRAT
        return key(keyX(squadratinhoKey) shr shift, keyY(squadratinhoKey) shr shift)
    }

    /** North-west (top-left) corner of a tile, as [lat, lon] in degrees. */
    fun tileNorthWest(x: Int, y: Int, zoom: Int): DoubleArray {
        val n = tilesPerAxis(zoom).toDouble()
        val lon = x / n * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        return doubleArrayOf(Math.toDegrees(latRad), lon)
    }

    /** Longitudinal width of a tile in degrees at [zoom] (constant across the globe). */
    fun tileWidthDegrees(zoom: Int): Double = 360.0 / tilesPerAxis(zoom)

    /**
     * Ground edge length of a tile in meters at the given [latitude]. Web Mercator tiles are square
     * on the ground and shrink toward the poles by cos(latitude), so this must be computed
     * per-latitude — never hardcode "1 mile" / "200 m".
     */
    fun tileEdgeMeters(zoom: Int, latitude: Double): Double =
        EQUATOR_METERS / tilesPerAxis(zoom) * cos(Math.toRadians(abs(latitude)))

    private const val MAX_MERCATOR_LAT = 85.05112878
}
