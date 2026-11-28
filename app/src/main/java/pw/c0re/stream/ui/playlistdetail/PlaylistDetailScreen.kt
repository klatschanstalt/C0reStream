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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.gridItems
import pw.c0re.stream.ui.components.listItems
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandEmptyState
import pw.c0re.stream.ui.components.BrandLoading
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.components.rememberResponsivePadding
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlayChannel: (Long) -> Unit,
    onPlayUrl: (String, String) -> Unit = { _, _ -> },
    onOpenVod: (Long) -> Unit = {},
    vm: PlaylistDetailViewModel = viewModel(key = "pl$playlistId"),
) {
    LaunchedEffect(playlistId) { vm.init(playlistId) }
    val channels = vm.channels.collectAsLazyPagingItems()
    val groups by vm.groups.collectAsState(initial = emptyList())
    val totalCount by vm.totalCount.collectAsState()
    val query by vm.query.collectAsState()
    val group by vm.selectedGroup.collectAsState()
    var guideChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    val horizontalPadding = rememberResponsivePadding()

    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.nav_live),
                actions = {
                    BrandBadge(totalCount.toString())
                    BrandSecondaryButton(
                        stringResource(R.string.detail_movies_series),
                        onClick = { onOpenVod(playlistId) },
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                },
                placeholder = { Text(stringResource(R.string.field_search_channel)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = group == null,
                        onClick = { vm.selectGroup(null) },
                        label = { Text(stringResource(R.string.detail_all)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                listItems(groups, key = { it }) { value ->
                    FilterChip(
                        selected = group == value,
                        onClick = { vm.selectGroup(value) },
                        label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            AdaptivePane(
                modifier = Modifier.weight(1f),
                compact = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listItems(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                            channels[index]?.let { channel ->
                                ChannelCard(channel, playlistId, vm, onPlayChannel) { guideChannel = it }
                            }
                        }
                        pagingStatusItems(channels)
                        item(key = "footer") { PoweredByFooter() }
                    }
                },
                medium = { ChannelGrid(channels, playlistId, vm, onPlayChannel) { guideChannel = it } },
                expanded = { ChannelGrid(channels, playlistId, vm, onPlayChannel) { guideChannel = it } },
            )
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

/** Refresh/append loading and error rows shared by both paged channel layouts. */
internal fun LazyListScope.pagingStatusItems(
    items: LazyPagingItems<*>,
    emptyMessage: String? = null,
) {
    when (val refresh = items.loadState.refresh) {
        is LoadState.Loading -> item(key = "refresh_loading") { BrandLoading() }
        is LoadState.Error -> item(key = "refresh_error") { PagingErrorRow(refresh) { items.retry() } }
        is LoadState.NotLoading -> {
            if (emptyMessage != null && items.itemCount == 0) {
                item(key = "paging_empty") { BrandEmptyState(emptyMessage) }
            }
        }
    }
    when (val append = items.loadState.append) {
        is LoadState.Loading -> item(key = "append_loading") { BrandLoading() }
        is LoadState.Error -> item(key = "append_error") { PagingErrorRow(append) { items.retry() } }
        is LoadState.NotLoading -> Unit
    }
}

/** Grid counterpart of [pagingStatusItems]. */
internal fun LazyGridScope.pagingStatusItems(
    items: LazyPagingItems<*>,
    emptyMessage: String? = null,
) {
    when (val refresh = items.loadState.refresh) {
        is LoadState.Loading -> gridItems(count = 1, key = { "refresh_loading" }) { BrandLoading() }
        is LoadState.Error -> gridItems(count = 1, key = { "refresh_error" }) {
            PagingErrorRow(refresh) { items.retry() }
        }
        is LoadState.NotLoading -> {
            if (emptyMessage != null && items.itemCount == 0) {
                gridItems(count = 1, key = { "paging_empty" }) { BrandEmptyState(emptyMessage) }
            }
        }
    }
    when (val append = items.loadState.append) {
        is LoadState.Loading -> gridItems(count = 1, key = { "append_loading" }) { BrandLoading() }
        is LoadState.Error -> gridItems(count = 1, key = { "append_error" }) {
            PagingErrorRow(append) { items.retry() }
        }
        is LoadState.NotLoading -> Unit
    }
}

@Composable
internal fun PagingErrorRow(error: LoadState.Error, retry: () -> Unit) {
    BrandEmptyState(
        message = error.error.localizedMessage ?: stringResource(R.string.error_playlist_load),
        actionLabel = stringResource(R.string.action_retry),
        onAction = retry,
    )
}

@Composable
private fun ChannelGrid(
    channels: LazyPagingItems<ChannelEntity>,
    playlistId: Long,
    vm: PlaylistDetailViewModel,
    onPlayChannel: (Long) -> Unit,
    onGuide: (ChannelEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
            channels[index]?.let { channel ->
                ChannelCard(channel, playlistId, vm, onPlayChannel, onGuide)
            }
        }
        pagingStatusItems(channels)
        gridItems(count = 1, key = { "footer" }) { PoweredByFooter() }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelEntity,
    playlistId: Long,
    vm: PlaylistDetailViewModel,
    onPlayChannel: (Long) -> Unit,
    onGuide: (ChannelEntity) -> Unit,
) {
    BrandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onPlayChannel(channel.id) },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel.logoUrl,
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
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
            IconButton(onClick = { onGuide(channel) }) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    stringResource(R.string.detail_program),
                )
            }
        }
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
    var refreshRequest by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val entries by produceState(initialValue = emptyList<EpgEntry>(), channel.id, refreshKey) {
        value = vm.guideFor(playlistId, channel).first()
    }
    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) return@LaunchedEffect
        refreshing = true
        try {
            vm.refreshGuide(playlistId)
        } finally {
            refreshKey++
            refreshing = false
        }
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
                IconButton(onClick = { refreshRequest++ }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                }
            }
        },
        text = {
            when {
                refreshing -> BrandLoading()
                entries.isEmpty() -> BrandEmptyState(stringResource(R.string.epg_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listItems(entries, key = { "${it.start}-${it.stop}-${it.title}" }) { entry ->
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
            }
        },
        confirmButton = {
            BrandSecondaryButton(stringResource(R.string.action_close), onClick = onDismiss)
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
    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            formatter.format(Date(entry.start)) + " - " + formatter.format(Date(entry.stop)) +
                if (running) "  \u25B6 " + stringResource(R.string.epg_now) else "",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            entry.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
                BrandPrimaryButton(
                    stringResource(
                        if (reminderSet) R.string.epg_reminder_set else R.string.epg_reminder,
                    ),
                    onClick = {
                        vm.toggleReminder(channel, entry)
                        reminderSet = !reminderSet
                    },
                )
            }
            val catchupSource = channel.catchupSource
            val catchupUrl = if (!running && entry.stop < now && catchupSource != null) {
                Catchup.build(catchupSource, entry.start, entry.stop, now)
            } else {
                null
            }
            catchupUrl?.let { url ->
                BrandSecondaryButton(
                    stringResource(R.string.catchup_watch),
                    onClick = { onPlayUrl(url, channel.name + " - " + entry.title) },
                )
            }
        }
    }
}

/**
 * Pure bounds/sampling helpers shared by QR and logo decoding. Compressed byte size alone is not
 * a memory guard: a tiny image can declare enormous dimensions. Every decoder first reads bounds
 * (or the header) and downscales before any full-size allocation happens.
 */
object SafeImageDecode {
    const val MAX_COMPRESSED_BYTES = 8L * 1024 * 1024
    const val MAX_DECODED_DIMENSION = 2_048
    const val MAX_DECODED_PIXELS = 2_000_000L
    const val MAX_DECODED_MEMORY_BYTES = 32L * 1024 * 1024
    const val MAX_SAMPLE_SIZE = 1_024
    /** A 48 dp logo at 4x density; decoding beyond this only wastes memory. */
    const val LOGO_TARGET_PX = 256

    fun isWithinLimits(
        width: Int,
        height: Int,
        maxDimension: Int = MAX_DECODED_DIMENSION,
        maxPixels: Long = MAX_DECODED_PIXELS,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (width > maxDimension || height > maxDimension) return false
        return width.toLong() * height.toLong() <= maxPixels
    }

    /** Power-of-two sample size that keeps both dimensions and the pixel count within limits. */
    fun sampleSize(
        width: Int,
        height: Int,
        target: Int,
        maxPixels: Long = MAX_DECODED_PIXELS,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > target || height / sample > target ||
            (width.toLong() / sample) * (height.toLong() / sample) > maxPixels
        ) {
            if (sample >= MAX_SAMPLE_SIZE) return MAX_SAMPLE_SIZE
            sample *= 2
        }
        return sample
    }

    /** Target size passed to ImageDecoder so the decoded bitmap stays inside both ceilings. */
    fun targetSize(
        width: Int,
        height: Int,
        maxDimension: Int = MAX_DECODED_DIMENSION,
        maxPixels: Long = MAX_DECODED_PIXELS,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val byDimension = min(maxDimension.toDouble() / width, maxDimension.toDouble() / height)
        val byPixels = sqrt(maxPixels.toDouble() / (width.toLong() * height.toLong()).toDouble())
        val scale = minOf(1.0, byDimension, byPixels)
        return maxOf(1, floor(width * scale).toInt()) to maxOf(1, floor(height * scale).toInt())
    }

    /** ARGB_8888 memory an image of this size occupies after decode. */
    fun decodedBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * Int.SIZE_BYTES.toLong()

    /** Reads at most [maxBytes] compressed bytes; throws instead of buffering an endless body. */
    fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= maxBytes) { "Image too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

/** Bounded logo cache: avoids refetching the same artwork while scrolling large lists. */
private object LogoCache {
    private const val maxEntries = 128
    private const val maxBytes = 8L * 1024 * 1024
    private const val maxEntryBytes = 1L * 1024 * 1024
    private var bytes = 0L
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (eldest == null) return false
            val remove = size > maxEntries || bytes > maxBytes
            if (remove) bytes -= eldest.value.byteCount
            return remove
        }
    }

    @Synchronized
    fun get(url: String): Bitmap? = cache[url]

    @Synchronized
    fun put(url: String, bitmap: Bitmap) {
        // One oversized bitmap must not enter the cache even when the cache total is still small.
        if (bitmap.byteCount <= 0 || bitmap.byteCount > maxEntryBytes) return
        cache[url]?.let { bytes -= it.byteCount }
        cache[url] = bitmap
        bytes += bitmap.byteCount
    }
}

@Composable
fun ChannelLogo(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(url?.let { LogoCache.get(it) }) }
    LaunchedEffect(url) {
        val key = url
        if (key.isNullOrBlank() || NetworkPolicy.parse(key) == null) return@LaunchedEffect
        val cached = LogoCache.get(key)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                NetworkPolicy.client().newCall(Request.Builder().url(key).build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bytes = response.readBytes(4L * 1024 * 1024)
                    // Bounds pass first: a small compressed file can declare a huge bitmap.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (!SafeImageDecode.isWithinLimits(
                            bounds.outWidth,
                            bounds.outHeight,
                            maxDimension = 4_096,
                            maxPixels = 16_000_000L,
                        )
                    ) return@runCatching null
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = SafeImageDecode.sampleSize(
                            bounds.outWidth,
                            bounds.outHeight,
                            SafeImageDecode.LOGO_TARGET_PX,
                            1_000_000L,
                        )
                    }
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@runCatching null
                    if (SafeImageDecode.decodedBytes(decoded.width, decoded.height) >
                        SafeImageDecode.MAX_DECODED_MEMORY_BYTES
                    ) {
                        decoded.recycle()
                        return@runCatching null
                    }
                    decoded
                }
            }.onSuccess { decoded ->
                if (decoded != null) {
                    LogoCache.put(key, decoded)
                    bitmap = decoded
                }
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            image.asImageBitmap(),
            contentDescription = stringResource(R.string.common_tv),
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_tv), style = MaterialTheme.typography.labelSmall)
        }
    }
}
