package pw.c0re.stream.ui.playlists

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.ImportGuardException
import pw.c0re.stream.data.ImportResult
import pw.c0re.stream.data.ImportStats
import pw.c0re.stream.data.M3UParser
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.PlaylistRepository
import pw.c0re.stream.data.XtreamApi
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

internal class ImportTooLargeException : IllegalStateException("Playlist file too large")
internal class InvalidPlaylistFormatException : IllegalArgumentException("Unsupported playlist format")

/** Maps import failures to stable, localized messages; never exposes a provider path or URL. */
fun localizedImportError(error: Throwable, context: Context): String {
    val resId = when (error) {
        is SecurityException -> R.string.error_import_permission
        is ImportTooLargeException -> R.string.error_import_too_large
        is InvalidPlaylistFormatException -> R.string.error_import_format
        is FileNotFoundException, is IOException -> R.string.error_import_unreadable
        else -> R.string.error_import_failed
    }
    return context.getString(resId)
}

/** Derives a short display name from a provider file name without path or control characters. */
fun playlistNameFromFileName(candidate: String?, fallback: String): String {
    val cleaned = candidate
        ?.map { if (it.isISOControl() || it == '/' || it == '\\' || it == ':') ' ' else it }
        ?.joinToString("")
        ?.trim()
        ?.removeSuffix(".gz")
        ?.substringBeforeLast('.')
        ?.trim()
        ?.take(128)
    return cleaned?.takeIf { it.isNotEmpty() } ?: fallback
}

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
    }

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

    /**
     * Imports a document picked through SAF. Only a content/file URI is accepted and it is always
     * read through [ContentResolver]; no filesystem path is reconstructed from it. A `null` URI
     * (picker cancellation) is a no-op, and a cancelled job never reaches the transactional store.
     *
     * [scope] and [maxBytes] are injectable seams for instrumented tests; production uses the
     * view-model scope and the full 32 MiB ceiling.
     */
    fun importFile(
        uri: Uri?,
        onDone: (Long) -> Unit,
        scope: CoroutineScope = viewModelScope,
        maxBytes: Long = MAX_IMPORT_BYTES,
    ): Job? {
        if (uri == null) {
            clearError()
            lastImport.value = null
            return null
        }
        require(maxBytes > 0) { "maxBytes must be positive" }
        return scope.launch {
            clearError()
            lastImport.value = null
            try {
                val resolver = getApplication<Application>().contentResolver
                // Best-effort persistable read access; not all providers support it and it is not
                // required for the immediate read while the picker grant is valid.
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                currentCoroutineContext().ensureActive()

                val bytes = readBounded(resolver, uri, maxBytes)
                currentCoroutineContext().ensureActive()
                if (!M3UParser.looksLikePlaylist(bytes)) throw InvalidPlaylistFormatException()
                val raw = withContext(Dispatchers.IO) { M3UParser.parseWithStats(bytes) }
                currentCoroutineContext().ensureActive()
                if (raw.channels.isEmpty()) throw InvalidPlaylistFormatException()

                val displayName = withContext(Dispatchers.IO) { queryDisplayName(resolver, uri) }
                currentCoroutineContext().ensureActive()
                val name = suggestedPlaylistName(uri, displayName)
                val result = withContext(Dispatchers.IO) {
                    repo.storeChannelsWithStats(
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
                }
                currentCoroutineContext().ensureActive()
                record(result)
                onDone(result.playlistId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A failure after cancellation must not resurrect UI state.
                if (isActive) lastError.value = localizedImportError(error, getApplication())
            }
        }
    }

    private suspend fun readBounded(
        resolver: ContentResolver,
        uri: Uri,
        maxBytes: Long,
    ): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open selected document")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) throw ImportTooLargeException()
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()

    private fun suggestedPlaylistName(uri: Uri, displayName: String?): String {
        val fallback = getApplication<Application>().getString(R.string.import_local_name)
        return playlistNameFromFileName(displayName ?: uri.lastPathSegment, fallback)
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
