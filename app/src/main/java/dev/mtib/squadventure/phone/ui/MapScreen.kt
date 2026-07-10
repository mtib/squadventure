package dev.mtib.squadventure.phone.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.phone.map.MapView

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // Read once so the map opens centered/zoomed on the user; null (no permission/fix) falls back to world view.
    val currentLocation = remember { lastKnownLocation(context) }

    // Refresh whenever the Map destination resumes (e.g. after recording/importing on another tab),
    // so new squares and paths appear without reopening the app or toggling filters.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            modifier = Modifier.fillMaxSize(),
            claims = state.claims,
            routes = state.routes,
            showHeatmap = state.showHeatmap,
            showSquares = true,
            focus = currentLocation,
            currentLocation = currentLocation,
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransportModeChips(selected = state.filter, onSelect = viewModel::setFilter, showAll = true)
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.map_heatmap))
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = state.showHeatmap, onCheckedChange = viewModel::setHeatmap)
                }
            }
        }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Most-recent last-known fix across providers, or null if no location permission / no fix. */
internal fun lastKnownLocation(context: Context): TrackPoint? {
    val granted = { p: String -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
        return null
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in manager.allProviders }
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { TrackPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else null, it.time) }
    }.getOrNull()
}
