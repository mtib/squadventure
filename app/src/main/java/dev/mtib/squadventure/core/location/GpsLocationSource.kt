package dev.mtib.squadventure.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import dev.mtib.squadventure.core.model.TrackPoint

/**
 * Location via the platform [LocationManager] — no Play Services, so it adds no networked dependency
 * and keeps the app's zero-network guarantee. Listens on every enabled provider (fused / GPS /
 * network) so fixes arrive quickly indoors and out, and seeds an immediate point from a recent
 * last-known fix so the live path/marker appears at once. Caller must hold a location permission.
 */
class GpsLocationSource(
    context: Context,
    private val minTimeMs: Long = 1_000L,
    private val minDistanceMeters: Float = 4f,
) : LocationSource {

    private val manager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val active = mutableListOf<LocationListener>()

    @SuppressLint("MissingPermission")
    override fun start(onFix: (TrackPoint) -> Unit) {
        stop()
        val providers = candidateProviders().filter { manager.isProviderEnabled(it) }

        recentLastKnown(providers)?.let { onFix(it.toTrackPoint()) }

        for (provider in providers) {
            val listener = LocationListener { location -> onFix(location.toTrackPoint()) }
            manager.requestLocationUpdates(provider, minTimeMs, minDistanceMeters, listener, Looper.getMainLooper())
            active.add(listener)
        }
    }

    override fun stop() {
        active.forEach { manager.removeUpdates(it) }
        active.clear()
    }

    private fun candidateProviders(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.distinct().filter { it in manager.allProviders }

    @SuppressLint("MissingPermission")
    private fun recentLastKnown(providers: List<String>): Location? {
        val now = System.currentTimeMillis()
        return providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { now - it.time < MAX_SEED_AGE_MS }
            .maxByOrNull { it.time }
    }

    private fun Location.toTrackPoint(): TrackPoint = TrackPoint(
        lat = latitude,
        lon = longitude,
        elevationMeters = if (hasAltitude()) altitude else null,
        timeMs = time,
    )

    private companion object {
        const val MAX_SEED_AGE_MS = 2 * 60 * 1000L
    }
}
