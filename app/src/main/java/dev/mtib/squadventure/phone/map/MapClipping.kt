package dev.mtib.squadventure.phone.map

import androidx.compose.ui.geometry.Offset

/**
 * Clips a closed polygon ring (vertices only, first point not repeated) to [rect] using
 * Sutherland–Hodgman. Guarantees every returned vertex lies within [rect], so projecting the
 * result to screen space afterwards can never produce coordinates far outside the canvas,
 * regardless of zoom.
 */
fun clipRingToRect(ring: List<Offset>, rect: WorldRect): List<Offset> {
    if (ring.size < 3) return emptyList()
    var points = clipEdge(ring, inside = { it.x >= rect.minX }, intersect = { a, b -> lerpX(a, b, rect.minX) })
    points = clipEdge(points, inside = { it.x <= rect.maxX }, intersect = { a, b -> lerpX(a, b, rect.maxX) })
    points = clipEdge(points, inside = { it.y >= rect.minY }, intersect = { a, b -> lerpY(a, b, rect.minY) })
    points = clipEdge(points, inside = { it.y <= rect.maxY }, intersect = { a, b -> lerpY(a, b, rect.maxY) })
    return points
}

private inline fun clipEdge(
    points: List<Offset>,
    inside: (Offset) -> Boolean,
    intersect: (Offset, Offset) -> Offset,
): List<Offset> {
    if (points.isEmpty()) return points
    val result = ArrayList<Offset>(points.size + 2)
    var previous = points[points.size - 1]
    var previousInside = inside(previous)
    for (current in points) {
        val currentInside = inside(current)
        if (currentInside != previousInside) {
            result.add(intersect(previous, current))
        }
        if (currentInside) result.add(current)
        previous = current
        previousInside = currentInside
    }
    return result
}

private fun lerpX(a: Offset, b: Offset, x: Float): Offset {
    val t = (x - a.x) / (b.x - a.x)
    return Offset(x, a.y + t * (b.y - a.y))
}

private fun lerpY(a: Offset, b: Offset, y: Float): Offset {
    val t = (y - a.y) / (b.y - a.y)
    return Offset(a.x + t * (b.x - a.x), y)
}

/**
 * Clips segment [a]-[b] to [rect] using Liang–Barsky. Returns the clipped endpoints, or `null` if
 * the segment doesn't intersect [rect] at all. Guarantees both returned points lie within [rect].
 */
fun clipSegmentToRect(a: Offset, b: Offset, rect: WorldRect): Pair<Offset, Offset>? {
    val dx = b.x - a.x
    val dy = b.y - a.y
    var t0 = 0f
    var t1 = 1f

    for (edge in 0 until 4) {
        val p: Float
        val q: Float
        when (edge) {
            0 -> { p = -dx; q = a.x - rect.minX }
            1 -> { p = dx; q = rect.maxX - a.x }
            2 -> { p = -dy; q = a.y - rect.minY }
            else -> { p = dy; q = rect.maxY - a.y }
        }
        if (p == 0f) {
            if (q < 0f) return null
        } else {
            val r = q / p
            if (p < 0f) {
                if (r > t1) return null
                if (r > t0) t0 = r
            } else {
                if (r < t0) return null
                if (r < t1) t1 = r
            }
        }
    }
    if (t0 > t1) return null
    return Offset(a.x + t0 * dx, a.y + t0 * dy) to Offset(a.x + t1 * dx, a.y + t1 * dy)
}
