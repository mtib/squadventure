package dev.mtib.squadventure.phone.ui

import java.util.Locale
import kotlin.math.roundToLong

object Format {
    /** `H:MM:SS` (or `MM:SS` under an hour). */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Distance in km with one decimal (metres under 1 km). */
    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.roundToLong()} m"
        else String.format(Locale.US, "%.1f km", meters / 1000.0)

    fun km(meters: Double): String = String.format(Locale.US, "%.1f", meters / 1000.0)
}
