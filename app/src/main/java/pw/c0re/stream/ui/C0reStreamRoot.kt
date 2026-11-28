package pw.c0re.stream.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.profiles.ActiveProfile
import pw.c0re.stream.data.profiles.PinFailure
import pw.c0re.stream.data.profiles.ProfileConfigEvents
import pw.c0re.stream.data.profiles.ProfileGate
import pw.c0re.stream.data.profiles.ProfileGatePhase
import pw.c0re.stream.data.profiles.ProfileGateState
import pw.c0re.stream.data.profiles.ProfileSessionRepository
import pw.c0re.stream.data.profiles.RoomProfilePinStore
import pw.c0re.stream.ui.backup.M3UExporter
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.C0reStreamWordmark
import pw.c0re.stream.ui.favorites.FavoritesScreen
import pw.c0re.stream.ui.globalsearch.GlobalSearchScreen
import pw.c0re.stream.ui.history.HistoryScreen
import pw.c0re.stream.ui.player.PlayerHub
import pw.c0re.stream.ui.player.PlayerScreen
import pw.c0re.stream.ui.playlistdetail.PlaylistDetailScreen
import pw.c0re.stream.ui.playlists.PlaylistsScreen
import pw.c0re.stream.ui.profiles.ProfilesScreen
import pw.c0re.stream.ui.settings.SettingsScreen
import pw.c0re.stream.ui.vod.VodScreen

object Routes {
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GLOBAL_SEARCH = "globalsearch"
    const val PROFILES = "profiles"
    const val PLAYER = "player/{channelId}"
    fun player(channelId: Long): String = "player/$channelId"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

/**
 * Owns the lock state for the whole app. The gate is closed (Loading/Locked/Error) until the
 * active profile, its PIN configuration and the unlock result are known, so no protected UI is
 * composed and no protected route is reachable during cold start, process restart or a switch.
 */
class ProfileGateViewModel(app: Application) : AndroidViewModel(app) {
    private val session = ProfileSessionRepository(app)
    private val gate = ProfileGate(
        store = RoomProfilePinStore((app as C0reStreamApp).database.profileDao()),
        pins = ProfileSessionRepository.pinManager,
    )
    val state: StateFlow<ProfileGateState> = gate.state

    init {
        viewModelScope.launch {
            session.activeProfile.collect { active ->
                gate.observeActive(active)
                gate.evaluate()
            }
        }
        // A PIN set/changed on the active profile must take effect immediately, even though the
        // active profile id itself did not change.
        viewModelScope.launch {
            ProfileConfigEvents.changes.collect { profileId ->
                if (session.activeProfile.first() == ActiveProfile.Selected(profileId)) {
                    gate.retry()
                }
            }
        }
    }

    fun submitPin(pin: String) = viewModelScope.launch {
        // Re-read the selection before accepting input: a switch may have landed meanwhile.
        val latest = session.activeProfile.first()
        gate.observeActive(latest)
        val chars = pin.toCharArray()
        try {
            gate.unlock(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun retry() = viewModelScope.launch { gate.retry() }
}

@Composable
fun C0reStreamRoot(sharedM3uUrl: String?) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as C0reStreamApp
    val gateViewModel: ProfileGateViewModel = viewModel()
    val gate by gateViewModel.state.collectAsState()
    // Closed is the default: no content and no route until the business logic says UNLOCKED.
    val unlocked = gate.phase == ProfileGatePhase.UNLOCKED

    // Locked sessions pause playback but keep the MediaSession and position; nothing auto-resumes.
    // The mini-player, full player and nav bars are not composed while the gate is closed.
    LaunchedEffect(unlocked) {
        if (!unlocked) runCatching { PlayerHub.controller?.pause() }
    }
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

    // External intents are only handled after the gate opens; while closed there is no NavHost
    // and therefore no reachable protected route or restored navigation state.
    LaunchedEffect(sharedM3uUrl, unlocked) {
        if (!unlocked || sharedM3uUrl == null) return@LaunchedEffect
        if (nav.currentDestination?.route != Routes.PLAYLISTS) {
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

    // A single NavHost is shared by every width class so the back stack, saved state,
    // view models and all callbacks survive a window resize or a fold/unfold.
    val navHost: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(
            navController = nav,
            startDestination = Routes.PLAYLISTS,
            modifier = hostModifier,
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
                    onOpenProfiles = { nav.navigate(Routes.PROFILES) },
                )
            }
            composable(Routes.GLOBAL_SEARCH) {
                GlobalSearchScreen(onPlay = { nav.navigate(Routes.player(it)) })
            }
            composable(Routes.PROFILES) {
                ProfilesScreen(onDone = { nav.popBackStack() })
            }
        }
    }

    val railItems: @Composable () -> Unit = {
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = current == tab.route,
                onClick = { selectTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }

    val drawerItems: @Composable () -> Unit = {
        tabs.forEach { tab ->
            NavigationDrawerItem(
                selected = current == tab.route,
                onClick = { selectTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    when (gate.phase) {
        ProfileGatePhase.LOADING -> ProfileGateLoading()
        ProfileGatePhase.LOCKED -> PinGate(state = gate, onSubmit = { gateViewModel.submitPin(it) })
        ProfileGatePhase.ERROR -> ProfileGateError(onRetry = { gateViewModel.retry() })
        ProfileGatePhase.UNLOCKED -> {
            BrandBackdrop(Modifier.fillMaxSize()) {
            Scaffold(
                // Explicit, single source of truth for safe areas. Scaffold distributes these
                // insets to the top/bottom bars and the content; no manual unions are needed.
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.systemBars,
                bottomBar = {
                    Column {
                        if (!playerRoute) {
                            MiniPlayer(onExpand = {
                                nav.navigate(Routes.player(PlayerHub.channelId))
                            })
                        }
                        AdaptivePane(
                            compact = {
                                if (topLevel) {
                                    NavigationBar {
                                        tabs.forEach { tab ->
                                            NavigationBarItem(
                                                selected = current == tab.route,
                                                onClick = { selectTab(tab.route) },
                                                icon = {
                                                    Icon(
                                                        tab.icon,
                                                        contentDescription = stringResource(tab.labelRes),
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes)) },
                                            )
                                        }
                                    }
                                }
                            },
                            medium = {},
                            expanded = {},
                        )
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    AdaptivePane(
                        modifier = Modifier.weight(1f),
                        compact = { navHost(Modifier.fillMaxSize()) },
                        medium = {
                            Row(Modifier.fillMaxSize()) {
                                NavigationRail(
                                    header = {
                                        C0reStreamWordmark(compact = true)
                                        Spacer(Modifier.height(12.dp))
                                    },
                                    content = { railItems() },
                                )
                                navHost(Modifier.weight(1f))
                            }
                        },
                        expanded = {
                            // Wide windows get a dedicated navigation pane. The remaining pane
                            // is a single NavHost, ready for a future secondary detail pane.
                            PermanentNavigationDrawer(
                                modifier = Modifier.fillMaxSize(),
                                drawerContent = {
                                    PermanentDrawerSheet {
                                        Column(Modifier.padding(vertical = 16.dp)) {
                                            C0reStreamWordmark(compact = true)
                                            Spacer(Modifier.height(16.dp))
                                            drawerItems()
                                        }
                                    }
                                },
                            ) {
                                navHost(Modifier.fillMaxSize())
                            }
                        },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ProfileGateLoading() {
    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.pin_gate_checking), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProfileGateError(onRetry: () -> Unit) {
    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.pin_gate_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            BrandPrimaryButton(stringResource(R.string.pin_gate_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun PinGate(state: ProfileGateState, onSubmit: (String) -> Unit) {
    val pinState = rememberTextFieldState()
    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.profiles_pin_prompt), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedSecureTextField(
                state = pinState,
                inputTransformation = InputTransformation.maxLengthReject(64)
                    .byValue { input, _ -> input.filter(Char::isDigit) },
                label = { Text(state.profileName ?: stringResource(R.string.profiles_title)) },
            )
            state.failure?.let { failure ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (failure) {
                            PinFailure.WRONG_CURRENT, PinFailure.MISMATCHED_NEW, PinFailure.INVALID_FORMAT ->
                                R.string.profiles_pin_wrong
                            PinFailure.TOO_MANY_ATTEMPTS -> R.string.pin_error_locked
                            else -> R.string.pin_gate_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            BrandPrimaryButton(stringResource(R.string.action_confirm), onClick = {
                val value = pinState.text.toString()
                pinState.clearText()
                onSubmit(value)
            })
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
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
