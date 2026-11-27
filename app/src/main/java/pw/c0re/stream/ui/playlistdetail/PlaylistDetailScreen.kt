package pw.c0re.stream.ui.playlistdetail

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import pw.c0re.stream.R
import pw.c0re.stream.data.Catchup
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.readBytes
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.model.EpgEntry
import pw.c0re.stream.ui.components.PoweredByFooter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlayChannel: (Long) -> Unit,
    onPlayUrl: (String, String) -> Unit = { _, _ -> },
    onOpenVod: (Long) -> Unit = {},
    vm: PlaylistDetailViewModel = viewModel(key = "pl$playlistId"),
) {
    LaunchedEffect(playlistId) { vm.init(playlistId) }
    val channels by vm.channels.collectAsState(initial = emptyList())
    val groups by vm.groups.collectAsState(initial = emptyList())
    val query by vm.query.collectAsState()
    val group by vm.selectedGroup.collectAsState()
    var guideChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.field_search_channel)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { onOpenVod(playlistId) }) {
                Text(stringResource(R.string.detail_movies_series))
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = group == null,
                    onClick = { vm.selectGroup(null) },
                    label = { Text(stringResource(R.string.detail_all)) },
                )
            }
            items(groups) { value ->
                FilterChip(
                    selected = group == value,
                    onClick = { vm.selectGroup(value) },
                    label = { Text(value, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(channels, key = { it.id }) { channel ->
                Card(onClick = { onPlayChannel(channel.id) }, shape = RoundedCornerShape(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(
                            channel.logoUrl,
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val now by vm.nowFor(playlistId, channel).collectAsState(initial = null)
                            if (now != null) {
                                Text(
                                    "\u25B6 " + now!!.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                channel.group?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        val favorite by vm.isFavorite(channel.id).collectAsState(initial = false)
                        IconButton(onClick = { vm.toggleFavorite(channel) }) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.content_desc_favorite),
                            )
                        }
                        IconButton(onClick = { guideChannel = channel }) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                stringResource(R.string.detail_program),
                            )
                        }
                    }
                }
            }
            item { PoweredByFooter() }
        }
    }

    guideChannel?.let { channel ->
        GuideDialog(
            playlistId = playlistId,
            channel = channel,
            vm = vm,
            onDismiss = { guideChannel = null },
            onPlayUrl = { url, title ->
                guideChannel = null
                onPlayUrl(url, title)
            },
        )
    }
}

@Composable
private fun GuideDialog(
    playlistId: Long,
    channel: ChannelEntity,
    vm: PlaylistDetailViewModel,
    onDismiss: () -> Unit,
    onPlayUrl: (String, String) -> Unit,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val entries by produceState(initialValue = emptyList<EpgEntry>(), channel.id, refreshKey) {
        value = vm.guideFor(playlistId, channel).first()
    }
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val now = remember(entries) { System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.epg_title, channel.name),
                    Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = {
                    vm.refreshGuide(playlistId)
                    refreshKey++
                }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty()) {
                    item { Text(stringResource(R.string.epg_empty)) }
                }
                items(entries) { entry ->
                    EpgRow(
                        channel = channel,
                        entry = entry,
                        running = entry.start <= now && now < entry.stop,
                        formatter = formatter,
                        now = now,
                        vm = vm,
                        onPlayUrl = onPlayUrl,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun EpgRow(
    channel: ChannelEntity,
    entry: EpgEntry,
    running: Boolean,
    formatter: SimpleDateFormat,
    now: Long,
    vm: PlaylistDetailViewModel,
    onPlayUrl: (String, String) -> Unit,
) {
    var reminderSet by remember(entry.start) { mutableStateOf(false) }
    LaunchedEffect(entry.start) {
        reminderSet = vm.hasReminder(channel.id, entry.start).first()
    }
    Column {
        Text(
            formatter.format(Date(entry.start)) + " - " + formatter.format(Date(entry.stop)) +
                if (running) "  \u25B6 " + stringResource(R.string.epg_now) else "",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(entry.title, style = MaterialTheme.typography.titleMedium)
        entry.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.start > now) {
                TextButton(onClick = {
                    vm.toggleReminder(channel, entry)
                    reminderSet = !reminderSet
                }) {
                    Text(
                        stringResource(
                            if (reminderSet) R.string.epg_reminder_set else R.string.epg_reminder,
                        ),
                    )
                }
            }
            val catchupSource = channel.catchupSource
            val catchupUrl = if (!running && entry.stop < now && catchupSource != null) {
                Catchup.build(catchupSource, entry.start, entry.stop, now)
            } else {
                null
            }
            catchupUrl?.let { url ->
                TextButton(onClick = { onPlayUrl(url, channel.name + " - " + entry.title) }) {
                    Text(stringResource(R.string.catchup_watch))
                }
            }
        }
    }
}

/** Bounded logo loader; rejects non-HTTP(S) URLs and oversized responses. */
@Composable
fun ChannelLogo(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || NetworkPolicy.parse(url) == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                NetworkPolicy.client().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bytes = response.readBytes(4L * 1024 * 1024)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.onSuccess { bitmap = it }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_tv), style = MaterialTheme.typography.labelSmall)
        }
    }
}
