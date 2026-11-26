package pw.c0re.stream.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.playbackStore by preferencesDataStore("ham3l_playback")

/**
 * Vordergrund-Dienst fuer die Wiedergabe: MediaSession (Lockscreen-/Kopfhoerer-Steuerung),
 * Benachrichtigung, WakeLock (kein Stream-Abbruch bei gesperrtem Display)
 * und konfigurierbare Puffer-Groessen.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val (minBuffer, maxBuffer) = runBlocking { readBufferPrefs() }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        minBuffer * 1000,
                        maxBuffer * 1000,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    )
                    .build(),
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private suspend fun readBufferPrefs(): Pair<Int, Int> {
        val prefs = playbackStore.data.first()
        val min = prefs[intPreferencesKey("min_buffer_sec")] ?: 50
        val max = prefs[intPreferencesKey("max_buffer_sec")] ?: 120
        return min to max
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
