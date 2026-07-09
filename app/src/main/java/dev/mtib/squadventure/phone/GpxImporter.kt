package dev.mtib.squadventure.phone

import android.content.Context
import android.net.Uri
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.activity.ImportResult
import dev.mtib.squadventure.core.gpx.Gpx

/** Shared GPX import path for the History screen's SAF picker and [ImportActivity]'s share target. */
object GpxImporter {

    data class Summary(val added: Int = 0, val duplicate: Int = 0, val empty: Int = 0, val failed: Int = 0)

    /**
     * Imports every `<trk>` found across [uris]. A multi-track file becomes several activities.
     * Dedup runs against the repository's existing hashes plus hashes newly added within this batch.
     */
    fun importUris(context: Context, repo: ActivityRepository, uris: List<Uri>): Summary {
        var added = 0
        var duplicate = 0
        var empty = 0
        var failed = 0
        var hashes = repo.existingHashes()
        var seq = 0
        for (uri in uris) {
            val tracks = try {
                context.contentResolver.openInputStream(uri)?.use { Gpx.parse(it) } ?: emptyList()
            } catch (e: Exception) {
                failed++
                continue
            }
            if (tracks.isEmpty()) {
                empty++
                continue
            }
            for (track in tracks) {
                seq++
                val createdAt = track.points.firstOrNull()?.timeMs ?: System.currentTimeMillis()
                val id = "imp-${System.currentTimeMillis()}-$seq"
                when (val result = repo.import(id, createdAt, track.points, track.name, hashes)) {
                    is ImportResult.Added -> {
                        added++
                        hashes = hashes + result.meta.contentHash
                    }
                    ImportResult.Duplicate -> duplicate++
                    ImportResult.Empty -> empty++
                }
            }
        }
        return Summary(added, duplicate, empty, failed)
    }

    fun message(context: Context, summary: Summary): String {
        val parts = buildList {
            if (summary.added > 0) {
                add(context.resources.getQuantityString(R.plurals.import_added, summary.added, summary.added))
            }
            if (summary.duplicate > 0) add(context.getString(R.string.import_duplicate))
            if (summary.empty > 0) add(context.getString(R.string.import_empty))
            if (summary.failed > 0) add(context.getString(R.string.import_failed))
        }
        return if (parts.isEmpty()) context.getString(R.string.import_empty) else parts.joinToString(" · ")
    }
}
