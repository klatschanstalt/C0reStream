package pw.c0re.stream.ui.playlists

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.R
import pw.c0re.stream.ui.components.C0reStreamWordmark
import pw.c0re.stream.ui.components.PoweredByFooter

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
    var showAdd by remember { mutableStateOf(sharedUrl != null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) showAdd = true
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, onAddNew)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add_playlist))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            C0reStreamWordmark()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.playlists_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onGlobalSearch) {
                    Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                }
                TextButton(onClick = { importFile.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.action_import_file))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlists_empty), style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { message ->
                Text(
                    stringResource(R.string.error_playlist_load) + ": " + message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(onClick = { onOpen(playlist.id) }, shape = RoundedCornerShape(18.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    pluralStringResource(
                                        R.plurals.channels_count,
                                        playlist.channelCount,
                                        playlist.channelCount,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            IconButton(onClick = { vm.refresh(playlist.id) }) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                            }
                            IconButton(onClick = { deleteId = playlist.id }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
                item { PoweredByFooter() }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    deleteId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
    var xtreamPass by remember { mutableStateOf("") }

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
                    OutlinedTextField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it.take(512) },
                        label = { Text(stringResource(R.string.field_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
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
            TextButton(enabled = canConfirm, onClick = {
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
            }) { Text(stringResource(R.string.action_add_playlist)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
