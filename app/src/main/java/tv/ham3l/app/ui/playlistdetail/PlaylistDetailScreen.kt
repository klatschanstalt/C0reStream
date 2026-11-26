package tv.ham3l.app.ui.playlistdetail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.ham3l.app.ui.components.PoweredByFooter
import java.net.URL

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlayChannel: (Long) -> Unit,
    onPlayUrl: (String, String) -> Unit = { _, _ -> },
    onOpenVod: (Long) -> Unit = {},
    vm: PlaylistDetailViewModel = viewModel(key = "pl" + playlistId),
) {
    LaunchedEffect(playlistId) { vm.init(playlistId) }
    val channels by vm.channels.collectAsState(initial = emptyList())
    val groups by vm.groups.collectAsState(initial = emptyList())
    val query by vm.query.collectAsState()
    val group by vm.selectedGroup.collectAsState()
    var guideChannel by remember { mutableStateOf<tv.ham3l.app.data.db.ChannelEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Kanal suchen") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { onOpenVod(playlistId) }) { Text("Filme & Serien") }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = group == null, onClick = { vm.selectGroup(null) }, label = { Text("Alle") }) }
            items(groups) { g ->
                FilterChip(selected = group == g, onClick = { vm.selectGroup(g) }, label = { Text(g, maxLines = 1) })
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(channels, key = { it.id }) { ch ->
                Card(onClick = { onPlayChannel(ch.id) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(ch.logoUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val now by vm.nowFor(playlistId, ch).collectAsState(initial = null)
                            if (now != null) {
                                Text(
                                    "\u25B6 " + now!!.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                ch.group?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                        val fav by vm.isFavorite(ch.id).collectAsState(initial = false)
                        IconButton(onClick = { vm.toggleFavorite(ch) }) {
                            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit")
                        }
                        IconButton(onClick = { guideChannel = ch }) {
                            Icon(Icons.Filled.CalendarMonth, "Programm")
                        }
                    }
                }
            }
            item { PoweredByFooter() }
        }
    }

    // EPG-Guide-Dialog: Tagesprogramm des gewaehlten Senders
    guideChannel?.let { ch ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { guideChannel = null },
            title = { Text("Programm - " + ch.name) },
            text = {
                val entries by vm.guideFor(playlistId, ch).collectAsState(initial = emptyList())
                val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                val now = System.currentTimeMillis()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isEmpty()) {
                        item { Text("Keine EPG-Daten fuer diesen Sender vorhanden.") }
                    }
                    items(entries) { e ->
                        Column {
                            val running = e.start <= now && now < e.stop
                            Text(
                                timeFmt.format(java.util.Date(e.start)) + " - " + timeFmt.format(java.util.Date(e.stop)) + (if (running) "  \u25B6 jetzt" else ""),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            e.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            // Catch-up: vergangene Sendungen ansehen, wenn der Sender Aufzeichnungen liefert
                            val catchupUrl = if (!running && ch.catchupSource != null && e.stop < now) {
                                tv.ham3l.app.data.Catchup.build(ch.catchupSource, e.start, e.stop, now)
                            } else null
                            catchupUrl?.let { cu ->
                                TextButton(onClick = {
                                    guideChannel = null
                                    onPlayUrl(cu, ch.name + " - " + e.title)
                                }) { Text("Ansehen (Catch-up)") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { guideChannel = null }) { Text("Schliessen") }
            },
        )
    }
}

// Einfacher async Logo-Loader ohne externe Coil-Abhaengigkeit
@Composable
fun ChannelLogo(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeStream(URL(url).openStream())
            }.onSuccess { bitmap = it }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("TV", style = MaterialTheme.typography.labelSmall)
        }
    }
}
