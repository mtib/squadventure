package dev.mtib.squadventure.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.geo.TileClaims
import dev.mtib.squadventure.core.model.TrackPoint
import dev.mtib.squadventure.core.model.TransportMode
import dev.mtib.squadventure.core.tracking.TrackingController
import dev.mtib.squadventure.phone.TrackingService
import dev.mtib.squadventure.phone.map.MapClaims
import dev.mtib.squadventure.phone.map.MapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RecordScreen() {
    val context = LocalContext.current
    val isTracking by TrackingController.isTracking.collectAsState()
    val elapsedMs by TrackingController.elapsedMs.collectAsState()
    val distanceMeters by TrackingController.distanceMeters.collectAsState()
    val path by TrackingController.path.collectAsState()
    val liveSquadratinhos by TrackingController.liveSquadratinhos.collectAsState()

    var selectedMode by rememberSaveable { mutableStateOf(TransportMode.WALK) }
    var showBackgroundRationale by remember { mutableStateOf(false) }
    var askedBackground by rememberSaveable { mutableStateOf(false) }

    var modeClaims by remember { mutableStateOf(MapClaims()) }
    var modeRoutes by remember { mutableStateOf<List<List<TrackPoint>>>(emptyList()) }
    // Center the live map on the user right away (before the first fix arrives) so the matching
    // squares/heatmap are visible instead of a world-zoom view.
    val lastKnown = remember { lastKnownLocation(context) }

    LaunchedEffect(selectedMode, isTracking) {
        if (!isTracking) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val repo = ActivityRepository(context)
            val squadratinhos = repo.allSquadratinhoKeys(setOf(selectedMode))
            val squadrats = TileClaims.squadratsFromSquadratinhos(squadratinhos)
            val routes = repo.list().filter { it.transportMode == selectedMode }.map { repo.loadDisplayPoints(it.id) }
            modeClaims = MapClaims(squadrats = squadrats, squadratinhos = squadratinhos)
            modeRoutes = routes
        }
    }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasForegroundLocation() =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun beginTracking() = TrackingService.start(context, selectedMode)

    val backgroundPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { beginTracking() }

    /** Start now; only prompt for background location once, and never if it's already granted. */
    fun startOrRequestBackground() {
        if (hasBackgroundLocation() || askedBackground) {
            beginTracking()
        } else {
            askedBackground = true
            showBackgroundRationale = true
        }
    }

    val foregroundPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasForegroundLocation()
            if (fineGranted) startOrRequestBackground()
            else Toast.makeText(context, context.getString(R.string.perm_denied), Toast.LENGTH_SHORT).show()
        }

    fun onStart() {
        if (hasForegroundLocation()) {
            startOrRequestBackground()
        } else {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            foregroundPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false; beginTracking() },
            text = { Text(stringResource(R.string.perm_background_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundRationale = false; beginTracking() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (isTracking) {
        Column(Modifier.fillMaxSize()) {
            MapView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                claims = modeClaims,
                routes = modeRoutes,
                showHeatmap = true,
                showSquares = true,
                liveRoute = path,
                currentLocation = path.lastOrNull() ?: lastKnown,
                focus = path.firstOrNull() ?: lastKnown,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(stringResource(R.string.stat_duration), Format.duration(elapsedMs), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_distance), Format.distance(distanceMeters), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_squadratinhos), liveSquadratinhos.size.toString(), Modifier.weight(1f))
            }
            Button(
                onClick = { TrackingService.stop(context) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.record_stop))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.record_pick_mode), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            TransportModeChips(
                selected = selectedMode,
                onSelect = { mode -> mode?.let { selectedMode = it } },
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = { onStart() }) {
                Text(stringResource(R.string.record_start))
            }
        }
    }
}
