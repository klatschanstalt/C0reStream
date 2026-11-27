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
import pw.c0re.stream.data.ImportGuardException
import pw.c0re.stream.data.ImportResult
import pw.c0re.stream.data.ImportStats
import pw.c0re.stream.data.M3UParser
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.PlaylistRepository
import pw.c0re.stream.data.XtreamApi

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private companion object { const val MAX_IMPORT_BYTES = 32L * 1024 * 1024 }
    private val db = (app as C0reStreamApp).database
    private val repo = PlaylistRepository(db)

    val playlists = db.playlistDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastError = mutableStateOf<String?>(null)
    val lastImport = mutableStateOf<ImportResult?>(null)

    private fun record(result: ImportResult) {
        lastImport.value = result
    }

    private fun mutableImportResult(stats: ImportStats) = ImportResult(
        playlistId = 0L,
        stats = stats,
        orderedKeys = emptyList(),
        orderedChannelIds = emptyList(),
        preservedIds = 0,
        shrinkGuard = pw.c0re.stream.data.ShrinkGuard(),
    )

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
                    val report = XtreamApi(client).fetchLiveChannelsWithReport(credentials)
                    check(report.channels.isNotEmpty()) { "No channels" }
                    repo.storeChannelsWithStats(
                        name = name,
                        url = XtreamApi.syntheticUrl(credentials),
                        epg = epg,
                        userAgent = userAgent,
                        useProxy = proxy,
                        proxyHost = host,
                        proxyPort = port,
                        raw = report.channels,
                        sourceStats = report.stats,
                    )
                }
            } else {
                runCatching { repo.fetchAndStoreWithStats(name, url, epg, userAgent, proxy, host, port) }
            }
            result.onSuccess { imported ->
                record(imported)
                onDone(imported.playlistId)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (error is ImportGuardException) record(mutableImportResult(error.stats))
                lastError.value = error.message
            }
        }
    }

    fun importFile(uri: android.net.Uri, onDone: (Long) -> Unit) = viewModelScope.launch {
        try {
            val resolver = getApplication<C0reStreamApp>().contentResolver
            // Best-effort persistable read access; not required for immediate use and not all
            // providers support it. Failure here must not abort an otherwise readable import.
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val raw = withContext(Dispatchers.IO) {
                val bytes = readBounded(resolver, uri)
                M3UParser.parseWithStats(bytes)
            }
            check(raw.channels.isNotEmpty()) { "No channels found" }
            val displayName = withContext(Dispatchers.IO) { queryDisplayName(resolver, uri) }
            val name = suggestedPlaylistName(uri, displayName)
            val result = repo.storeChannelsWithStats(
                name = name,
                url = null,
                epg = null,
                userAgent = null,
                useProxy = false,
                proxyHost = null,
                proxyPort = null,
                raw = raw.channels,
                sourceStats = raw.stats,
            )
            record(result)
            onDone(result.playlistId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lastError.value = error.message ?: "Import failed"
        }
    }

    private suspend fun readBounded(resolver: android.content.ContentResolver, uri: android.net.Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri)
                ?: throw java.io.FileNotFoundException("Cannot open ${uri.scheme ?: "content"} source")
            input.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > MAX_IMPORT_BYTES) throw IllegalStateException("File too large")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()

    private fun suggestedPlaylistName(uri: android.net.Uri, displayName: String?): String {
        val raw = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val withoutExtension = raw
            ?.substringBeforeLast('.')
            ?.substringBeforeLast(".gz")
            ?.takeIf { it.isNotBlank() }
        return withoutExtension ?: "Local playlist"
    }

    fun refresh(id: Long) = viewModelScope.launch {
        val playlist = db.playlistDao().byId(id) ?: return@launch
        repo.refreshWithStats(playlist).onSuccess { result ->
            record(result)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            if (error is ImportGuardException) record(mutableImportResult(error.stats))
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
