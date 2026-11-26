package tv.ham3l.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tv.ham3l.app.ui.backup.M3UExporter
import tv.ham3l.app.ui.vod.VodScreen
import tv.ham3l.app.ui.favorites.FavoritesScreen
import tv.ham3l.app.ui.globalsearch.GlobalSearchScreen
import tv.ham3l.app.ui.history.HistoryScreen
import tv.ham3l.app.ui.player.PlayerHub
import tv.ham3l.app.ui.playlists.PlaylistsScreen
import tv.ham3l.app.ui.player.PlayerScreen
import tv.ham3l.app.ui.playlistdetail.PlaylistDetailScreen
import tv.ham3l.app.ui.settings.SettingsScreen

object Routes {
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GLOBAL_SEARCH = "globalsearch"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun Ham3LRoot(sharedM3uUrl: String?) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        Tab(Routes.PLAYLISTS, "Live TV", Icons.Filled.LiveTv),
        Tab(Routes.FAVORITES, "Favoriten", Icons.Filled.Favorite),
        Tab(Routes.HISTORY, "Verlauf", Icons.Filled.History),
        Tab(Routes.SETTINGS, "Einstellungen", Icons.Filled.Settings),
    )
    val backStack = nav.currentBackStackEntryAsState()
    val current = backStack.value?.destination?.route
    val showBar = tabs.any { it.route == current }
    val miniPlayerRoute = current?.startsWith("player/") == true

    // Per Link geteilte M3U-Datei: direkt zum Hinzufuegen oeffnen
    LaunchedEffect(sharedM3uUrl) {
        if (sharedM3uUrl != null) nav.navigate(Routes.PLAYLISTS)
    }

    Scaffold(
        bottomBar = {
            Column {
                // Mini-Player: sichtbar solange Wiedergabe laeuft (nicht im Vollbild-Player)
                if (!miniPlayerRoute) {
                    val hubTitle by PlayerHub.title
                    val hubController by PlayerHub.controller
                    if (hubTitle != null && hubController != null) {
                        Surface {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(hubTitle.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = {
                                    val c = hubController ?: return@IconButton
                                    if (c.isPlaying) c.pause() else c.play()
                                }) {
                                    Icon(
                                        if (hubController?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        "Wiedergabe",
                                    )
                                }
                                IconButton(onClick = { PlayerHub.close() }) { Icon(Icons.Filled.Close, "Schliessen") }
                            }
                        }
                    }
                }
                if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.PLAYLISTS, modifier = Modifier.padding(padding)) {
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    sharedUrl = sharedM3uUrl,
                    onOpen = { nav.navigate("detail/$it") },
                    onAddNew = { nav.navigate("detail/$it") },
                    onGlobalSearch = { nav.navigate(tv.ham3l.app.ui.Routes.GLOBAL_SEARCH) },
                )
            }
            composable("detail/{playlistId}") { entry ->
                PlaylistDetailScreen(
                    playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L,
                    onPlayChannel = { nav.navigate("player/$it") },
                    onPlayUrl = { url, title ->
                        val enc = android.net.Uri.encode(url)
                        nav.navigate("player/0?url=$enc")
                        tv.ham3l.app.ui.player.PlayerHub.title.value = title
                    },
                    onOpenVod = { nav.navigate("vod/$it") },
                )
            }
            composable("player/{channelId}?url={url}") { entry ->
                PlayerScreen(
                    channelId = entry.arguments?.getString("channelId")?.toLongOrNull() ?: 0L,
                    streamUrl = entry.arguments?.getString("url")?.let { android.net.Uri.decode(it) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "vod/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                VodScreen(
                    playlistId = entry.arguments?.getLong("playlistId") ?: 0L,
                    onPlayUrl = { url, title ->
                        val enc = android.net.Uri.encode(url)
                        nav.navigate("player/0?url=$enc")
                        tv.ham3l.app.ui.player.PlayerHub.title.value = title
                    },
                )
            }
            composable(Routes.FAVORITES) { FavoritesScreen(onPlay = { nav.navigate("player/$it") }) }
            composable(Routes.HISTORY) { HistoryScreen(onPlay = { nav.navigate("player/$it") }) }
            composable(Routes.SETTINGS) { SettingsScreen(onExportBackup = { scope.launch { M3UExporter.share(nav.context, (nav.context.applicationContext as tv.ham3l.app.Ham3LApp).database) } }) }
            composable(Routes.GLOBAL_SEARCH) { GlobalSearchScreen(onPlay = { nav.navigate("player/$it") }) }
        }
    }
}
