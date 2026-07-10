package dev.mtib.squadventure.core.tracking

import dev.mtib.squadventure.core.geo.Geo
import dev.mtib.squadventure.core.geo.SlippyTile
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the in-progress recording, read by [dev.mtib.squadventure] the tracking
 * service, the Compose UI, and the widget (same process, no DI/IPC — mirrors transcribe's
 * `RecordingController`). Deliberately Android-free: the caller injects timestamps ([start]/[tick])
 * so this is unit-testable.
 */
object TrackingController {

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _transportMode = MutableStateFlow(TransportMode.WALK)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _path = MutableStateFlow<List<TrackPoint>>(emptyList())
    val path: StateFlow<List<TrackPoint>> = _path.asStateFlow()

    private val _liveSquadratinhos = MutableStateFlow<Set<Long>>(emptySet())
    val liveSquadratinhos: StateFlow<Set<Long>> = _liveSquadratinhos.asStateFlow()

    private var startedAtMs = 0L

    /** Last point distance was accrued to; the live counter uses the same movement dead-band as the
     * saved distance ([Geo.denoisedDistanceMeters]) so stationary GPS jitter doesn't inflate it. */
    private var distanceAnchor: TrackPoint? = null

    @Synchronized
    fun start(mode: TransportMode, startedAtMs: Long) {
        reset()
        _transportMode.value = mode
        this.startedAtMs = startedAtMs
        _isTracking.value = true
    }

    fun setMode(mode: TransportMode) {
        _transportMode.value = mode
    }

    @Synchronized
    fun onLocation(point: TrackPoint) {
        if (!_isTracking.value) return
        val current = _path.value
        val anchor = distanceAnchor
        if (anchor == null) {
            distanceAnchor = point
        } else {
            val step = Geo.haversineMeters(anchor.lat, anchor.lon, point.lat, point.lon)
            if (step >= Geo.DEADBAND_MIN_STEP_METERS) {
                _distanceMeters.value += step
                distanceAnchor = point
            }
        }
        _path.value = current + point
        val key = SlippyTile.squadratinhoOf(point.lat, point.lon)
        if (key !in _liveSquadratinhos.value) {
            _liveSquadratinhos.value = _liveSquadratinhos.value + key
        }
    }

    /** Advance the elapsed timer from a wall-clock now (the service ticks this ~1/s). */
    fun tick(nowMs: Long) {
        if (_isTracking.value) _elapsedMs.value = (nowMs - startedAtMs).coerceAtLeast(0L)
    }

    /** Stop and return the recorded path for the caller to persist. */
    @Synchronized
    fun stop(): List<TrackPoint> {
        _isTracking.value = false
        return _path.value
    }

    @Synchronized
    fun reset() {
        _isTracking.value = false
        _elapsedMs.value = 0L
        _distanceMeters.value = 0.0
        _path.value = emptyList()
        _liveSquadratinhos.value = emptySet()
        startedAtMs = 0L
        distanceAnchor = null
    }
}
