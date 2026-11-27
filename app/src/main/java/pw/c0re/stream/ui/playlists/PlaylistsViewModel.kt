package pw.c0re.stream.ui.playlists

import android.app.Application
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.PlaylistRepository

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val repo = PlaylistRepository(db)

    val playlists = db.playlistDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastError = mutableStateOf<String?>(null)

    fun add(name: String, url: String, epg: String?, ua: String?, proxy: Boolean, host: String?, port: Int?, xtream: pw.c0re.stream.ui.playlists.XtreamInput?, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val result = if (xtream != null && xtream.enabled) {
                runCatching {
                    val creds = pw.c0re.stream.data.XtreamApi.Credentials(xtream.host, xtream.username, xtream.password)
                    val raw = pw.c0re.stream.data.XtreamApi(okhttp3.OkHttpClient.Builder().followSslRedirects(false).callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build()).fetchLiveChannels(creds)
                    check(raw.isNotEmpty()) { "Xtream-Login fehlgeschlagen oder keine Kanaele" }
                    repo.storeChannels(name, pw.c0re.stream.data.XtreamApi.syntheticUrl(creds), epg, ua, proxy, host, port, raw)
                }
            } else {
                repo.fetchAndStore(name, url, epg, ua, proxy, host, port)
            }
            result.onSuccess { onDone(it) }.onFailure { if (it is CancellationException) throw it; lastError.value = "Playlist konnte nicht geladen werden" }
        }
    }

    fun importFile(uri: android.net.Uri, onDone: (Long) -> Unit) = viewModelScope.launch {
        try {
            val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val resolver = getApplication<C0reStreamApp>().contentResolver
                requireNotNull(resolver.openInputStream(uri)).use { input ->
                    val bytes = input.readNBytes(32 * 1024 * 1024 + 1)
                    require(bytes.size <= 32 * 1024 * 1024)
                    pw.c0re.stream.data.M3UParser.parse(bytes.toString(Charsets.UTF_8))
                }
            }
            onDone(repo.storeChannels("Lokale Playlist", null, null, null, false, null, null, raw))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { lastError.value = "Dateiimport fehlgeschlagen" }
    }

    fun refresh(id: Long) {
        viewModelScope.launch {
            val pl = db.playlistDao().byId(id) ?: return@launch
            val url = pl.url ?: return@launch
            val result = if (url.startsWith("xtream://")) runCatching {
                val creds = checkNotNull(pw.c0re.stream.data.XtreamApi.parseSyntheticUrl(url))
                val raw = pw.c0re.stream.data.XtreamApi(okhttp3.OkHttpClient.Builder().followSslRedirects(false).callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build()).fetchLiveChannels(creds)
                repo.storeChannels(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort, raw, id)
            } else repo.fetchAndStore(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort, id)
            result.onFailure { if (it is CancellationException) throw it; lastError.value = "Aktualisierung fehlgeschlagen" }
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        db.withTransaction {
        // Zuerst abhaengige Favoriten/Verlaufseintraege entfernen, dann Playlist + Kanaele
        db.favoriteDao().removeForPlaylist(id)
        db.historyDao().removeForPlaylist(id)
        db.channelDao().deleteForPlaylist(id)
        db.playlistDao().delete(id)
        }
    }
}
