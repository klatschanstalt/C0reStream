package pw.c0re.stream.ui.vod

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.XtreamApi
import pw.c0re.stream.data.db.WatchlistEntity
import pw.c0re.stream.data.vod.XtreamVodApi
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.playlistdetail.ChannelLogo

class VodViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val api = XtreamVodApi(XtreamApi(NetworkPolicy.client()))

    val creds = MutableStateFlow<XtreamApi.Credentials?>(null)
    val movies = MutableStateFlow<List<XtreamVodApi.VodItem>>(emptyList())
    val seriesList = MutableStateFlow<List<XtreamVodApi.VodItem>>(emptyList())
    val episodes = MutableStateFlow<Map<Int, List<XtreamVodApi.Episode>>>(emptyMap())
    val error = MutableStateFlow<String?>(null)
    val watchlist = db.watchlistDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var playlistId: Long = 0

    fun init(id: Long) {
        if (creds.value != null || id <= 0) return
        playlistId = id
        viewModelScope.launch {
            val playlist = db.playlistDao().byId(id) ?: return@launch
            val parsed = playlist.url?.let { XtreamApi.parseSyntheticUrl(it) }
            if (parsed == null) {
                error.value = "Xtream login required"
                return@launch
            }
            creds.value = parsed
            runCatching { movies.value = api.movies(parsed) }
                .onFailure {
                    error.value = it.message
                    AppLog.log("VOD", it.message ?: "?")
                }
            runCatching { seriesList.value = api.series(parsed) }
                .onFailure { AppLog.log("Series", it.message ?: "?") }
        }
    }

    fun loadEpisodes(seriesId: Int) = viewModelScope.launch {
        val current = creds.value ?: return@launch
        runCatching { episodes.value = api.seriesInfo(current, seriesId) }
            .onFailure { error.value = it.message }
    }

    fun movieUrl(item: XtreamVodApi.VodItem): String? = creds.value?.let {
        runCatching { api.movieUrl(it, item.id, item.container) }.getOrNull()
    }

    fun episodeUrl(episode: XtreamVodApi.Episode): String? = creds.value?.let {
        runCatching { api.episodeUrl(it, episode.id, episode.container) }.getOrNull()
    }

    fun movieKey(item: XtreamVodApi.VodItem): String = "movie:$playlistId:${item.id}"

    fun episodeKey(series: XtreamVodApi.VodItem, episode: XtreamVodApi.Episode): String =
        "episode:$playlistId:${series.id}:${episode.id}"

    fun toggleMovieWatchlist(item: XtreamVodApi.VodItem) = viewModelScope.launch {
        val key = movieKey(item)
        val existing = db.watchlistDao().byKey(key)
        if (existing != null) {
            db.watchlistDao().remove(key)
        } else {
            db.watchlistDao().upsert(
                WatchlistEntity(
                    playlistId = playlistId,
                    mediaKey = key,
                    title = item.name.take(512),
                    posterUrl = item.poster,
                    url = movieUrl(item),
                    kind = "movie",
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun toggleSeriesWatchlist(item: XtreamVodApi.VodItem) = viewModelScope.launch {
        val key = "series:$playlistId:${item.id}"
        val existing = db.watchlistDao().byKey(key)
        if (existing != null) {
            db.watchlistDao().remove(key)
        } else {
            db.watchlistDao().upsert(
                WatchlistEntity(
                    playlistId = playlistId,
                    mediaKey = key,
                    title = item.name.take(512),
                    posterUrl = item.poster,
                    url = null,
                    kind = "series",
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun isWatchlisted(mediaKey: String) = watchlist.value.any { it.mediaKey == mediaKey }
}

@Composable
fun VodScreen(
    playlistId: Long,
    onPlayMedia: (url: String, title: String, mediaKey: String?, kind: String) -> Unit,
    vm: VodViewModel = viewModel(key = "vod$playlistId"),
) {
    LaunchedEffect(playlistId) { vm.init(playlistId) }
    val movies by vm.movies.collectAsState()
    val seriesList by vm.seriesList.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val error by vm.error.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openSeries by remember { mutableStateOf<XtreamVodApi.VodItem?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(128) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(stringResource(R.string.vod_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.vod_movies)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.vod_series)) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.watchlist_title)) })
        }
        error?.let {
            Text(
                if (it == "Xtream login required") stringResource(R.string.vod_xtream_only) else it,
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (tab) {
            0 -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                val filtered = movies.filter { query.isBlank() || it.name.contains(query, true) }
                items(filtered, key = { it.id }) { movie ->
                    val key = vm.movieKey(movie)
                    Card(onClick = {
                        val url = vm.movieUrl(movie) ?: return@Card
                        onPlayMedia(url, movie.name, key, "movie")
                    }, shape = RoundedCornerShape(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelLogo(movie.poster, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(movie.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                movie.rating?.let {
                                    Text(
                                        stringResource(R.string.vod_rating, it),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            IconButton(onClick = { vm.toggleMovieWatchlist(movie) }) {
                                Icon(
                                    if (vm.isWatchlisted(key)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    stringResource(R.string.content_desc_watchlist),
                                )
                            }
                        }
                    }
                }
                item { PoweredByFooter() }
            }
            1 -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                val filtered = seriesList.filter { query.isBlank() || it.name.contains(query, true) }
                items(filtered, key = { it.id }) { series ->
                    val key = "series:$playlistId:${series.id}"
                    Card(onClick = {
                        openSeries = series
                        vm.loadEpisodes(series.id)
                    }, shape = RoundedCornerShape(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelLogo(series.poster, Modifier.size(48.dp))
                            Text(
                                series.name,
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { vm.toggleSeriesWatchlist(series) }) {
                                Icon(
                                    if (vm.isWatchlisted(key)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    stringResource(R.string.content_desc_watchlist),
                                )
                            }
                        }
                    }
                }
                item { PoweredByFooter() }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (watchlist.isEmpty()) {
                    item { Text(stringResource(R.string.watchlist_empty)) }
                }
                items(watchlist, key = { it.id }) { entry ->
                    WatchlistRow(
                        entry = entry,
                        onPlay = { url, title, key, kind -> onPlayMedia(url, title, key, kind) },
                    )
                }
                item { PoweredByFooter() }
            }
        }
    }

    openSeries?.let { series ->
        AlertDialog(
            onDismissRequest = { openSeries = null },
            title = { Text(series.name) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (episodes.isEmpty()) {
                        item { Text(stringResource(R.string.vod_loading)) }
                    }
                    episodes.forEach { (season, list) ->
                        item {
                            Text(
                                stringResource(R.string.vod_season, season),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(list, key = { it.id }) { episode ->
                            Card(onClick = {
                                val url = vm.episodeUrl(episode) ?: return@Card
                                openSeries = null
                                onPlayMedia(
                                    url,
                                    series.name + " S" + episode.season + "E" + episode.episodeNum,
                                    vm.episodeKey(series, episode),
                                    "episode",
                                )
                            }) {
                                Text(
                                    "E" + episode.episodeNum + " - " + episode.title,
                                    Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openSeries = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun WatchlistRow(
    entry: WatchlistEntity,
    onPlay: (String, String, String, String) -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(entry.posterUrl, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(entry.kind, style = MaterialTheme.typography.bodySmall)
            }
            entry.url?.let { url ->
                TextButton(onClick = { onPlay(url, entry.title, entry.mediaKey, entry.kind) }) {
                    Text(stringResource(R.string.vod_resume))
                }
            }
        }
    }
}
