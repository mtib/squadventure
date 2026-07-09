package dev.mtib.squadventure.core.geo

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator projection to a normalized `[0,1]²` world square (0,0 = NW / -180°,+85.05°;
 * 1,1 = SE / +180°,-85.05°). This is the single coordinate system the map canvas draws in, and it
 * agrees with [SlippyTile]: a tile `(x,y)` at `zoom` occupies the normalized rectangle
 * `[x/2^zoom, (x+1)/2^zoom] × [y/2^zoom, (y+1)/2^zoom]`, so square overlays align exactly to the
 * basemap and to each other.
 */
object WebMercator {
    private const val MAX_LAT = 85.05112878

    fun lonToX(lon: Double): Double {
        val wrapped = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return (wrapped + 180.0) / 360.0
    }

    fun latToY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(clamped)
        return (1.0 - asinh(tan(latRad)) / PI) / 2.0
    }

    fun xToLon(x: Double): Double = x * 360.0 - 180.0

    fun yToLat(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))

    /** Returns `[x, y]` in `[0,1]²`. */
    fun project(lat: Double, lon: Double): DoubleArray = doubleArrayOf(lonToX(lon), latToY(lat))

    /** Normalized left/top edge of tile index [tile] at [zoom]. */
    fun tileEdge(tile: Int, zoom: Int): Double = tile.toDouble() / SlippyTile.tilesPerAxis(zoom)

    /** Normalized side length of one tile at [zoom]. */
    fun tileSpan(zoom: Int): Double = 1.0 / SlippyTile.tilesPerAxis(zoom)
}
