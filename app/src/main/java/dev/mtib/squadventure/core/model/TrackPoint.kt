package dev.mtib.squadventure.core.model

import kotlinx.serialization.Serializable

/**
 * A single recorded GPS fix. [elevationMeters] and [timeMs] are optional because imported GPX may
 * omit them (`timeMs == null` → unknown wall-clock time for that point).
 */
@Serializable
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double? = null,
    val timeMs: Long? = null,
)
