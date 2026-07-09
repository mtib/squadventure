package dev.mtib.squadventure.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mtib.squadventure.R
import dev.mtib.squadventure.phone.ui.DetailScreen
import dev.mtib.squadventure.phone.ui.HistoryScreen
import dev.mtib.squadventure.phone.ui.MapScreen
import dev.mtib.squadventure.phone.ui.RecordScreen
import dev.mtib.squadventure.phone.ui.SquadventureTheme

/**
 * Single-activity host: Navigation Compose with a bottom bar for the Map / History / Record
 * destinations and a pushed `detail/{id}` route. Shortcut actions ([CreateActivityShortcutActivity]'s
 * `ACTION_START_ACTIVITY`, [ACTION_IMPORT_GPX]) route on both cold start and while already running
 * ([onNewIntent], since the activity is `singleTop`).
 */
class MainActivity : ComponentActivity() {

    private var pendingAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAction = intent?.action
        setContent {
            SquadventureTheme {
                Surface {
                    SquadventureNavHost(
                        pendingAction = pendingAction,
                        onActionConsumed = { pendingAction = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = intent.action
    }

    companion object {
        const val ACTION_IMPORT_GPX = "dev.mtib.squadventure.IMPORT_GPX"
    }
}

private data class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination("map", R.string.nav_map, Icons.Filled.Map),
    TopLevelDestination("history", R.string.nav_history, Icons.Filled.History),
    TopLevelDestination("record", R.string.nav_record, Icons.Filled.FiberManualRecord),
)

@Composable
private fun SquadventureNavHost(pendingAction: String?, onActionConsumed: () -> Unit) {
    val navController = rememberNavController()
    var autoImport by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            CreateActivityShortcutActivity.ACTION_START_ACTIVITY -> {
                navController.navigate("record") { launchSingleTop = true }
                onActionConsumed()
            }
            MainActivity.ACTION_IMPORT_GPX -> {
                navController.navigate("history") { launchSingleTop = true }
                autoImport = true
                onActionConsumed()
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == null || topLevelDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.padding(padding),
        ) {
            composable("map") { MapScreen() }
            composable("history") {
                HistoryScreen(
                    onOpenDetail = { id -> navController.navigate("detail/$id") },
                    autoImport = autoImport,
                    onAutoImportHandled = { autoImport = false },
                )
            }
            composable("record") { RecordScreen() }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                if (id != null) {
                    DetailScreen(id = id, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
