package dev.mtib.squadventure.phone.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import dev.mtib.squadventure.core.geo.WebMercator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The bundled low-poly world land basemap (`assets/world/world_lowpoly.geojson`, Natural Earth
 * 110m land polygons — see `scripts/world-asset/README.md`), parsed once into a single
 * normalized-[0,1]² [Path] in [WebMercator] space. Holes use [PathFillType.EvenOdd] rather than
 * ring winding, which is close enough for this low-poly, mostly-hole-free dataset.
 */
class WorldBasemap private constructor(val landPath: Path) {
    companion object {
        private const val ASSET_PATH = "world/world_lowpoly.geojson"

        @Volatile
        private var cached: WorldBasemap? = null

        suspend fun load(context: Context): WorldBasemap {
            cached?.let { return it }
            return withContext(Dispatchers.IO) {
                cached ?: parse(context).also { cached = it }
            }
        }

        private fun parse(context: Context): WorldBasemap {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val features = JSONObject(text).getJSONArray("features")
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            for (i in 0 until features.length()) {
                val geometry = features.getJSONObject(i).getJSONObject("geometry")
                when (geometry.getString("type")) {
                    "Polygon" -> addPolygon(geometry.getJSONArray("coordinates"), path)
                    "MultiPolygon" -> {
                        val polygons = geometry.getJSONArray("coordinates")
                        for (p in 0 until polygons.length()) {
                            addPolygon(polygons.getJSONArray(p), path)
                        }
                    }
                }
            }
            return WorldBasemap(path)
        }

        private fun addPolygon(polygon: JSONArray, path: Path) {
            for (r in 0 until polygon.length()) addRing(polygon.getJSONArray(r), path)
        }

        private fun addRing(ring: JSONArray, path: Path) {
            if (ring.length() == 0) return
            for (i in 0 until ring.length()) {
                val point = ring.getJSONArray(i)
                val xy = WebMercator.project(lat = point.getDouble(1), lon = point.getDouble(0))
                val offset = Offset(xy[0].toFloat(), xy[1].toFloat())
                if (i == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            path.close()
        }
    }
}
