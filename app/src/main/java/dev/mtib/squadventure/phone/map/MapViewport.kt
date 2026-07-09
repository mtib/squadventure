package dev.mtib.squadventure.phone.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.mtib.squadventure.core.geo.SlippyTile
import kotlin.math.max
import kotlin.math.min

/**
 * A pan/zoom camera over the normalized `[0,1]²` [dev.mtib.squadventure.core.geo.WebMercator]
 * world. [origin] is the world coordinate at the canvas's top-left; [zoomPx] is screen pixels per
 * world unit, so `screen = (world - origin) * zoomPx`.
 */
data class MapViewport(val origin: Offset, val zoomPx: Float) {

    fun visibleWorldRect(canvasSize: IntSize): WorldRect {
        val w = canvasSize.width / zoomPx
        val h = canvasSize.height / zoomPx
        return WorldRect(origin.x, origin.y, origin.x + w, origin.y + h)
    }

    /** Projects a world-space point to screen pixels: bounded near `[0, canvasSize]` for on-screen points. */
    fun worldToScreen(world: Offset): Offset =
        Offset((world.x - origin.x) * zoomPx, (world.y - origin.y) * zoomPx)

    /** Applies a pinch/pan/zoom gesture, keeping [centroid] (screen px) fixed under the fingers. */
    fun applyGesture(canvasSize: IntSize, centroid: Offset, pan: Offset, zoomFactor: Float): MapViewport {
        val worldAtCentroid = Offset(origin.x + centroid.x / zoomPx, origin.y + centroid.y / zoomPx)
        val newZoom = zoomPx * zoomFactor
        val newOrigin = Offset(
            worldAtCentroid.x - centroid.x / newZoom - pan.x / newZoom,
            worldAtCentroid.y - centroid.y / newZoom - pan.y / newZoom,
        )
        return clamp(canvasSize, MapViewport(newOrigin, newZoom))
    }

    companion object {
        private const val STREET_ZOOM_LEVEL = 16
        private const val TILE_PIXELS = 256f
        private const val MAX_ZOOM_LEVEL = 20

        fun fitWorld(canvasSize: IntSize): MapViewport {
            val zoomPx = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f)
            val origin = Offset(
                0.5f - (canvasSize.width / zoomPx) / 2f,
                0.5f - (canvasSize.height / zoomPx) / 2f,
            )
            return MapViewport(origin, zoomPx)
        }

        fun centeredOn(canvasSize: IntSize, world: Offset): MapViewport {
            val zoomPx = SlippyTile.tilesPerAxis(STREET_ZOOM_LEVEL) * TILE_PIXELS
            val origin = Offset(
                world.x - (canvasSize.width / zoomPx) / 2f,
                world.y - (canvasSize.height / zoomPx) / 2f,
            )
            return clamp(canvasSize, MapViewport(origin, zoomPx))
        }

        private fun clamp(canvasSize: IntSize, viewport: MapViewport): MapViewport {
            val minZoom = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f)
            val maxZoom = SlippyTile.tilesPerAxis(MAX_ZOOM_LEVEL) * TILE_PIXELS
            val zoomPx = viewport.zoomPx.coerceIn(minZoom, maxZoom)

            val visibleW = canvasSize.width / zoomPx
            val visibleH = canvasSize.height / zoomPx
            val margin = 0.5f
            val minX = -margin
            val maxX = 1f + margin - visibleW
            val minY = -margin
            val maxY = 1f + margin - visibleH

            val x = if (minX <= maxX) viewport.origin.x.coerceIn(minX, maxX) else (minX + maxX) / 2f
            val y = if (minY <= maxY) viewport.origin.y.coerceIn(minY, maxY) else (minY + maxY) / 2f
            return MapViewport(Offset(x, y), zoomPx)
        }
    }
}

/** An axis-aligned rectangle in normalized `[0,1]²` world space; may extend beyond it slightly. */
data class WorldRect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    fun intersects(other: WorldRect): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    fun intersects(x: Float, y: Float, size: Float): Boolean =
        minX <= x + size && maxX >= x && minY <= y + size && maxY >= y

    fun union(other: WorldRect): WorldRect =
        WorldRect(min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY))

    /** Grows this rect by [marginFraction] of its own width/height on each side, for clip/cull margins. */
    fun expanded(marginFraction: Float): WorldRect {
        val marginX = (maxX - minX) * marginFraction
        val marginY = (maxY - minY) * marginFraction
        return WorldRect(minX - marginX, minY - marginY, maxX + marginX, maxY + marginY)
    }
}
