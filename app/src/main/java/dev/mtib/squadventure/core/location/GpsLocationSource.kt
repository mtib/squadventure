package dev.mtib.squadventure.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import dev.mtib.squadventure.core.model.TrackPoint

/**
 * GPS via the platform [LocationManager] — no Play Services, so it adds no networked dependency and
 * keeps the app's zero-network guarantee. The caller must hold `ACCESS_FINE_LOCATION` before [start].
 */
class GpsLocationSource(
    context: Context,
    private val minTimeMs: Long = 2_000L,
    private val minDistanceMeters: Float = 5f,
) : LocationSource {

    private val manager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(onFix: (TrackPoint) -> Unit) {
        stop()
        val l = LocationListener { location: Location ->
            onFix(
                TrackPoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    elevationMeters = if (location.hasAltitude()) location.altitude else null,
                    timeMs = location.time,
                ),
            )
        }
        listener = l
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            minTimeMs,
            minDistanceMeters,
            l,
            Looper.getMainLooper(),
        )
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}
