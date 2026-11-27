package pw.c0re.stream.ui.playlists

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.M3UParser
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.PlaylistRepository
import pw.c0re.stream.data.XtreamApi
import pw.c0re.stream.data.decodeText

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val repo = PlaylistRepository(db)

    val playlists = db.playlistDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastError = mutableStateOf<String?>(null)

    fun add(
        name: String,
        url: String,
        epg: String?,
        userAgent: String?,
        proxy: Boolean,
        host: String?,
        port: Int?,
        xtream: XtreamInput?,
        onDone: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val result = if (xtream != null && xtream.enabled) {
                runCatching {
                    val credentials = XtreamApi.Credentials(
                        host = xtream.host.trim(),
                        username = xtream.username.trim(),
                        password = xtream.password,
                    )
                    NetworkPolicy.requireUrl(credentials.host)
                    val client = NetworkPolicy.client(userAgent, proxy, host, port)
                    val raw = XtreamApi(client).fetchLiveChannels(credentials)
                    check(raw.isNotEmpty()) { "No channels" }
                    repo.storeChannels(
                        name = name,
                        url = XtreamApi.syntheticUrl(credentials),
                        epg = epg,
                        userAgent = userAgent,
                        useProxy = proxy,
                        proxyHost = host,
                        proxyPort = port,
                        raw = raw,
                    )
                }
            } else {
                repo.fetchAndStore(name, url, epg, userAgent, proxy, host, port)
            }
            result.onSuccess(onDone).onFailure { error ->
                if (error is CancellationException) throw error
                lastError.value = error.message
            }
        }
    }

    fun importFile(uri: android.net.Uri, onDone: (Long) -> Unit) = viewModelScope.launch {
        try {
            val raw = withContext(Dispatchers.IO) {
                val resolver = getApplication<C0reStreamApp>().contentResolver
                requireNotNull(resolver.openInputStream(uri)).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(out.size() + read <= 32 * 1024 * 1024) { "File too large" }
                        out.write(buffer, 0, read)
                    }
                    M3UParser.parse(decodeText(out.toByteArray()))
                }
            }
            onDone(repo.storeChannels("Local playlist", null, null, null, false, null, null, raw))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lastError.value = error.message
        }
    }

    fun refresh(id: Long) = viewModelScope.launch {
        val playlist = db.playlistDao().byId(id) ?: return@launch
        repo.refresh(playlist).onFailure { error ->
            if (error is CancellationException) throw error
            lastError.value = error.message
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.deletePlaylist(id)
    }

    fun clearError() {
        lastError.value = null
    }
}
