package pw.c0re.stream.ui.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pw.c0re.stream.R
import pw.c0re.stream.data.db.AppDatabase
import java.io.File

/**
 * Exports all playlists and channels as a valid M3U file and opens the system share sheet.
 * The file is unencrypted and may contain credentials, so the user is warned first.
 */
object M3UExporter {
    private fun clean(value: String): String =
        value.filterNot { it.isISOControl() }.replace('"', '\'')

    suspend fun buildM3U(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder("#EXTM3U\n")
        for (playlist in db.playlistDao().allOnce()) {
            for (channel in db.channelDao().snapshot(playlist.id)) {
                builder.append("#EXTINF:-1")
                if (!channel.tvgId.isNullOrBlank()) {
                    builder.append(" tvg-id=\"").append(clean(channel.tvgId.orEmpty())).append('"')
                }
                if (!channel.logoUrl.isNullOrBlank()) {
                    builder.append(" tvg-logo=\"").append(clean(channel.logoUrl.orEmpty())).append('"')
                }
                if (!channel.group.isNullOrBlank()) {
                    builder.append(" group-title=\"").append(clean(channel.group.orEmpty())).append('"')
                }
                builder.append(", ").append(clean(channel.name)).append('\n')
                builder.append(channel.url).append('\n')
            }
        }
        builder.toString()
    }

    suspend fun share(context: Context, db: AppDatabase) {
        val m3u = buildM3U(db)
        val directory = File(context.cacheDir, "export").apply { mkdirs() }
        val file = withContext(Dispatchers.IO) {
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(".m3u") }?.forEach { it.delete() }
            File.createTempFile("c0restream-", ".m3u", directory).apply {
                writeText(m3u)
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.backup_export_m3u_warning)
            .setMessage(R.string.settings_export_m3u)
            .setNegativeButton(android.R.string.cancel) { _, _ -> file.delete() }
            .setPositiveButton(R.string.backup_export_m3u_share) { _, _ ->
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_export_m3u)))
            }
            .setOnCancelListener { file.delete() }
            .show()
    }
}
