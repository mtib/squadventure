package dev.mtib.squadventure.core.activity

/**
 * Aggregate totals across all activities, plus the amount gained in a trailing window (the widget's
 * "+N this week"). Week deltas are *newly claimed* squares — squares from recent activities that
 * weren't already claimed by older ones — while distance is simply summed over the window.
 */
data class GlobalStats(
    val squadrats: Int,
    val squadratinhos: Int,
    val totalMeters: Double,
    val weekSquadrats: Int,
    val weekSquadratinhos: Int,
    val weekMeters: Double,
)
