package dev.mtib.squadventure.phone.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.model.TransportMode
import dev.mtib.squadventure.phone.map.MapView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(state.meta?.title ?: state.meta?.let { formatActivityDate(it.createdAt) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.prepareExportUri(id)?.let { uri ->
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/gpx+xml")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.detail_export))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_delete))
                    }
                },
            )
        },
    ) { padding ->
        val meta = state.meta
        if (state.loading || meta == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                MapView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    claims = state.claims,
                    routes = listOf(state.points),
                    fitPoints = state.points,
                    showSquares = true,
                )
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem(stringResource(R.string.stat_squadrats), state.squareStats.total.toString(), Modifier.weight(1f))
                        StatItem(stringResource(R.string.stat_squadratinhos), state.miniStats.total.toString(), Modifier.weight(1f))
                        StatItem(stringResource(R.string.stat_yard), state.miniStats.yard.toString(), Modifier.weight(1f))
                        StatItem(stringResource(R.string.stat_ubersquare), state.miniStats.uberSquare.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem(stringResource(R.string.stat_distance), Format.distance(meta.distanceMeters), Modifier.weight(1f))
                        StatItem(stringResource(R.string.stat_duration), Format.duration(meta.durationMs), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    ModeDropdown(mode = meta.transportMode, onSelect = { viewModel.updateMode(id, it) })
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(id) }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ModeDropdown(mode: TransportMode, onSelect: (TransportMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(mode.icon(), contentDescription = null, modifier = Modifier)
            Spacer(Modifier.width(8.dp))
            Text("${stringResource(R.string.detail_transport_mode)}: ${stringResource(mode.labelRes())}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TransportMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(stringResource(m.labelRes())) },
                    leadingIcon = { Icon(m.icon(), contentDescription = null) },
                    onClick = { expanded = false; onSelect(m) },
                )
            }
        }
    }
}
