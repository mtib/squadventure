package dev.mtib.squadventure.core.geo

import dev.mtib.squadventure.core.model.TrackPoint

/** Turns a point stream into the sets of squares it claims. */
object TileClaims {

    fun squadratinhos(points: List<TrackPoint>): Set<Long> {
        val out = HashSet<Long>()
        for (p in points) out.add(SlippyTile.squadratinhoOf(p.lat, p.lon))
        return out
    }

    fun squadrats(points: List<TrackPoint>): Set<Long> {
        val out = HashSet<Long>()
        for (p in points) out.add(SlippyTile.squadratOf(p.lat, p.lon))
        return out
    }

    /** The z14 squadrats implied by a set of z17 squadratinhos (cheaper than re-scanning points). */
    fun squadratsFromSquadratinhos(squadratinhos: Set<Long>): Set<Long> {
        val out = HashSet<Long>()
        for (k in squadratinhos) out.add(SlippyTile.squadratForSquadratinho(k))
        return out
    }
}
