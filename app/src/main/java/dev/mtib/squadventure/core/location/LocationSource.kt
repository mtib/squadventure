package dev.mtib.squadventure.core.location

import dev.mtib.squadventure.core.model.TrackPoint

/** Abstracts GPS so the service and tests are decoupled from the platform provider. */
interface LocationSource {
    fun start(onFix: (TrackPoint) -> Unit)
    fun stop()
}
