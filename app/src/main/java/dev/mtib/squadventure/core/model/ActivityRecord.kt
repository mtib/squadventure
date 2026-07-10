package dev.mtib.squadventure.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivitySource {
    /** Recorded live in the app with GPS. */
    RECORDED,

    /** Imported from a file or received via the share sheet. */
    IMPORTED,
}

/**
 * Persisted per-activity metadata (`meta.json`). Scalar stats are cached here so the History list
 * and widget never re-parse GPX; the point stream lives beside it in `track.gpx`.
 */
@Serializable
data class ActivityMeta(
    val id: String,
    val createdAt: Long,
    val transportMode: TransportMode = TransportMode.OTHER,
    val source: ActivitySource,
    val title: String? = null,
    val distanceMeters: Double = 0.0,
    val durationMs: Long = 0L,
    val pointCount: Int = 0,
    val squadratCount: Int = 0,
    val squadratinhoCount: Int = 0,
    val startLat: Double? = null,
    val startLon: Double? = null,
    /** SHA-256 of the canonical point stream; used to reject exact-duplicate imports. */
    val contentHash: String = "",
    /** SHA-256 of coordinates only (no time); matches the same route re-imported without timestamps. */
    val geometryHash: String = "",
    /** Version of the derived-stats computation used to produce this record; drives lazy migration. */
    val schemaVersion: Int = 0,
)

/** An activity together with its loaded point stream. */
data class ActivityRecord(
    val meta: ActivityMeta,
    val points: List<TrackPoint>,
)
