package pw.c0re.stream.ui.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pw.c0re.stream.data.db.AppDatabase
import java.io.File

/**
 * Backup: exportiert alle Playlists samt Kanaelen als gueltige M3U-Datei
 * und oeffnet den System-Teilen-Dialog (Speichern in Drive/Mail/etc.).
 */
object M3UExporter {
    private fun clean(value: String): String = value.filterNot { it.isISOControl() }.replace('"', '\'')


    suspend fun buildM3U(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder("#EXTM3U\n")
        val playlists = db.playlistDao().all().first()
        for (pl in playlists) {
            val channels = db.channelDao().byPlaylist(pl.id).first()
            for (ch in channels) {
                sb.append("#EXTINF:-1")
                if (!ch.tvgId.isNullOrBlank()) sb.append(" tvg-id=\"").append(clean(ch.tvgId.orEmpty())).append("\"")
                if (!ch.logoUrl.isNullOrBlank()) sb.append(" tvg-logo=\"").append(clean(ch.logoUrl.orEmpty())).append("\"")
                if (!ch.group.isNullOrBlank()) sb.append(" group-title=\"").append(clean(ch.group.orEmpty())).append("\"")
                sb.append(", ").append(clean(ch.name)).append("\n").append(ch.url).append("\n")
            }
        }
        sb.toString()
    }

    suspend fun share(context: Context, db: AppDatabase) {
        val m3u = buildM3U(db)
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".m3u") }?.forEach { it.delete() }
            File.createTempFile("c0restream-", ".m3u", dir).apply { writeText(m3u) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        android.app.AlertDialog.Builder(context)
            .setTitle("Unverschlüsselter M3U-Export")
            .setMessage("Die Datei kann Zugangsdaten enthalten. Nur mit vertrauenswürdigen Empfängern teilen.")
            .setNegativeButton(android.R.string.cancel) { _, _ -> file.delete() }
            .setPositiveButton("Teilen") { _, _ -> context.startActivity(Intent.createChooser(intent, "C0reStream M3U")) }
            .setOnCancelListener { file.delete() }
            .show()
    }
}
