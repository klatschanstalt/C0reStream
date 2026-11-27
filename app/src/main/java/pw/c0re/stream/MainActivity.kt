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
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.ui.C0reStreamRoot
import pw.c0re.stream.ui.player.PlayerHub
import pw.c0re.stream.ui.theme.C0reStreamThemeFromPrefs

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

    private fun extractSharedPlaylist(intent: Intent?): String? {
        if (intent == null) return null
        val value = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            else -> null
        } ?: return null
        if (NetworkPolicy.parse(value) == null) return null
        if (intent.action == Intent.ACTION_SEND) return value
        val path = runCatching { intent.data?.path?.lowercase() }.getOrNull().orEmpty()
        return value.takeIf { path.endsWith(".m3u") || path.endsWith(".m3u8") }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
