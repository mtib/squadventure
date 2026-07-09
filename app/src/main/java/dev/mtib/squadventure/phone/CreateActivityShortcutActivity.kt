package dev.mtib.squadventure.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.mtib.squadventure.R

/**
 * Handles `ACTION_CREATE_SHORTCUT` so "Start activity" shows up in launchers' widget/shortcut picker
 * (the "widget shortcut"), mirroring transcribe's `CreateRecordingShortcutActivity`. Selecting it
 * pins a shortcut that opens the app straight into recording.
 */
class CreateActivityShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            val launch = Intent(this, MainActivity::class.java)
                .setAction(ACTION_START_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val shortcut = ShortcutInfoCompat.Builder(this, "start_activity")
                .setShortLabel(getString(R.string.shortcut_new_short))
                .setLongLabel(getString(R.string.shortcut_new_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_track))
                .setIntent(launch)
                .build()
            setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        const val ACTION_START_ACTIVITY = "dev.mtib.squadventure.START_ACTIVITY"
    }
}
