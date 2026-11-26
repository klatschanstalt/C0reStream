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
        val sharedM3u = intent?.data?.toString()
        setContent {
            C0reStreamThemeFromPrefs {
                C0reStreamRoot(sharedM3uUrl = sharedM3u)
            }
        }
    }
}
