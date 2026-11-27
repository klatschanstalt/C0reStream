package pw.c0re.stream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.ui.backup.M3UExporter
import pw.c0re.stream.ui.favorites.FavoritesScreen
import pw.c0re.stream.ui.globalsearch.GlobalSearchScreen
import pw.c0re.stream.ui.history.HistoryScreen
import pw.c0re.stream.ui.player.PlayerHub
import pw.c0re.stream.ui.player.PlayerScreen
import pw.c0re.stream.ui.playlistdetail.PlaylistDetailScreen
import pw.c0re.stream.ui.playlists.PlaylistsScreen
import pw.c0re.stream.ui.settings.SettingsScreen
import pw.c0re.stream.ui.vod.VodScreen

object Routes {
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GLOBAL_SEARCH = "globalsearch"
    const val PLAYER = "player/{channelId}"
    fun player(channelId: Long): String = "player/$channelId"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun C0reStreamRoot(sharedM3uUrl: String?) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as C0reStreamApp
    val tabs = listOf(
        Tab(Routes.PLAYLISTS, R.string.nav_live, Icons.Filled.LiveTv),
        Tab(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
        Tab(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
        Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )
    val backStack = nav.currentBackStackEntryAsState()
    val current = backStack.value?.destination?.route
    val topLevel = tabs.any { it.route == current }
    val playerRoute = current?.startsWith("player/") == true

    LaunchedEffect(sharedM3uUrl) {
        if (sharedM3uUrl != null && current != Routes.PLAYLISTS) {
            nav.navigate(Routes.PLAYLISTS) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun selectTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            bottomBar = {
                Column {
                    if (!playerRoute) {
                        MiniPlayer(onExpand = {
                            nav.navigate(Routes.player(PlayerHub.channelId))
                        })
                    }
                    if (topLevel && !wide) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = current == tab.route,
                                    onClick = { selectTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                    label = { Text(stringResource(tab.labelRes)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = current == tab.route,
                                onClick = { selectTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
                NavHost(
                    navController = nav,
                    startDestination = Routes.PLAYLISTS,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(Routes.PLAYLISTS) {
                        PlaylistsScreen(
                            sharedUrl = sharedM3uUrl,
                            onOpen = { nav.navigate("detail/$it") },
                            onAddNew = { nav.navigate("detail/$it") },
                            onGlobalSearch = { nav.navigate(Routes.GLOBAL_SEARCH) },
                        )
                    }
                    composable("detail/{playlistId}") { entry ->
                        PlaylistDetailScreen(
                            playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L,
                            onPlayChannel = { nav.navigate(Routes.player(it)) },
                            onPlayUrl = { url, title ->
                                PlayerHub.prepare(url, title, null, "live", 0L)
                                nav.navigate(Routes.player(0L))
                            },
                            onOpenVod = { nav.navigate("vod/$it") },
                        )
                    }
                    composable(Routes.PLAYER) { entry ->
                        PlayerScreen(
                            channelId = entry.arguments?.getString("channelId")?.toLongOrNull() ?: 0L,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("vod/{playlistId}") { entry ->
                        val playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                        VodScreen(
                            playlistId = playlistId,
                            onPlayMedia = { url, title, mediaKey, kind ->
                                PlayerHub.prepare(url, title, mediaKey, kind, playlistId)
                                nav.navigate(Routes.player(0L))
                            },
                        )
                    }
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(onPlay = { nav.navigate(Routes.player(it)) })
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(onPlay = { nav.navigate(Routes.player(it)) })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onExportBackup = {
                                scope.launch { M3UExporter.share(context, app.database) }
                            },
                        )
                    }
                    composable(Routes.GLOBAL_SEARCH) {
                        GlobalSearchScreen(onPlay = { nav.navigate(Routes.player(it)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(onExpand: () -> Unit) {
    val title = PlayerHub.title
    val controller = PlayerHub.controller
    val playing = PlayerHub.isPlaying
    if (title == null || controller == null) return
    Surface {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            IconButton(onClick = {
                if (controller.isPlaying) controller.pause() else controller.play()
            }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (playing) R.string.action_pause else R.string.action_play),
                )
            }
            IconButton(onClick = { PlayerHub.close() }) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close))
            }
        }
    }
}
