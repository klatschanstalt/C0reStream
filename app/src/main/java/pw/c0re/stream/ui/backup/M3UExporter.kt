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

    suspend fun buildM3U(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder("#EXTM3U\n")
        val playlists = db.playlistDao().all().first()
        for (pl in playlists) {
            val channels = db.channelDao().byPlaylist(pl.id).first()
            for (ch in channels) {
                sb.append("#EXTINF:-1")
                if (!ch.tvgId.isNullOrBlank()) sb.append(" tvg-id=\"").append(ch.tvgId).append("\"")
                if (!ch.logoUrl.isNullOrBlank()) sb.append(" tvg-logo=\"").append(ch.logoUrl).append("\"")
                if (!ch.group.isNullOrBlank()) sb.append(" group-title=\"").append(ch.group).append("\"")
                sb.append(", ").append(ch.name).append("\n").append(ch.url).append("\n")
            }
        }
        sb.toString()
    }

    suspend fun share(context: Context, db: AppDatabase) {
        val m3u = buildM3U(db)
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "ham3l-tv-backup.m3u")
        file.writeText(m3u)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "C0reStream TV Backup teilen"))
    }
}
