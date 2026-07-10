package dev.mtib.squadventure.core.model

import kotlinx.serialization.Serializable

/**
 * How an activity was travelled. [OTHER] is the default for imported/shared GPX (the source rarely
 * says); the user can re-tag any activity afterwards. The map's filter chips are these plus "all".
 */
@Serializable
enum class TransportMode {
    WALK,
    BIKE,
    CAR,
    BOAT,
    OTHER,
}
