package dev.mtib.squadventure

import dev.mtib.squadventure.core.geo.SlippyTile.key
import dev.mtib.squadventure.core.metrics.SquareMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class SquareMetricsTest {

    private fun tiles(vararg xy: Pair<Int, Int>): Set<Long> = xy.map { key(it.first, it.second) }.toSet()

    private fun solidBlock(w: Int, h: Int): Set<Long> =
        buildSet { for (x in 0 until w) for (y in 0 until h) add(key(x, y)) }

    @Test
    fun empty_set_is_all_zero() {
        val s = SquareMetrics.stats(emptySet())
        assertEquals(0, s.total)
        assertEquals(0, s.yard)
        assertEquals(0, s.uberSquare)
    }

    @Test
    fun single_tile_is_not_surrounded() {
        val s = SquareMetrics.stats(tiles(3 to 7))
        assertEquals(1, s.total)
        assertEquals(0, s.yard)
        assertEquals(1, s.uberSquare)
    }

    @Test
    fun two_by_two_block_has_no_surrounded_tile() {
        val s = SquareMetrics.stats(solidBlock(2, 2))
        assertEquals(4, s.total)
        assertEquals(0, s.yard)
        assertEquals(2, s.uberSquare)
    }

    @Test
    fun l_shape() {
        val s = SquareMetrics.stats(tiles(0 to 0, 1 to 0, 0 to 1))
        assertEquals(3, s.total)
        assertEquals(0, s.yard)
        assertEquals(1, s.uberSquare)
    }

    @Test
    fun three_by_three_solid_yard_is_only_the_centre() {
        val s = SquareMetrics.stats(solidBlock(3, 3))
        assertEquals(9, s.total)
        assertEquals(1, s.yard)
        assertEquals(3, s.uberSquare)
    }

    @Test
    fun four_by_four_solid_yard_is_the_inner_two_by_two() {
        val s = SquareMetrics.stats(solidBlock(4, 4))
        assertEquals(16, s.total)
        assertEquals(4, s.yard)
        assertEquals(4, s.uberSquare)
    }

    @Test
    fun plus_shape_yard_is_the_surrounded_centre() {
        // Centre (1,1) has all four neighbours; the arms do not.
        val s = SquareMetrics.stats(tiles(1 to 1, 0 to 1, 2 to 1, 1 to 0, 1 to 2))
        assertEquals(5, s.total)
        assertEquals(1, s.yard)
    }

    @Test
    fun diagonal_touching_tiles_have_no_yard() {
        val s = SquareMetrics.stats(tiles(0 to 0, 1 to 1))
        assertEquals(2, s.total)
        assertEquals(0, s.yard)
    }

    @Test
    fun yard_picks_the_largest_surrounded_cluster() {
        // A 4x4 (surrounded core of 4) plus a far-away 3x3 (surrounded core of 1): yard = 4.
        val far = buildSet { for (x in 20..22) for (y in 20..22) add(key(x, y)) }
        val s = SquareMetrics.stats(solidBlock(4, 4) + far)
        assertEquals(4, s.yard)
    }

    @Test
    fun largest_cluster_tiles_are_the_surrounded_core() {
        val core = SquareMetrics.largestClusterTiles(solidBlock(4, 4))
        assertEquals(setOf(key(1, 1), key(2, 1), key(1, 2), key(2, 2)), core)
    }

    @Test
    fun largest_filled_square_tiles_are_the_solid_block() {
        val block = SquareMetrics.largestFilledSquareTiles(tiles(0 to 0, 1 to 0, 0 to 1, 1 to 1, 2 to 0))
        assertEquals(4, block.size)
        assertEquals(setOf(key(0, 0), key(1, 0), key(0, 1), key(1, 1)), block)
        assertEquals(2, SquareMetrics.largestFilledSquare(tiles(0 to 0, 1 to 0, 0 to 1, 1 to 1, 2 to 0)))
    }
}
