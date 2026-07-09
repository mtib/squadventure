package dev.mtib.squadventure.phone.map

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.ui.geometry.Offset
import dev.mtib.squadventure.core.geo.WebMercator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One basemap polygon in [WebMercator] world space: an outer ring plus optional hole rings (each a
 * list of vertices, first point not repeated), and its bounding box for cull tests. Rendered with
 * [androidx.compose.ui.graphics.PathFillType.EvenOdd] so holes cut correctly within the polygon.
 */
class WorldPolygon(val rings: List<List<Offset>>, val bounds: WorldRect)

/**
 * The bundled offline world basemap: land coastlines (`assets/world/world_lowpoly.geojson`,
 * Natural Earth 10m land + minor islands) and lakes (`assets/world/world_lakes.geojson`, Natural
 * Earth 10m lakes) — see `scripts/world-asset/README.md`. Parsed once into [WorldPolygon] lists in
 * normalized `[0,1]²` world space via a streaming [JsonReader] (the files are large, so we never
 * build a full JSON tree in memory); per-frame rendering clips and projects only the polygons whose
 * bounds intersect the visible viewport (see [MapView]).
 */
class WorldBasemap private constructor(val land: List<WorldPolygon>, val lakes: List<WorldPolygon>) {
    companion object {
        private const val LAND_ASSET_PATH = "world/world_lowpoly.geojson"
        private const val LAKES_ASSET_PATH = "world/world_lakes.geojson"

        @Volatile
        private var cached: WorldBasemap? = null

        suspend fun load(context: Context): WorldBasemap {
            cached?.let { return it }
            return withContext(Dispatchers.IO) {
                cached ?: parse(context).also { cached = it }
            }
        }

        private fun parse(context: Context): WorldBasemap =
            WorldBasemap(parseFeatures(context, LAND_ASSET_PATH), parseFeatures(context, LAKES_ASSET_PATH))

        private fun parseFeatures(context: Context, assetPath: String): List<WorldPolygon> {
            val polygons = ArrayList<WorldPolygon>()
            JsonReader(context.assets.open(assetPath).bufferedReader()).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "features") {
                        reader.beginArray()
                        while (reader.hasNext()) parseFeature(reader, polygons)
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
            return polygons
        }

        private fun parseFeature(reader: JsonReader, out: MutableList<WorldPolygon>) {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "geometry" && reader.peek() != JsonToken.NULL) {
                    parseGeometry(reader, out)
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }

        private fun parseGeometry(reader: JsonReader, out: MutableList<WorldPolygon>) {
            reader.beginObject()
            var type: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> type = reader.nextString()
                    "coordinates" -> when (type) {
                        "Polygon" -> out.add(parsePolygon(reader))
                        "MultiPolygon" -> {
                            reader.beginArray()
                            while (reader.hasNext()) out.add(parsePolygon(reader))
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        /** Reads one polygon (`[ring, ...]`), projecting each vertex to world space and tracking bounds. */
        private fun parsePolygon(reader: JsonReader): WorldPolygon {
            val rings = ArrayList<List<Offset>>()
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            reader.beginArray()
            while (reader.hasNext()) {
                val ring = ArrayList<Offset>()
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginArray()
                    val lon = reader.nextDouble()
                    val lat = reader.nextDouble()
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                    val xy = WebMercator.project(lat, lon)
                    val x = xy[0].toFloat()
                    val y = xy[1].toFloat()
                    ring.add(Offset(x, y))
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                reader.endArray()
                if (ring.size > 1 && ring.first() == ring.last()) ring.removeAt(ring.size - 1)
                rings.add(ring)
            }
            reader.endArray()
            return WorldPolygon(rings, WorldRect(minX, minY, maxX, maxY))
        }
    }
}
