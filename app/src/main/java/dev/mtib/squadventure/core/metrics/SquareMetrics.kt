package dev.mtib.squadventure.core.metrics

import dev.mtib.squadventure.core.geo.SlippyTile

/**
 * Derived stats over a set of claimed tiles (packed [SlippyTile] keys). Squadrats' vocabulary:
 *  - [total]        — number of distinct claimed tiles.
 *  - [yard]         — size of the largest 4-connected (N/E/S/W) cluster of claimed tiles.
 *  - [uberSquare]   — side length N of the largest solid N×N block of all-claimed tiles.
 *
 * All algorithms are sparse (keyed off the claimed set), so they scale to a globe-spanning,
 * mostly-empty grid without ever allocating a bounding-box array.
 */
data class SquareStats(
    val total: Int,
    val yard: Int,
    val uberSquare: Int,
)

object SquareMetrics {

    fun stats(tiles: Set<Long>): SquareStats =
        SquareStats(total = tiles.size, yard = largestCluster(tiles), uberSquare = largestFilledSquare(tiles))

    /** Largest 4-connected component ("yard" / VeloViewer cluster). */
    fun largestCluster(tiles: Set<Long>): Int {
        if (tiles.isEmpty()) return 0
        val visited = HashSet<Long>(tiles.size)
        val stack = ArrayDeque<Long>()
        var best = 0
        for (start in tiles) {
            if (start in visited) continue
            var size = 0
            stack.addLast(start)
            visited.add(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                size++
                val x = SlippyTile.keyX(cur)
                val y = SlippyTile.keyY(cur)
                for (n in neighbors(x, y)) {
                    if (n in tiles && visited.add(n)) stack.addLast(n)
                }
            }
            if (size > best) best = size
        }
        return best
    }

    /**
     * Side length N of the largest solid N×N block ("übersquadrat" / max square). Sparse
     * maximal-square DP: process cells in (y, x) order so the up / left / up-left neighbours are
     * already solved; dp = 1 + min(left, up, upLeft) for occupied cells.
     */
    fun largestFilledSquare(tiles: Set<Long>): Int {
        if (tiles.isEmpty()) return 0
        val ordered = tiles.sortedWith(compareBy({ SlippyTile.keyY(it) }, { SlippyTile.keyX(it) }))
        val dp = HashMap<Long, Int>(tiles.size)
        var best = 0
        for (cur in ordered) {
            val x = SlippyTile.keyX(cur)
            val y = SlippyTile.keyY(cur)
            val left = dp[SlippyTile.key(x - 1, y)] ?: 0
            val up = dp[SlippyTile.key(x, y - 1)] ?: 0
            val upLeft = dp[SlippyTile.key(x - 1, y - 1)] ?: 0
            val side = 1 + minOf(left, up, upLeft)
            dp[cur] = side
            if (side > best) best = side
        }
        return best
    }

    private fun neighbors(x: Int, y: Int): LongArray = longArrayOf(
        SlippyTile.key(x + 1, y),
        SlippyTile.key(x - 1, y),
        SlippyTile.key(x, y + 1),
        SlippyTile.key(x, y - 1),
    )
}
