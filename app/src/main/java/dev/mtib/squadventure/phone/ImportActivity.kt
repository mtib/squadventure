package dev.mtib.squadventure.phone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.mtib.squadventure.core.activity.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Translucent share-target for GPX files (`ACTION_SEND[_MULTIPLE]` / `ACTION_VIEW`, including
 * health apps that mis-tag GPX as a wildcard MIME type): imports then finishes with a localized
 * Toast, with no visible UI of its own.
 */
class ImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                val repo = ActivityRepository(applicationContext)
                val summary = GpxImporter.importUris(applicationContext, repo, uris)
                GpxImporter.message(applicationContext, summary)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun extractUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> intent.parcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) } ?: emptyList()
        Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        Intent.ACTION_VIEW -> intent.data?.let { listOf(it) } ?: emptyList()
        else -> emptyList()
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, T::class.java)
    else getParcelableExtra(name)

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.parcelableArrayListExtraCompat(name: String): ArrayList<T>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayListExtra(name, T::class.java)
    else getParcelableArrayListExtra(name)
