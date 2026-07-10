package dev.mtib.squadventure.core.activity

import android.content.Context
import dev.mtib.squadventure.core.geo.Geo
import dev.mtib.squadventure.core.geo.SlippyTile
import dev.mtib.squadventure.core.geo.TileClaims
import dev.mtib.squadventure.core.gpx.Gpx
import dev.mtib.squadventure.core.model.ActivityMeta
import dev.mtib.squadventure.core.model.ActivitySource
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cached derived claims (`squares.json`) so the global map/widget avoid re-parsing every GPX. */
@Serializable
private data class DerivedClaims(val squadratinhos: LongArray)

sealed interface ImportResult {
    data class Added(val meta: ActivityMeta) : ImportResult
    data object Duplicate : ImportResult
    data object Empty : ImportResult
}

/**
 * File-backed activity store: `filesDir/activities/<id>/{track.gpx, meta.json, squares.json}`.
 * Mirrors the transcribe app's plain-file + kotlinx.serialization pattern (no Room/DataStore).
 */
class ActivityRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; coerceInputValues = true }
    private val root: File = File(context.filesDir, "activities").apply { mkdirs() }

    fun list(): List<ActivityMeta> =
        root.listFiles { f -> f.isDirectory }
            ?.mapNotNull { readMeta(it.name) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    fun readMeta(id: String): ActivityMeta? {
        val f = File(dir(id), META)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<ActivityMeta>(f.readText()) }.getOrNull()
    }

    fun loadPoints(id: String): List<TrackPoint> {
        val f = File(dir(id), TRACK)
        if (!f.exists()) return emptyList()
        return f.inputStream().use { Gpx.parse(it) }.flatMap { it.points }
    }

    fun loadSquadratinhoKeys(id: String): Set<Long> {
        val f = File(dir(id), SQUARES)
        if (f.exists()) {
            runCatching { json.decodeFromString<DerivedClaims>(f.readText()) }
                .getOrNull()?.let { return it.squadratinhos.toHashSet() }
        }
        // Backfill for older activities that predate the cache.
        val keys = TileClaims.squadratinhos(loadPoints(id))
        writeDerived(id, keys)
        return keys
    }

    fun existingHashes(): Set<String> = list().map { it.contentHash }.filter { it.isNotEmpty() }.toHashSet()

    /** Union of claimed z17 squares across activities, optionally restricted to [modes]. */
    fun allSquadratinhoKeys(modes: Set<dev.mtib.squadventure.core.model.TransportMode>? = null): Set<Long> {
        val out = HashSet<Long>()
        for (m in list()) {
            if (modes != null && m.transportMode !in modes) continue
            out.addAll(loadSquadratinhoKeys(m.id))
        }
        return out
    }

    /**
     * All-time totals plus trailing-[windowMs] gains. [nowMs] is injected (no clock in core).
     */
    fun globalStats(nowMs: Long, windowMs: Long = 7L * 24 * 60 * 60 * 1000): GlobalStats {
        val cutoff = nowMs - windowMs
        val all = HashSet<Long>()
        val older = HashSet<Long>()
        var totalMeters = 0.0
        var weekMeters = 0.0
        for (m in list()) {
            val keys = loadSquadratinhoKeys(m.id)
            all.addAll(keys)
            totalMeters += m.distanceMeters
            if (m.createdAt >= cutoff) weekMeters += m.distanceMeters else older.addAll(keys)
        }
        val allBig = TileClaims.squadratsFromSquadratinhos(all)
        val olderBig = TileClaims.squadratsFromSquadratinhos(older)
        return GlobalStats(
            squadrats = allBig.size,
            squadratinhos = all.size,
            totalMeters = totalMeters,
            weekSquadrats = allBig.size - olderBig.size,
            weekSquadratinhos = all.size - older.size,
            weekMeters = weekMeters,
        )
    }

    fun gpxFile(id: String): File = File(dir(id), TRACK)

    /** Persist a freshly recorded track. [createdAt]/[id] are injected (no clock in core). */
    fun saveRecorded(
        id: String,
        createdAt: Long,
        points: List<TrackPoint>,
        mode: TransportMode,
        title: String? = null,
    ): ActivityMeta = save(id, createdAt, points, mode, ActivitySource.RECORDED, title)

    /**
     * Import a track. Returns [ImportResult.Duplicate] if an activity with the same content hash
     * already exists, [ImportResult.Empty] for a track with no usable points. Imports default to
     * [TransportMode.OTHER]; the user can re-tag afterwards.
     */
    fun import(
        id: String,
        createdAt: Long,
        points: List<TrackPoint>,
        title: String?,
        knownHashes: Set<String> = existingHashes(),
    ): ImportResult {
        if (points.isEmpty()) return ImportResult.Empty
        val hash = Gpx.contentHash(points)
        if (hash in knownHashes) return ImportResult.Duplicate
        val meta = save(id, createdAt, points, TransportMode.OTHER, ActivitySource.IMPORTED, title)
        return ImportResult.Added(meta)
    }

    fun updateTransportMode(id: String, mode: TransportMode) {
        val meta = readMeta(id) ?: return
        writeMeta(meta.copy(transportMode = mode))
    }

    fun rename(id: String, title: String?) {
        val meta = readMeta(id) ?: return
        writeMeta(meta.copy(title = title))
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    private fun save(
        id: String,
        createdAt: Long,
        points: List<TrackPoint>,
        mode: TransportMode,
        source: ActivitySource,
        title: String?,
    ): ActivityMeta {
        dir(id).mkdirs()
        gpxFile(id).writeText(Gpx.write(points, title))
        val squadratinhos = TileClaims.squadratinhos(points)
        writeDerived(id, squadratinhos)
        val squadrats = TileClaims.squadratsFromSquadratinhos(squadratinhos)
        val timed = points.mapNotNull { it.timeMs }
        val meta = ActivityMeta(
            id = id,
            createdAt = createdAt,
            transportMode = mode,
            source = source,
            title = title,
            distanceMeters = Geo.pathLengthMeters(points),
            durationMs = if (timed.size >= 2) timed.max() - timed.min() else 0L,
            pointCount = points.size,
            squadratCount = squadrats.size,
            squadratinhoCount = squadratinhos.size,
            startLat = points.firstOrNull()?.lat,
            startLon = points.firstOrNull()?.lon,
            contentHash = Gpx.contentHash(points),
        )
        writeMeta(meta)
        return meta
    }

    private fun writeMeta(meta: ActivityMeta) {
        File(dir(meta.id), META).writeText(json.encodeToString(ActivityMeta.serializer(), meta))
    }

    private fun writeDerived(id: String, squadratinhos: Set<Long>) {
        File(dir(id), SQUARES).writeText(
            json.encodeToString(DerivedClaims.serializer(), DerivedClaims(squadratinhos.toLongArray())),
        )
    }

    private fun dir(id: String): File = File(root, id)

    companion object {
        private const val TRACK = "track.gpx"
        private const val META = "meta.json"
        private const val SQUARES = "squares.json"
    }
}
