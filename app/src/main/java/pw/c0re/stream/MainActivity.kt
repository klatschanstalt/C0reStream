package pw.c0re.stream

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import pw.c0re.stream.data.profiles.QrPlaylistParser
import pw.c0re.stream.ui.C0reStreamRoot
import pw.c0re.stream.ui.player.PlayerHub
import pw.c0re.stream.ui.theme.C0reStreamThemeFromPrefs
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val sharedM3u = mutableStateOf<String?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Playback remains available when notifications are denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedM3u.value = extractSharedPlaylist(intent)
        setContent {
            C0reStreamThemeFromPrefs {
                C0reStreamRoot(sharedM3uUrl = sharedM3u.value)
            }
        }
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedM3u.value = extractSharedPlaylist(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!PlayerHub.isPlaying || PlayerHub.title == null) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    /**
     * Accepts only a single, well-formed HTTP(S) playlist URL. The value only pre-fills the add
     * dialog after the user has passed the profile/PIN gate; nothing is fetched or played here, so
     * an external intent cannot execute a URL or bypass protected profiles.
     */
    private fun extractSharedPlaylist(intent: Intent?): String? {
        if (intent == null) return null
        val value = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return null
                if (uri.scheme != "http" && uri.scheme != "https") return null
                val path = runCatching { uri.path?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
                if (path.endsWith(".m3u") || path.endsWith(".m3u8")) uri.toString() else null
            }
            Intent.ACTION_SEND -> {
                // Only plain-text playlist URLs are supported; stream/content extras are ignored.
                if (intent.hasExtra(Intent.EXTRA_STREAM)) return null
                if (intent.type?.lowercase(Locale.ROOT) !in SEND_MIME_TYPES) return null
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            }
            else -> null
        } ?: return null
        return QrPlaylistParser.parsePayload(value)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        val SEND_MIME_TYPES = setOf(
            "text/plain",
            "text/x-mpegurl",
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "audio/x-mpegurl",
            "audio/mpegurl",
        )
    }
}
