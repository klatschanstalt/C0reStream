package tv.ham3l.app.ui.playlists

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.ham3l.app.ui.components.Ham3LWordmark
import tv.ham3l.app.ui.components.PoweredByFooter

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

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Playlist hinzufuegen") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(20.dp)) {
            Ham3LWordmark()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Deine Playlists", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onGlobalSearch) { Text("Alle Kanaele durchsuchen") }
            }
            Spacer(Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    "Noch keine Playlist. Tippe auf + und fuege eine M3U-URL hinzu.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(playlists, key = { it.id }) { pl ->
                    Card(onClick = { onOpen(pl.id) }, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pl.channelCount.toString() + " Kanaele", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { vm.refresh(pl.id) }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                            IconButton(onClick = { vm.delete(pl.id) }) { Icon(Icons.Filled.Delete, "Loeschen") }
                        }
                    }
                }
                item { PoweredByFooter() }
            }
        }
    }

    if (showAdd) AddPlaylistDialog(
        initialUrl = sharedUrl,
        onDismiss = { showAdd = false },
        onConfirm = { name, url, epg, ua, proxy, host, port, xtream ->
            vm.add(name, url, epg, ua, proxy, host, port, xtream, onAddNew)
            showAdd = false
        },
    )
}

data class XtreamInput(val enabled: Boolean, val host: String, val username: String, val password: String)

@Composable
private fun AddPlaylistDialog(
    initialUrl: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, Boolean, String?, Int?, XtreamInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var epg by remember { mutableStateOf("") }
    var ua by remember { mutableStateOf("") }
    var useProxy by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var useXtream by remember { mutableStateOf(false) }
    var xHost by remember { mutableStateOf("") }
    var xUser by remember { mutableStateOf("") }
    var xPass by remember { mutableStateOf("") }

    val canConfirm = if (useXtream) {
        xHost.isNotBlank() && xUser.isNotBlank() && name.isNotBlank()
    } else {
        url.isNotBlank() && name.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Xtream-Codes-Login", Modifier.weight(1f))
                    Switch(useXtream, { useXtream = it })
                }
                if (useXtream) {
                    OutlinedTextField(xHost, { xHost = it }, label = { Text("Server (http://host:port)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(xUser, { xUser = it }, label = { Text("Benutzername") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(xPass, { xPass = it }, label = { Text("Passwort") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(url, { url = it }, label = { Text("M3U-URL") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(epg, { epg = it }, label = { Text("EPG-URL (optional, XMLTV)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ua, { ua = it }, label = { Text("User-Agent (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Proxy verwenden", Modifier.weight(1f))
                    Switch(useProxy, { useProxy = it })
                }
                if (useProxy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.weight(0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val xt = XtreamInput(useXtream, xHost.trim(), xUser.trim(), xPass)
                    onConfirm(name.trim(), url.trim(), epg.trim().ifBlank { null }, ua.trim().ifBlank { null }, useProxy, host.trim().ifBlank { null }, port.toIntOrNull(), xt)
                },
            ) { Text("Hinzufuegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
