package tv.ham3l.app.ui.playlists

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp
import tv.ham3l.app.data.PlaylistRepository

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database
    private val repo = PlaylistRepository(db)

    val playlists = db.playlistDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastError = mutableStateOf<String?>(null)

    fun add(name: String, url: String, epg: String?, ua: String?, proxy: Boolean, host: String?, port: Int?, xtream: tv.ham3l.app.ui.playlists.XtreamInput?, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val result = if (xtream != null && xtream.enabled) {
                runCatching {
                    val creds = tv.ham3l.app.data.XtreamApi.Credentials(xtream.host, xtream.username, xtream.password)
                    val raw = tv.ham3l.app.data.XtreamApi(okhttp3.OkHttpClient()).fetchLiveChannels(creds)
                    check(raw.isNotEmpty()) { "Xtream-Login fehlgeschlagen oder keine Kanaele" }
                    // temporaere M3U bauen und normal einspeisen
                    val m3u = buildString {
                        append("#EXTM3U\n")
                        raw.forEach { r ->
                            append("#EXTINF:-1")
                            if (!r.logo.isNullOrBlank()) append(" tvg-logo=\"").append(r.logo).append("\"")
                            if (!r.tvgId.isNullOrBlank()) append(" tvg-id=\"").append(r.tvgId).append("\"")
                            append(", ").append(r.name).append("\n").append(r.url).append("\n")
                        }
                    }
                    val channels = tv.ham3l.app.data.M3UParser.parse(m3u)
                    storeChannels(name, channels, tv.ham3l.app.data.XtreamApi.syntheticUrl(creds), epg, ua, proxy, host, port)
                }
            } else {
                repo.fetchAndStore(name, url, epg, ua, proxy, host, port)
            }
            result.onSuccess { onDone(it) }.onFailure { lastError.value = it.message }
        }
    }

    private suspend fun storeChannels(
        name: String, raw: List<tv.ham3l.app.data.M3UParser.RawChannel>, url: String?, epg: String?, ua: String?, proxy: Boolean, host: String?, port: Int?,
    ): Long {
        val dbx = db
        val playlistId = dbx.playlistDao().upsert(
            tv.ham3l.app.data.db.PlaylistEntity(name = name, url = url, epgUrl = epg, userAgent = ua, useProxy = proxy, proxyHost = host, proxyPort = port, lastUpdated = System.currentTimeMillis()),
        )
        dbx.channelDao().deleteForPlaylist(playlistId)
        dbx.channelDao().insertAll(tv.ham3l.app.data.M3UParser.toChannels(playlistId, raw).map {
            tv.ham3l.app.data.db.ChannelEntity(playlistId = it.playlistId, name = it.name, logoUrl = it.logoUrl, group = it.group, url = it.url, tvgId = it.tvgId, catchupSource = it.catchupSource)
        })
        dbx.playlistDao().updateMeta(playlistId, raw.size, System.currentTimeMillis())
        return playlistId
    }

    fun refresh(id: Long) {
        viewModelScope.launch {
            val pl = db.playlistDao().byId(id) ?: return@launch
            val url = pl.url ?: return@launch
            repo.fetchAndStore(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort)
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        // Zuerst abhaengige Favoriten/Verlaufseintraege entfernen, dann Playlist + Kanaele
        db.favoriteDao().removeForPlaylist(id)
        db.historyDao().removeForPlaylist(id)
        db.channelDao().deleteForPlaylist(id)
        db.playlistDao().delete(id)
    }
}
