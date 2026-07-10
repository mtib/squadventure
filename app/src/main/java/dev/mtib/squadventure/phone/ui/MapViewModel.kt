package dev.mtib.squadventure.phone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.geo.TileClaims
import dev.mtib.squadventure.core.metrics.SquareMetrics
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import dev.mtib.squadventure.phone.map.MapClaims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val claims: MapClaims = MapClaims(),
    val routes: List<List<TrackPoint>> = emptyList(),
    val showHeatmap: Boolean = true,
    val filter: TransportMode? = null,
    val loading: Boolean = true,
    val yard: Int = 0,
    val miniYard: Int = 0,
    val uberSquare: Int = 0,
    val uberMiniSquare: Int = 0,
)

/** Global-map data: claimed squares (optionally filtered by mode) and, when the heatmap is on, every contributing activity's path. */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    fun setFilter(mode: TransportMode?) {
        _state.value = _state.value.copy(filter = mode)
        refresh()
    }

    fun setHeatmap(on: Boolean) {
        _state.value = _state.value.copy(showHeatmap = on)
        refresh()
    }

    fun refresh() {
        val filter = _state.value.filter
        val showHeatmap = _state.value.showHeatmap
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val modes = filter?.let { setOf(it) }
            val squadratinhos = repo.allSquadratinhoKeys(modes)
            val squadrats = TileClaims.squadratsFromSquadratinhos(squadratinhos)
            val bigStats = SquareMetrics.stats(squadrats)
            val smallStats = SquareMetrics.stats(squadratinhos)
            val yardSquadrats = SquareMetrics.largestClusterTiles(squadrats)
            val yardSquadratinhos = SquareMetrics.largestClusterTiles(squadratinhos)
            val uberSquadrats = SquareMetrics.largestFilledSquareTiles(squadrats)
            val uberSquadratinhos = SquareMetrics.largestFilledSquareTiles(squadratinhos)
            val routes = if (showHeatmap) {
                repo.list().filter { modes == null || it.transportMode in modes }.map { repo.loadDisplayPoints(it.id) }
            } else {
                emptyList()
            }
            _state.value = _state.value.copy(
                claims = MapClaims(
                    squadrats = squadrats,
                    squadratinhos = squadratinhos,
                    yardSquadrats = yardSquadrats,
                    yardSquadratinhos = yardSquadratinhos,
                    uberSquadrats = uberSquadrats,
                    uberSquadratinhos = uberSquadratinhos,
                ),
                routes = routes,
                loading = false,
                yard = bigStats.yard,
                miniYard = smallStats.yard,
                uberSquare = bigStats.uberSquare,
                uberMiniSquare = smallStats.uberSquare,
            )
        }
    }
}
