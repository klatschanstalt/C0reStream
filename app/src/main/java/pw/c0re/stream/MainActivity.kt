package pw.c0re.stream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pw.c0re.stream.ui.C0reStreamRoot
import pw.c0re.stream.ui.theme.C0reStreamThemeFromPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedM3u = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.data?.toString()?.takeIf { pw.c0re.stream.data.NetworkPolicy.parse(it) != null }
        setContent {
            C0reStreamThemeFromPrefs {
                C0reStreamRoot(sharedM3uUrl = sharedM3u)
            }
        }
    }
}
