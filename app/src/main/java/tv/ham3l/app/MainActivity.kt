package tv.ham3l.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tv.ham3l.app.ui.Ham3LRoot
import tv.ham3l.app.ui.theme.Ham3LThemeFromPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedM3u = intent?.data?.toString()
        setContent {
            Ham3LThemeFromPrefs {
                Ham3LRoot(sharedM3uUrl = sharedM3u)
            }
        }
    }
}
