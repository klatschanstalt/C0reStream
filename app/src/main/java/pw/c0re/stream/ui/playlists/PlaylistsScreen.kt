package pw.c0re.stream.ui.playlists

import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.R
import pw.c0re.stream.data.ImportStats
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.profiles.QrPlaylistParser
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandEmptyState
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandSectionTitle
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.C0reStreamWordmark
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.components.rememberResponsivePadding

@Composable
fun PlaylistsScreen(
    sharedUrl: String?,
    onOpen: (Long) -> Unit,
    onAddNew: (Long) -> Unit,
    onGlobalSearch: () -> Unit = {},
    vm: PlaylistsViewModel = viewModel(),
) {
    val playlists by vm.playlists.collectAsState()
    val error by vm.lastError
    val importResult by vm.lastImport
    var showAdd by remember { mutableStateOf(sharedUrl != null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var qrError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val qrPlaylistName = stringResource(R.string.playlist_qr_name)
    val horizontalPadding = rememberResponsivePadding()

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) showAdd = true
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, onAddNew)
    }
    val importQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        qrError = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val url = withContext(Dispatchers.Default) {
                val bitmap = runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                }.getOrNull()
                bitmap?.let { QrPlaylistParser.parse(it) }
            }
            if (url == null) {
                qrError = true
            } else {
                vm.add(qrPlaylistName, url, null, null, false, null, null, null, onAddNew)
            }
        }
    }

    BrandBackdrop(Modifier.fillMaxSize()) {
        Scaffold(
            // The root Scaffold already consumed system bars; avoid applying them twice.
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.action_add_playlist))
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
            ) {
                C0reStreamWordmark()
                Spacer(Modifier.height(12.dp))
                BrandTopBar(
                    title = stringResource(R.string.playlists_title),
                    actions = {
                        IconButton(onClick = onGlobalSearch) {
                            Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                        }
                        IconButton(onClick = { importQr.launch("image/*") }) {
                            Icon(Icons.Filled.QrCode, stringResource(R.string.qr_import))
                        }
                        BrandSecondaryButton(
                            stringResource(R.string.action_import_file),
                            onClick = {
                                importFile.launch(
                                    arrayOf(
                                        "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl",
                                        "application/vnd.apple.mpegurl", "application/octet-stream",
                                        "text/plain", "application/gzip", "application/x-gzip", "*/*",
                                    ),
                                )
                            },
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                BrandSectionTitle(stringResource(R.string.nav_live))
                Spacer(Modifier.height(8.dp))

                if (playlists.isEmpty()) {
                    BrandEmptyState(stringResource(R.string.playlists_empty))
                }
                error?.let { message ->
                    Text(
                        stringResource(R.string.error_playlist_load) + ": " + message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (qrError) {
                    Text(stringResource(R.string.qr_invalid), color = MaterialTheme.colorScheme.error)
                }
                importResult?.let { result ->
                    ImportStatsCard(result.stats)
                }

                AdaptivePane(
                    modifier = Modifier.weight(1f),
                    compact = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onOpen = onOpen,
                                    onRefresh = { vm.refresh(playlist.id) },
                                    onDelete = { deleteId = playlist.id },
                                )
                            }
                            item { PoweredByFooter() }
                        }
                    },
                    medium = { WidePlaylists(playlists, onOpen, vm, onDelete = { deleteId = it }) },
                    expanded = { WidePlaylists(playlists, onOpen, vm, onDelete = { deleteId = it }) },
                )
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message)) },
            confirmButton = {
                BrandPrimaryButton(stringResource(R.string.action_delete), onClick = {
                    vm.delete(id)
                    deleteId = null
                })
            },
            dismissButton = {
                BrandSecondaryButton(stringResource(R.string.action_cancel), onClick = { deleteId = null })
            },
        )
    }

    if (showAdd) {
        AddPlaylistDialog(
            initialUrl = sharedUrl,
            onDismiss = {
                showAdd = false
                vm.clearError()
            },
            onConfirm = { name, url, epg, ua, proxy, host, port, xtream ->
                vm.add(name, url, epg, ua, proxy, host, port, xtream, onAddNew)
                showAdd = false
            },
        )
    }
}

@Composable
private fun WidePlaylists(
    playlists: List<PlaylistEntity>,
    onOpen: (Long) -> Unit,
    vm: PlaylistsViewModel,
    onDelete: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onOpen = onOpen,
                onRefresh = { vm.refresh(playlist.id) },
                onDelete = { onDelete(playlist.id) },
            )
        }
        item { PoweredByFooter() }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    onOpen: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    BrandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(playlist.id) },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BrandBadge(
                    pluralStringResource(
                        R.plurals.channels_count,
                        playlist.channelCount,
                        playlist.channelCount,
                    ),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun ImportStatsCard(stats: ImportStats) {
    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.import_received, stats.received))
            Text(stringResource(R.string.import_imported, stats.accepted))
            Text(stringResource(R.string.import_merged, stats.merged))
            Text(stringResource(R.string.import_rejected, stats.rejected))
            if (stats.truncated) BrandBadge(stringResource(R.string.import_limit))
            if (stats.rejections.any { it.code == "SHRINK_GUARD" }) {
                BrandBadge(stringResource(R.string.import_shrink_guard))
            }
            stats.rejections.take(4).forEach { rejection ->
                Text(reasonLabel(rejection.code), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

data class XtreamInput(
    val enabled: Boolean,
    val host: String,
    val username: String,
    val password: String,
)

@Composable
private fun AddPlaylistDialog(
    initialUrl: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, Boolean, String?, Int?, XtreamInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var epg by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var useProxy by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var useXtream by remember { mutableStateOf(false) }
    var xtreamHost by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    val passwordState = rememberTextFieldState()
    val xtreamPass by remember { derivedStateOf { passwordState.text.toString() } }

    val canConfirm = name.isNotBlank() && if (useXtream) {
        xtreamHost.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotEmpty()
    } else {
        url.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(128) },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_xtream_login), Modifier.weight(1f))
                    Switch(checked = useXtream, onCheckedChange = { useXtream = it })
                }
                if (useXtream) {
                    Text(stringResource(R.string.xtream_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = xtreamHost,
                        onValueChange = { xtreamHost = it.trim() },
                        label = { Text(stringResource(R.string.field_server)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it.trim().take(256) },
                        label = { Text(stringResource(R.string.field_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedSecureTextField(
                        state = passwordState,
                        inputTransformation = InputTransformation.maxLengthReject(512),
                        label = { Text(stringResource(R.string.field_password)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.trim() },
                        label = { Text(stringResource(R.string.field_m3u_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it.trim() },
                    label = { Text(stringResource(R.string.field_epg_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it.take(512) },
                    label = { Text(stringResource(R.string.field_user_agent)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_proxy), Modifier.weight(1f))
                    Switch(checked = useProxy, onCheckedChange = { useProxy = it })
                }
                if (useProxy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it.trim().take(255) },
                            label = { Text(stringResource(R.string.field_proxy_host)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.field_proxy_port)) },
                            modifier = Modifier.weight(0.6f),
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            BrandPrimaryButton(
                stringResource(R.string.action_add_playlist),
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        name.trim(),
                        url.trim(),
                        epg.trim().ifBlank { null },
                        userAgent.trim().ifBlank { null },
                        useProxy,
                        proxyHost.trim().ifBlank { null },
                        proxyPort.toIntOrNull(),
                        XtreamInput(useXtream, xtreamHost.trim(), xtreamUser.trim(), xtreamPass),
                    )
                },
            )
        },
        dismissButton = {
            BrandSecondaryButton(stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun reasonLabel(code: String): String = stringResource(
    when (code) {
        "PLAYLIST_TOO_LARGE" -> R.string.reason_playlist_too_large
        "TOO_MANY_LINES" -> R.string.reason_too_many_lines
        "LINE_TOO_LONG" -> R.string.reason_line_too_long
        "TOO_MANY_CHANNELS" -> R.string.reason_too_many_channels
        "INVALID_STREAM_URL" -> R.string.reason_invalid_stream_url
        "GZIP_DECODE_FAILED" -> R.string.reason_gzip_decode_failed
        "GZIP_TOO_LARGE" -> R.string.reason_gzip_too_large
        "SHRINK_GUARD" -> R.string.reason_shrink_guard
        "DUPLICATE_KEY_COLLISION" -> R.string.reason_duplicate_collision
        "XTREAM_RECORD_LIMIT" -> R.string.reason_xtream_record_limit
        "XTREAM_PAGINATION_UNSUPPORTED" -> R.string.reason_xtream_pagination
        "XTREAM_CATEGORIES_UNAVAILABLE" -> R.string.reason_xtream_categories
        "XTREAM_INVALID_RECORD" -> R.string.reason_xtream_invalid_record
        else -> R.string.error_playlist_load
    },
)
