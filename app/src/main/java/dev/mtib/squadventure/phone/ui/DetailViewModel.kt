package dev.mtib.squadventure.phone.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.geo.TileClaims
import dev.mtib.squadventure.core.metrics.SquareMetrics
import dev.mtib.squadventure.core.metrics.SquareStats
import dev.mtib.squadventure.core.model.ActivityMeta
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import dev.mtib.squadventure.phone.map.MapClaims
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState(
    val meta: ActivityMeta? = null,
    val points: List<TrackPoint> = emptyList(),
    val claims: MapClaims = MapClaims(),
    /** Stats over the z14 squadrat grid (labelled "Squares" in the UI). */
    val squareStats: SquareStats = SquareStats(0, 0, 0),
    /** Stats over the z17 squadratinho grid (labelled "Mini-Squares"); yard/max-square are shown from this finer grid. */
    val miniStats: SquareStats = SquareStats(0, 0, 0),
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            fetchAndPublish(id)
        }
    }

    fun updateMode(id: String, mode: TransportMode) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateTransportMode(id, mode)
            fetchAndPublish(id)
        }
    }

    private fun fetchAndPublish(id: String) {
        val meta = repo.readMeta(id)
        val points = repo.loadPoints(id)
        val squadratinhos = TileClaims.squadratinhos(points)
        val squadrats = TileClaims.squadratsFromSquadratinhos(squadratinhos)
        _state.value = DetailUiState(
            meta = meta,
            points = points,
            claims = MapClaims(squadrats = squadrats, squadratinhos = squadratinhos),
            squareStats = SquareMetrics.stats(squadrats),
            miniStats = SquareMetrics.stats(squadratinhos),
            loading = false,
        )
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            _state.value = _state.value.copy(deleted = true)
        }
    }

    /** Copies the GPX into `cacheDir/shared/` and returns a FileProvider [Uri] for sharing. */
    suspend fun prepareExportUri(id: String): Uri? = withContext(Dispatchers.IO) {
        val src = repo.gpxFile(id)
        if (!src.exists()) return@withContext null
        val context = getApplication<Application>()
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val dest = File(sharedDir, "$id.gpx")
        src.copyTo(dest, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }
}
