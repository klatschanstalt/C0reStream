package pw.c0re.stream.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.media3.session.MediaController

/**
 * App-weiter Zustand fuer den Mini-Player: aktiver Titel und der
 * MediaController zur Steuerung aus jedem Screen.
 */
object PlayerHub {
    var title = mutableStateOf<String?>(null)
    var controller = mutableStateOf<MediaController?>(null)

    fun close() {
        controller.value?.stop()
        controller.value?.release()
        controller.value = null
        title.value = null
    }
}
