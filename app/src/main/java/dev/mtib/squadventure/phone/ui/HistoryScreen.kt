package dev.mtib.squadventure.phone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.model.ActivityMeta
import kotlinx.coroutines.launch

/** Latest signed APK, stable path (see .github/workflows/release.yml). Opened in the browser so the
 * app itself never touches the network — it stays offline / INTERNET-permission-free. */
private const val LATEST_RELEASE_APK_URL =
    "https://github.com/mtib/squadventure/releases/latest/download/squadventure.apk"

private fun openLatestRelease(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_APK_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenDetail: (String) -> Unit,
    autoImport: Boolean = false,
    onAutoImportHandled: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(),
) {
    val activities by viewModel.activities.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val message = viewModel.importUris(uris)
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(autoImport) {
        if (autoImport) {
            importLauncher.launch(arrayOf("*/*"))
            onAutoImportHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                text = { Text(stringResource(R.string.history_import)) },
            )
        },
    ) { padding ->
        val context = LocalContext.current
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { openLatestRelease(context) }) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.download_latest))
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    loading && activities.isEmpty() -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    activities.isEmpty() -> {
                        Text(
                            stringResource(R.string.history_empty),
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(activities, key = { it.id }) { meta ->
                                HistoryRow(meta = meta, onClick = { onOpenDetail(meta.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(meta: ActivityMeta, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                meta.transportMode.icon(),
                contentDescription = stringResource(meta.transportMode.labelRes()),
                modifier = Modifier,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(formatActivityDate(meta.createdAt), style = MaterialTheme.typography.bodyLarge)
                Text(Format.distance(meta.distanceMeters), style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${meta.squadratCount} ${stringResource(R.string.stat_squadrats)}", style = MaterialTheme.typography.labelMedium)
                Text("${meta.squadratinhoCount} ${stringResource(R.string.stat_squadratinhos)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
