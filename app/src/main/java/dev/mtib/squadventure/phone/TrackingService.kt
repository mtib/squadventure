package dev.mtib.squadventure.phone

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.mtib.squadventure.R
import dev.mtib.squadventure.SquadventureApp
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.location.GpsLocationSource
import dev.mtib.squadventure.core.location.LocationSource
import dev.mtib.squadventure.core.model.TransportMode
import dev.mtib.squadventure.core.tracking.TrackingController
import dev.mtib.squadventure.phone.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns GPS capture while an activity is recording. Thin host: real state
 * lives in [TrackingController]. Mirrors transcribe's `RecordingService` (typed foreground service,
 * Stop action, partial wake lock, 1 Hz notification + widget refresh loop).
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var location: LocationSource
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAtMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        location = GpsLocationSource(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(); return START_NOT_STICKY }
            ACTION_START -> begin(intent)
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        if (TrackingController.isTracking.value) return
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { TransportMode.valueOf(it) }.getOrNull() }
            ?: TransportMode.WALK
        startedAtMs = System.currentTimeMillis()
        TrackingController.start(mode, startedAtMs)

        startForegroundCompat(buildNotification())
        acquireWakeLock()
        location.start { fix -> TrackingController.onLocation(fix) }

        scope.launch {
            while (isActive && TrackingController.isTracking.value) {
                TrackingController.tick(System.currentTimeMillis())
                notify(buildNotification())
                StatsWidgetProvider.update(applicationContext)
                delay(1_000)
            }
        }
    }

    private fun finish() {
        val points = TrackingController.stop()
        location.stop()
        if (points.size >= 2) {
            val id = "act-$startedAtMs"
            val mode = TrackingController.transportMode.value
            ActivityRepository(applicationContext).saveRecorded(id, startedAtMs, points, mode)
        }
        TrackingController.reset()
        StatsWidgetProvider.update(applicationContext)
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private fun buildNotification(): android.app.Notification {
        val content = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val status = getString(
            R.string.tracking_notification_text,
            Format.duration(TrackingController.elapsedMs.value),
            Format.distance(TrackingController.distanceMeters.value),
        )
        return NotificationCompat.Builder(this, SquadventureApp.TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_track)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(status)
            .setContentIntent(content)
            .addAction(0, getString(R.string.record_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(notification: android.app.Notification) {
        androidx.core.app.NotificationManagerCompat.from(this).let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            ) {
                it.notify(NOTIF_ID, notification)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Squadventure::tracking").apply {
            setReferenceCounted(false)
            acquire(MAX_TRACKING_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        location.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val MAX_TRACKING_MS = 12L * 60 * 60 * 1000
        const val ACTION_START = "dev.mtib.squadventure.action.START"
        const val ACTION_STOP = "dev.mtib.squadventure.action.STOP"
        const val EXTRA_MODE = "mode"

        fun start(context: Context, mode: TransportMode) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
