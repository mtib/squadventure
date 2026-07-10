package dev.mtib.squadventure.core.metrics

import dev.mtib.squadventure.core.geo.SlippyTile

/**
 * Derived stats over a set of claimed tiles (packed [SlippyTile] keys). Squadrats' vocabulary:
 *  - [total]        — number of distinct claimed tiles.
 *  - [yard]         — size of the largest cluster of *surrounded* tiles (VeloViewer "max cluster"):
 *                     a tile only counts if all four of its N/E/S/W neighbours are also claimed, and
 *                     the yard is the largest 4-connected group of such surrounded tiles. So a 3×3
 *                     block has a yard of 1 (its centre), a 4×4 block a yard of 4 (its 2×2 core).
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

    /** Size of the largest cluster of surrounded tiles ("yard" / VeloViewer max cluster). */
    fun largestCluster(tiles: Set<Long>): Int = largestClusterTiles(tiles).size

    /**
     * The tiles forming the largest "yard": the biggest 4-connected group of *surrounded* tiles,
     * where a tile is surrounded iff all four of its N/E/S/W neighbours are also claimed. Boundary
     * tiles (with an unclaimed neighbour) never count. Empty if no tile is fully surrounded.
     */
    fun largestClusterTiles(tiles: Set<Long>): Set<Long> {
        if (tiles.isEmpty()) return emptySet()
        val surrounded = HashSet<Long>()
        for (t in tiles) {
            val x = SlippyTile.keyX(t)
            val y = SlippyTile.keyY(t)
            if (neighbors(x, y).all { it in tiles }) surrounded.add(t)
        }
        if (surrounded.isEmpty()) return emptySet()

        val visited = HashSet<Long>(surrounded.size)
        val stack = ArrayDeque<Long>()
        var best: Set<Long> = emptySet()
        for (start in surrounded) {
            if (start in visited) continue
            val component = HashSet<Long>()
            stack.addLast(start)
            visited.add(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                component.add(cur)
                val x = SlippyTile.keyX(cur)
                val y = SlippyTile.keyY(cur)
                for (n in neighbors(x, y)) {
                    if (n in surrounded && visited.add(n)) stack.addLast(n)
                }
            }
            if (component.size > best.size) best = component
        }
        return best
    }

    /** Side length N of the largest solid N×N block ("übersquadrat" / max square). */
    fun largestFilledSquare(tiles: Set<Long>): Int {
        val t = largestFilledSquareTiles(tiles)
        return if (t.isEmpty()) 0 else Math.round(Math.sqrt(t.size.toDouble())).toInt()
    }

    /**
     * The tiles forming the largest solid N×N block. Sparse maximal-square DP: process cells in
     * (y, x) order so the up / left / up-left neighbours are already solved; dp = 1 + min(left, up,
     * upLeft) for occupied cells. Tracks the bottom-right cell of the best square, then enumerates it.
     */
    fun largestFilledSquareTiles(tiles: Set<Long>): Set<Long> {
        if (tiles.isEmpty()) return emptySet()
        val ordered = tiles.sortedWith(compareBy({ SlippyTile.keyY(it) }, { SlippyTile.keyX(it) }))
        val dp = HashMap<Long, Int>(tiles.size)
        var bestSide = 0
        var bestX = 0
        var bestY = 0
        for (cur in ordered) {
            val x = SlippyTile.keyX(cur)
            val y = SlippyTile.keyY(cur)
            val left = dp[SlippyTile.key(x - 1, y)] ?: 0
            val up = dp[SlippyTile.key(x, y - 1)] ?: 0
            val upLeft = dp[SlippyTile.key(x - 1, y - 1)] ?: 0
            val side = 1 + minOf(left, up, upLeft)
            dp[cur] = side
            if (side > bestSide) {
                bestSide = side
                bestX = x
                bestY = y
            }
        }
        if (bestSide == 0) return emptySet()
        val out = HashSet<Long>(bestSide * bestSide)
        for (dy in 0 until bestSide) {
            for (dx in 0 until bestSide) {
                out.add(SlippyTile.key(bestX - dx, bestY - dy))
            }
        }
        return out
    }

    private fun neighbors(x: Int, y: Int): LongArray = longArrayOf(
        SlippyTile.key(x + 1, y),
        SlippyTile.key(x - 1, y),
        SlippyTile.key(x, y + 1),
        SlippyTile.key(x, y - 1),
    )
}
