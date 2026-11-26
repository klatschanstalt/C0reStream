package tv.ham3l.app.ui.vod

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp
import tv.ham3l.app.data.AppLog
import tv.ham3l.app.data.XtreamApi
import tv.ham3l.app.data.vod.XtreamVodApi
import tv.ham3l.app.ui.components.PoweredByFooter
import tv.ham3l.app.ui.playlistdetail.ChannelLogo

class VodViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database
    private val api = XtreamVodApi(okhttp3.OkHttpClient())

    val creds = MutableStateFlow<XtreamApi.Credentials?>(null)
    val movies = MutableStateFlow<List<XtreamVodApi.VodItem>>(emptyList())
    val seriesList = MutableStateFlow<List<XtreamVodApi.VodItem>>(emptyList())
    val episodes = MutableStateFlow<Map<Int, List<XtreamVodApi.Episode>>>(emptyMap())
    val error = MutableStateFlow<String?>(null)

    fun init(playlistId: Long) {
        if (creds.value != null) return
        viewModelScope.launch {
            val pl = db.playlistDao().byId(playlistId) ?: return@launch
            val parsed = pl.url?.let { XtreamApi.parseSyntheticUrl(it) }
            if (parsed == null) {
                error.value = "Diese Playlist ist kein Xtream-Server (Filme/Serien nur per Xtream-Login)."
                return@launch
            }
            creds.value = parsed
            runCatching { movies.value = api.movies(parsed) }.onFailure { error.value = it.message; AppLog.log("VOD", it.message ?: "?") }
            runCatching { seriesList.value = api.series(parsed) }.onFailure { AppLog.log("Series", it.message ?: "?") }
        }
    }

    fun loadEpisodes(seriesId: Int) = viewModelScope.launch {
        val c = creds.value ?: return@launch
        runCatching { episodes.value = api.seriesInfo(c, seriesId) }.onFailure { error.value = it.message }
    }

    fun playMovie(id: Int, onUrl: (String) -> Unit) {
        val c = creds.value ?: return
        onUrl(api.movieUrl(c, id))
    }

    fun playEpisode(id: Int, container: String, onUrl: (String) -> Unit) {
        val c = creds.value ?: return
        onUrl(api.episodeUrl(c, id, container))
    }
}

@Composable
fun VodScreen(
    playlistId: Long,
    onPlayUrl: (String, String) -> Unit,
    vm: VodViewModel = viewModel(key = "vod$playlistId"),
) {
    LaunchedEffect(playlistId) { vm.init(playlistId) }
    val creds by vm.creds.collectAsState()
    val movies by vm.movies.collectAsState()
    val seriesList by vm.seriesList.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val error by vm.error.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var openSeries by remember { mutableStateOf<XtreamVodApi.VodItem?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Filme") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Serien") })
        }
        error?.let { Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) }

        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(movies, key = { it.id }) { m ->
                    Card(onClick = { vm.playMovie(m.id) { url -> onPlayUrl(url, m.name) } }, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            ChannelLogo(m.poster, Modifier.padding(end = 12.dp).fillMaxWidth(0.18f))
                            Column {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                m.rating?.let { Text("Bewertung: " + it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                item { PoweredByFooter() }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(seriesList, key = { it.id }) { s ->
                    Card(onClick = { openSeries = s; vm.loadEpisodes(s.id) }, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            ChannelLogo(s.poster, Modifier.padding(end = 12.dp).fillMaxWidth(0.18f))
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                item { PoweredByFooter() }
            }
        }
    }

    // Episoden-Dialog
    openSeries?.let { s ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { openSeries = null },
            title = { Text(s.name) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    episodes.isEmpty().let { empty ->
                        if (empty) item { Text("Lade Episoden ...") }
                    }
                    episodes.forEach { (season, eps) ->
                        item { Text("Staffel " + season, style = MaterialTheme.typography.titleMedium) }
                        items(eps, key = { it.id }) { e ->
                            Card(onClick = { vm.playEpisode(e.id, e.container) { url -> onPlayUrl(url, s.name + " S" + e.season + "E" + e.episodeNum) } }) {
                                Text("E" + e.episodeNum + " - " + e.title, Modifier.fillMaxWidth().padding(12.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { openSeries = null }) { Text("Schliessen") } },
        )
    }
}
