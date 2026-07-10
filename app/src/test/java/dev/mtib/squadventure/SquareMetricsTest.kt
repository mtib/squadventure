package dev.mtib.squadventure

import dev.mtib.squadventure.core.geo.SlippyTile.key
import dev.mtib.squadventure.core.metrics.SquareMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class SquareMetricsTest {

    private fun tiles(vararg xy: Pair<Int, Int>): Set<Long> = xy.map { key(it.first, it.second) }.toSet()

    @Test
    fun empty_set_is_all_zero() {
        val s = SquareMetrics.stats(emptySet())
        assertEquals(0, s.total)
        assertEquals(0, s.yard)
        assertEquals(0, s.uberSquare)
    }

    @Test
    fun single_tile() {
        val s = SquareMetrics.stats(tiles(3 to 7))
        assertEquals(1, s.total)
        assertEquals(1, s.yard)
        assertEquals(1, s.uberSquare)
    }

    @Test
    fun two_by_two_block() {
        val s = SquareMetrics.stats(tiles(0 to 0, 1 to 0, 0 to 1, 1 to 1))
        assertEquals(4, s.total)
        assertEquals(4, s.yard)
        assertEquals(2, s.uberSquare)
    }

    @Test
    fun l_shape_has_no_solid_two_square() {
        val s = SquareMetrics.stats(tiles(0 to 0, 1 to 0, 0 to 1))
        assertEquals(3, s.total)
        assertEquals(3, s.yard)
        assertEquals(1, s.uberSquare)
    }

    @Test
    fun three_by_three_solid() {
        val block = buildSet { for (x in 0..2) for (y in 0..2) add(key(x, y)) }
        val s = SquareMetrics.stats(block)
        assertEquals(9, s.total)
        assertEquals(9, s.yard)
        assertEquals(3, s.uberSquare)
    }

    @Test
    fun yard_is_the_largest_disconnected_cluster() {
        // A lone tile far away, plus a connected run of three.
        val s = SquareMetrics.stats(tiles(100 to 100, 0 to 0, 1 to 0, 2 to 0))
        assertEquals(4, s.total)
        assertEquals(3, s.yard)
        assertEquals(1, s.uberSquare)
    }

    @Test
    fun diagonal_touching_tiles_are_not_connected() {
        // 4-connectivity: diagonal neighbours don't join a cluster.
        val s = SquareMetrics.stats(tiles(0 to 0, 1 to 1))
        assertEquals(2, s.total)
        assertEquals(1, s.yard)
    }

    @Test
    fun largest_cluster_tiles_are_the_connected_run() {
        val cluster = SquareMetrics.largestClusterTiles(tiles(100 to 100, 0 to 0, 1 to 0, 2 to 0))
        assertEquals(setOf(key(0, 0), key(1, 0), key(2, 0)), cluster)
    }

    @Test
    fun largest_filled_square_tiles_are_the_solid_block() {
        // A 2x2 block at (0,0) plus an extra tile that doesn't extend the square.
        val block = SquareMetrics.largestFilledSquareTiles(tiles(0 to 0, 1 to 0, 0 to 1, 1 to 1, 2 to 0))
        assertEquals(4, block.size)
        assertEquals(setOf(key(0, 0), key(1, 0), key(0, 1), key(1, 1)), block)
        assertEquals(2, SquareMetrics.largestFilledSquare(tiles(0 to 0, 1 to 0, 0 to 1, 1 to 1, 2 to 0)))
    }
}
