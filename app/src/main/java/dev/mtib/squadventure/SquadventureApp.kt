package dev.mtib.squadventure

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SquadventureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking"
    }
}
