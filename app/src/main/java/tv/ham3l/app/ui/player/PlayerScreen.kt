package tv.ham3l.app.ui.player

import android.content.ComponentName
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import tv.ham3l.app.service.PlaybackService

/**
 * Wiedergabe ueber den PlaybackService (MediaSession): Lockscreen-Steuerung,
 * WakeLock, Puffer-Einstellungen. Sender-Zapping, Tonspur-Auswahl,
 * Bild-in-Bild und Catch-up-URLs werden hier verdrahtet.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(channelId: Long, streamUrl: String?, onBack: () -> Unit, vm: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    var currentId by remember { mutableLongStateOf(channelId) }
    val channel by vm.channelFor(currentId).collectAsState(initial = null)
    val siblings by vm.siblingsOf(channel).collectAsState(initial = emptyList())
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var showTracks by remember { mutableStateOf(false) }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val activity = context as? Activity
    val pipSupported = activity != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        val bounds = Rect()
        if (playerView?.getGlobalVisibleRect(bounds) == true) builder.setSourceRectHint(bounds)
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    DisposableEffect(controller, playerView, activity) {
        val c = controller
        fun updatePip() {
            if (pipSupported) activity?.setPictureInPictureParams(pipParams(c?.isPlaying == true))
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updatePip() }
            override fun onPlayerError(error: PlaybackException) { playbackError = error.errorCodeName }
        }
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePip() }
        c?.addListener(listener)
        playerView?.addOnLayoutChangeListener(layoutListener)
        updatePip()
        onDispose {
            c?.removeListener(listener)
            playerView?.removeOnLayoutChangeListener(layoutListener)
            if (pipSupported) activity?.setPictureInPictureParams(pipParams(false))
        }
    }

    fun zap(step: Int) {
        val list = siblings
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == currentId }
        currentId = list[((idx + step) % list.size + list.size) % list.size].id
    }

    // MediaController zum Service verbinden
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ if (!future.isCancelled) runCatching { future.get() }.onSuccess { controller = it }.onFailure { playbackError = "Wiedergabedienst nicht erreichbar" } }, ContextCompat.getMainExecutor(context))
        onDispose {
            PlayerHub.close()
            future.cancel(false)
            controller?.release()
            controller = null
        }
    }

    // Kanalwechsel: neues Medium setzen (Player lebt im Service, kein Leak)
    LaunchedEffect(channel, controller) {
        val c = controller ?: return@LaunchedEffect
        val ch = channel
        val url = ch?.url ?: streamUrl ?: return@LaunchedEffect
        if (ch != null) vm.logWatch(ch)
        PlayerHub.title.value = ch?.name ?: "Ham3L TV"
        PlayerHub.controller.value = c
        playbackError = null
        c.setMediaItem(MediaItem.fromUri(url))
        c.prepare()
        c.playWhenReady = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val c = controller
        if (c != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = c
                        useController = true
                        keepScreenOn = true
                        playerView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Verbinde mit Wiedergabedienst ...", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        playbackError?.let { error ->
            Column(Modifier.align(Alignment.Center).background(Color.Black).padding(16.dp)) {
                Text("Wiedergabe fehlgeschlagen: $error", color = Color.White)
                TextButton(onClick = { playbackError = null; controller?.prepare(); controller?.play() }) { Text("Erneut versuchen") }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck", tint = Color.White)
        }
        // Sender-Zapping, Tonspur, PiP
        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { zap(-1) }) {
                Icon(Icons.Filled.SkipPrevious, "Vorheriger Kanal", tint = Color.White)
            }
            FilledTonalIconButton(onClick = { zap(1) }) {
                Icon(Icons.Filled.SkipNext, "Naechster Kanal", tint = Color.White)
            }
            FilledTonalIconButton(onClick = { showTracks = true }) {
                Icon(Icons.Filled.Audiotrack, "Tonspur", tint = Color.White)
            }
            FilledTonalIconButton(enabled = pipSupported && c != null, onClick = {
                activity?.enterPictureInPictureMode(pipParams(false))
            }) {
                Icon(Icons.Filled.PictureInPictureAlt, "Bild-in-Bild", tint = Color.White)
            }
        }
    }

    // Tonspur-Auswahl
    if (showTracks) {
        val c = controller
        AlertDialog(
            onDismissRequest = { showTracks = false },
            title = { Text("Tonspur") },
            text = {
                Column {
                    val audioGroups = c?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
                    if (audioGroups.isEmpty()) {
                        Text("Keine alternativen Tonspuren gefunden.")
                    }
                    audioGroups.forEachIndexed { i, group ->
                        TextButton(onClick = {
                            c?.let { controller ->
                            controller.trackSelectionParameters = controller.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            }
                            showTracks = false
                        }) { Text(group.getTrackFormat(0).label ?: ("Tonspur " + (i + 1))) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTracks = false }) { Text("Schliessen") } },
        )
    }
}
