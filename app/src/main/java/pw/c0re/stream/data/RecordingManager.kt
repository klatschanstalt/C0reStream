package pw.c0re.stream.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-authorized local capture of a plain (non-DRM) stream. It never bypasses DRM and writes
 * only into the app's own external files directory. Cancellation removes partial files.
 */
object RecordingManager {
    private const val maxBytes = 2L * 1024 * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    fun isRecording(): Boolean = job?.isActive == true

    /**
     * Source-specific values from PlaylistEntity may be supplied by the caller; the defaults keep
     * existing three-argument callers unchanged. They are applied to every request made by the
     * continuous-stream [NetworkPolicy.recordingClient].
     */
    fun start(
        context: Context,
        url: String,
        title: String?,
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): Boolean {
        if (NetworkPolicy.parse(url) == null || job?.isActive == true) return false
        val directory = File(context.getExternalFilesDir("recordings") ?: context.filesDir, "recordings")
            .apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val safeTitle = title.orEmpty().replace(Regex("[^A-Za-z0-9 _-]"), "").take(64)
            .ifBlank { "recording" }
        val file = File(directory, "$safeTitle-$stamp.ts")
        _active.value = file.name
        job = scope.launch {
            val currentJob = coroutineContext[Job]
            var succeeded = false
            try {
                NetworkPolicy.recordingClient(userAgent, useProxy, proxyHost, proxyPort)
                    .newCall(Request.Builder().url(NetworkPolicy.requireUrl(url)).build())
                    .execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = response.body
                        check(body.contentLength() <= maxBytes) { "Stream too large" }
                        file.outputStream().use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (isActive) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    check(total <= maxBytes) { "Recording limit reached" }
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                succeeded = isActive
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.log("Record", error.message ?: "?")
            } finally {
                if (!succeeded) runCatching { file.delete() }
                // A fast stop/start sequence must not let the old job clear the new recording's
                // active state when its finally block finally runs.
                if (job == null || job === currentJob) _active.value = null
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
