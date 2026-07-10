package dev.mtib.squadventure.core.gpx

import dev.mtib.squadventure.core.model.TrackPoint
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One `<trk>` from a GPX file — a single activity's worth of points. */
data class ParsedTrack(
    val name: String?,
    val points: List<TrackPoint>,
)

/**
 * Minimal GPX 1.1 reader/writer. Reading uses DOM (available on both Android and the JVM, so this
 * is unit-testable off-device). A file may contain several `<trk>` elements — a "many activities"
 * export — and each becomes one [ParsedTrack]; `<trkseg>`s within a track are concatenated.
 */
object Gpx {

    private val ISO: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun parse(input: InputStream): List<ParsedTrack> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(input)
        doc.documentElement.normalize()

        val tracks = doc.getElementsByTagName("trk")
        val result = ArrayList<ParsedTrack>()
        if (tracks.length > 0) {
            for (i in 0 until tracks.length) {
                val trk = tracks.item(i) as Element
                val name = trk.getElementsByTagName("name").let { if (it.length > 0) it.item(0).textContent?.trim() else null }
                val pts = parsePointsUnder(trk, "trkpt")
                if (pts.isNotEmpty()) result.add(ParsedTrack(name, pts))
            }
        }
        if (result.isEmpty()) {
            // Fall back to route points, then standalone waypoints (some exporters use these).
            val rte = parsePointsUnder(doc.documentElement, "rtept")
            if (rte.isNotEmpty()) result.add(ParsedTrack(null, rte))
            else {
                val wpt = parsePointsUnder(doc.documentElement, "wpt")
                if (wpt.isNotEmpty()) result.add(ParsedTrack(null, wpt))
            }
        }
        return result
    }

    private fun parsePointsUnder(root: Element, tag: String): List<TrackPoint> {
        val nodes = root.getElementsByTagName(tag)
        val points = ArrayList<TrackPoint>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            val ele = childText(el, "ele")?.toDoubleOrNull()
            val time = childText(el, "time")?.let { parseTime(it) }
            points.add(TrackPoint(lat, lon, ele, time))
        }
        return points
    }

    private fun childText(el: Element, tag: String): String? {
        val kids = el.getElementsByTagName(tag)
        for (i in 0 until kids.length) {
            val node = kids.item(i)
            if (node.parentNode === el) return node.textContent?.trim()
        }
        return null
    }

    /**
     * Parses a GPX `<time>` (ISO-8601). Handles `Z`, numeric offsets (`+02:00`) and fractional
     * seconds — which real exporters/health apps emit and the strict `...ssZ` format rejected,
     * leaving every imported activity with a 0:00 duration.
     */
    private fun parseTime(raw: String): Long? {
        val s = raw.trim()
        return runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
    }

    fun write(points: List<TrackPoint>, name: String?): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Squadventure\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n")
        if (!name.isNullOrBlank()) append("    <name>${escape(name)}</name>\n")
        append("    <trkseg>\n")
        val fmt = ISO
        for (p in points) {
            append("      <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
            p.elevationMeters?.let { append("<ele>$it</ele>") }
            p.timeMs?.let { append("<time>${fmt.format(java.util.Date(it))}</time>") }
            append("</trkpt>\n")
        }
        append("    </trkseg>\n")
        append("  </trk>\n")
        append("</gpx>\n")
    }

    /**
     * Stable content hash of a point stream — coordinates rounded to ~1 cm and time to the second —
     * so re-importing the same track (even re-wrapped by a different exporter) is detected as a
     * duplicate.
     */
    fun contentHash(points: List<TrackPoint>): String = hashPoints(points, includeTime = true)

    /**
     * Geometry-only hash — same rounding as [contentHash] but ignoring time — so the same route
     * re-imported still matches even when the stored copy previously lost its timestamps.
     */
    fun geometryHash(points: List<TrackPoint>): String = hashPoints(points, includeTime = false)

    private fun hashPoints(points: List<TrackPoint>, includeTime: Boolean): String {
        val md = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder()
        for (p in points) {
            sb.setLength(0)
            sb.append(String.format(Locale.US, "%.7f,%.7f", p.lat, p.lon))
            if (includeTime) sb.append(',').append(p.timeMs?.div(1000) ?: -1L)
            sb.append('\n')
            md.update(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
