package pw.c0re.stream.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pw.c0re.stream.R
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.RecordingManager
import pw.c0re.stream.data.player.PerformanceProfile
import pw.c0re.stream.data.player.PlaybackSettings
import pw.c0re.stream.data.player.VideoMode
import java.util.Locale

/**
 * Media3 player screen. Playback is owned by [PlayerHub]/PlaybackService, so leaving this
 * screen keeps background playback and the mini-player alive. Supports PiP, audio/subtitle/
 * quality selection, sleep timer, reconnection, resume and catch-up URLs, plus immersive
 * fullscreen, per-channel video scaling and compact service-side diagnostics.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val pending = PlayerHub.pending
    var currentId by remember { mutableLongStateOf(channelId) }
    val channel by vm.channelFor(currentId).collectAsState(initial = null)
    val siblings by vm.siblingsOf(channel).collectAsState(initial = emptyList())
    val preferenceKey = vm.preferenceKey(channel)
    val prefs by vm.playbackPrefs.settingsFor(preferenceKey).collectAsState(initial = PlaybackSettings())
    val controller = PlayerHub.controller
    val hubError = PlayerHub.error
    val playing = PlayerHub.isPlaying
    val diagnostics = PlayerHub.diagnostics
    val videoSize = PlayerHub.videoSize
    val recording by RecordingManager.active.collectAsState()

    var showTracks by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showPlaybackOptions by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(true) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var resumedPosition by remember { mutableStateOf<Long?>(null) }
    var loadedKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val liveLabel = stringResource(R.string.player_live)
    val orientation = LocalConfiguration.current.orientation

    val effectiveUrl = channel?.url ?: pending?.url ?: PlayerHub.streamUrl
    val effectiveTitle = channel?.name ?: pending?.title ?: PlayerHub.title
    val effectiveMediaKey = if (channel != null) null else pending?.mediaKey ?: PlayerHub.mediaKey
    val effectiveKind = if (channel != null) "live" else pending?.kind ?: PlayerHub.kind
    val effectivePlaylistId = channel?.playlistId ?: pending?.playlistId ?: PlayerHub.playlistId

    val currentMediaKey by rememberUpdatedState(effectiveMediaKey)
    val currentTitle by rememberUpdatedState(effectiveTitle)
    val currentUrl by rememberUpdatedState(effectiveUrl)
    val currentPlaylistId by rememberUpdatedState(effectivePlaylistId)
    val currentKind by rememberUpdatedState(effectiveKind)

    val pipSupported = activity != null &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        val bounds = Rect()
        if (playerView?.getGlobalVisibleRect(bounds) == true) builder.setSourceRectHint(bounds)
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    // True edge-to-edge playback: bars hidden, transient bars swipeable, cutout respected by
    // controls only. Restores the previous window state when leaving the player.
    DisposableEffect(activity, immersive) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 33) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (immersive) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT >= 33) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        PlayerHub.connect(context)
        onDispose {
            val player = PlayerHub.controller
            if (player != null) {
                vm.saveProgress(
                    currentMediaKey,
                    currentPlaylistId,
                    currentTitle.orEmpty(),
                    currentUrl,
                    player.currentPosition,
                    player.duration,
                    currentKind,
                )
            }
        }
    }

    LaunchedEffect(controller, playing, playerView, orientation) {
        val pipActivity = activity
        if (pipSupported) {
            pipActivity?.setPictureInPictureParams(pipParams(playing))
        }
    }

    // Load the selected media item and restore a saved VOD position.
    LaunchedEffect(controller, channel?.id, pending?.url, currentId) {
        val player = controller ?: return@LaunchedEffect
        val url = effectiveUrl
        if (url == null || NetworkPolicy.parse(url) == null) {
            playbackError = "Invalid stream URL"
            return@LaunchedEffect
        }
        val loadKey = "$url|${effectiveMediaKey.orEmpty()}"
        if (loadedKey == loadKey) return@LaunchedEffect
        loadedKey = loadKey
        val displayTitle = effectiveTitle ?: liveLabel
        PlayerHub.attach(
            title = displayTitle,
            channelId = channel?.id ?: currentId,
            streamUrl = url,
            mediaKey = effectiveMediaKey,
            kind = effectiveKind,
            playlistId = effectivePlaylistId,
        )
        channel?.let(vm::logWatch)
        PlayerHub.consumePending()
        playbackError = null
        retryCount = 0
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(displayTitle).build())
                .build(),
        )
        player.prepare()
        val saved = effectiveMediaKey?.let { key ->
            runCatching { vm.progressFor(key) }.getOrNull()
        }
        if (saved != null && saved.positionMs > 30_000 && saved.positionMs < saved.durationMs - 10_000) {
            player.seekTo(saved.positionMs)
            resumedPosition = saved.positionMs
        }
        player.playWhenReady = true
    }

    // Persist VOD progress every five seconds while playing.
    LaunchedEffect(controller, effectiveMediaKey, channel?.id) {
        if (effectiveKind == "live" || effectiveMediaKey == null) return@LaunchedEffect
        val player = controller ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (player.isPlaying) {
                vm.saveProgress(
                    effectiveMediaKey,
                    effectivePlaylistId,
                    effectiveTitle.orEmpty(),
                    effectiveUrl,
                    player.currentPosition,
                    player.duration,
                    effectiveKind,
                )
            }
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes <= 0) return@LaunchedEffect
        delay(sleepMinutes * 60_000L)
        controller?.pause()
        sleepMinutes = 0
    }

    LaunchedEffect(resumedPosition) {
        if (resumedPosition != null) {
            delay(3_000)
            resumedPosition = null
        }
    }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
                if (retryCount < 3) {
                    retryCount++
                    scope.launch {
                        delay(1_500L * retryCount)
                        runCatching {
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    retryCount = 0
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun zap(step: Int) {
        val list = siblings
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == currentId }
        val next = list[((index + step) % list.size + list.size) % list.size]
        currentId = next.id
        PlayerHub.channelId = next.id
    }

    val mode = prefs.videoMode
    val resizeMode = when (mode) {
        VideoMode.FILL, VideoMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
            val naturalRatio = if (videoSize.width > 0 && videoSize.height > 0) {
                val par = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                videoSize.width * par / videoSize.height
            } else {
                containerRatio
            }
            val frameRatio = mode.forcedAspectRatio ?: when (mode) {
                VideoMode.AUTO, VideoMode.ORIGINAL -> naturalRatio
                else -> containerRatio
            }
            val frameModifier = if (mode.forcedAspectRatio != null) {
                val width = minOf(maxWidth, maxHeight * frameRatio)
                Modifier.size(width, width / frameRatio).align(Alignment.Center)
            } else {
                Modifier.fillMaxSize()
            }

            if (controller != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = controller
                            useController = true
                            keepScreenOn = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setResizeMode(resizeMode)
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.player = controller
                        view.setResizeMode(resizeMode)
                    },
                    modifier = frameModifier.background(Color.Black),
                )
            }
        }

        if (controller == null) {
            Text(
                stringResource(R.string.player_connecting),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val error = playbackError ?: hubError
        if (error != null) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_failed, error), color = Color.White)
                TextButton(onClick = {
                    playbackError = null
                    retryCount = 0
                    controller?.prepare()
                    controller?.play()
                }) { Text(stringResource(R.string.action_retry)) }
            }
        }

        if (showDiagnostics) {
            Column(
                Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 64.dp, top = 4.dp)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.diagnostics_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
                DiagnosticLine(R.string.diagnostics_fps, String.format(Locale.ROOT, "%.1f", diagnostics.fps))
                DiagnosticLine(R.string.diagnostics_bitrate, "${diagnostics.bitrateKbps} kbps")
                DiagnosticLine(
                    R.string.diagnostics_resolution,
                    if (diagnostics.videoWidth > 0 && diagnostics.videoHeight > 0) {
                        "${diagnostics.videoWidth}x${diagnostics.videoHeight}"
                    } else {
                        "—"
                    },
                )
                DiagnosticLine(R.string.diagnostics_codec, diagnostics.codec.ifBlank { "—" })
                DiagnosticLine(R.string.diagnostics_decoder, diagnostics.decoder.ifBlank { "—" })
                DiagnosticLine(R.string.diagnostics_buffer, "${diagnostics.bufferMs} ms")
                DiagnosticLine(R.string.diagnostics_dropped, diagnostics.droppedFrames.toString())
                DiagnosticLine(R.string.diagnostics_audio, diagnostics.audioFormat.ifBlank { "—" })
            }
        }

        resumedPosition?.let { position ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(R.string.player_resumed, formatTime(position)),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        recording?.let { name ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 72.dp, start = 16.dp),
                tonalElevation = 4.dp,
            ) { Text(stringResource(R.string.player_recording, name), Modifier.padding(8.dp)) }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
                .align(Alignment.TopStart),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.action_back),
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { showPlaybackOptions = true }) {
                Icon(Icons.Filled.Settings, stringResource(R.string.video_mode))
            }
            FilledTonalIconButton(onClick = { showDiagnostics = !showDiagnostics }) {
                Icon(Icons.Filled.Info, stringResource(R.string.diagnostics_title))
            }
            FilledTonalIconButton(onClick = { immersive = !immersive }) {
                Icon(
                    if (immersive) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    stringResource(R.string.player_fullscreen),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (siblings.isNotEmpty()) {
                FilledTonalIconButton(onClick = { zap(-1) }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        stringResource(R.string.action_previous_channel),
                        tint = Color.White,
                    )
                }
                FilledTonalIconButton(onClick = { zap(1) }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        stringResource(R.string.action_next_channel),
                        tint = Color.White,
                    )
                }
            }
            FilledTonalIconButton(onClick = { showTracks = true }, enabled = controller != null) {
                Icon(Icons.Filled.Audiotrack, stringResource(R.string.player_tracks), tint = Color.White)
            }
            FilledTonalIconButton(onClick = { showSleep = true }) {
                Icon(Icons.Filled.Bedtime, stringResource(R.string.player_sleep_timer), tint = Color.White)
            }
            FilledTonalIconButton(enabled = effectiveUrl != null, onClick = {
                if (recording != null) {
                    RecordingManager.stop()
                } else {
                    effectiveUrl?.let { RecordingManager.start(context, it, effectiveTitle) }
                }
            }) {
                Icon(
                    if (recording != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    stringResource(R.string.player_record),
                    tint = Color.White,
                )
            }
            FilledTonalIconButton(
                enabled = pipSupported && controller != null,
                onClick = { runCatching { activity?.enterPictureInPictureMode(pipParams(false)) } },
            ) {
                Icon(
                    Icons.Filled.PictureInPictureAlt,
                    stringResource(R.string.action_pip),
                    tint = Color.White,
                )
            }
        }
    }

    controller?.let { activeController ->
        if (showTracks) {
            TrackDialog(controller = activeController, onDismiss = { showTracks = false })
        }
    }

    if (showPlaybackOptions) {
        PlaybackOptionsDialog(
            prefs = prefs,
            onDismiss = { showPlaybackOptions = false },
            onVideoMode = { value ->
                scope.launch { vm.playbackPrefs.setVideoMode(value, preferenceKey) }
            },
            onHardwareDecoder = { value ->
                scope.launch { vm.playbackPrefs.setHardwareDecoder(value) }
            },
            onDecoderFallback = { value ->
                scope.launch { vm.playbackPrefs.setDecoderFallback(value) }
            },
            onAudioLanguage = { value ->
                scope.launch { vm.playbackPrefs.setPreferredAudioLanguage(value, preferenceKey) }
                controller?.let { active ->
                    active.setTrackSelectionParameters(
                        active.trackSelectionParameters.buildUpon()
                            .setPreferredAudioLanguage(value.trim().lowercase(Locale.ROOT))
                            .build(),
                    )
                }
            },
            onAudioOffset = { value ->
                scope.launch { vm.playbackPrefs.setAudioOffsetMs(value) }
            },
            onPerformanceProfile = { value ->
                scope.launch { vm.playbackPrefs.setPerformanceProfile(value) }
            },
            onReset = {
                scope.launch {
                    if (preferenceKey != null) vm.playbackPrefs.clearChannel(preferenceKey)
                    else vm.playbackPrefs.setVideoMode(VideoMode.AUTO)
                }
            },
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text(stringResource(R.string.player_sleep_timer)) },
            text = {
                Column {
                    listOf(0, 15, 30, 60, 90).forEach { minutes ->
                        TextButton(onClick = {
                            sleepMinutes = minutes
                            showSleep = false
                        }) {
                            Text(
                                if (minutes == 0) stringResource(R.string.player_sleep_off)
                                else pluralStringResource(R.plurals.player_sleep_minutes, minutes, minutes),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleep = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun DiagnosticLine(labelRes: Int, value: String) {
    // Labels are plain translations; the measured value is rendered next to the label.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(labelRes),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PlaybackOptionsDialog(
    prefs: PlaybackSettings,
    onDismiss: () -> Unit,
    onVideoMode: (VideoMode) -> Unit,
    onHardwareDecoder: (Boolean) -> Unit,
    onDecoderFallback: (Boolean) -> Unit,
    onAudioLanguage: (String) -> Unit,
    onAudioOffset: (Long) -> Unit,
    onPerformanceProfile: (PerformanceProfile) -> Unit,
    onReset: () -> Unit,
) {
    var languageDraft by remember(prefs.preferredAudioLanguage) {
        mutableStateOf(prefs.preferredAudioLanguage)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_mode)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VideoMode.entries.forEach { value ->
                    val label = when (value) {
                        VideoMode.AUTO -> R.string.video_mode_auto
                        VideoMode.ORIGINAL -> R.string.video_mode_original
                        VideoMode.FIT -> R.string.video_mode_fit
                        VideoMode.FILL -> R.string.video_mode_fill
                        VideoMode.ZOOM -> R.string.video_mode_zoom
                        VideoMode.STRETCH -> R.string.video_mode_stretch
                        VideoMode.RATIO_16_9 -> R.string.video_mode_16_9
                        VideoMode.RATIO_4_3 -> R.string.video_mode_4_3
                        VideoMode.RATIO_21_9 -> R.string.video_mode_21_9
                    }
                    TextButton(onClick = { onVideoMode(value) }) {
                        Text(
                            stringResource(label),
                            color = if (value == prefs.videoMode) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    }
                }
                TextButton(onClick = onReset) { Text(stringResource(R.string.player_reset_video)) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = prefs.hardwareDecoder, onCheckedChange = onHardwareDecoder)
                    Text(stringResource(R.string.player_hw_decoder))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = prefs.decoderFallback, onCheckedChange = onDecoderFallback)
                    Text(stringResource(R.string.player_decoder_fallback))
                }
                OutlinedTextField(
                    value = languageDraft,
                    onValueChange = { languageDraft = it },
                    label = { Text(stringResource(R.string.player_audio_language)) },
                    singleLine = true,
                )
                TextButton(onClick = { onAudioLanguage(languageDraft) }) {
                    Text(stringResource(R.string.action_save))
                }
                Text(stringResource(R.string.player_audio_offset))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onAudioOffset(prefs.audioOffsetMs - 100) }) {
                        Icon(Icons.Filled.Remove, stringResource(R.string.player_audio_offset))
                    }
                    Text("${prefs.audioOffsetMs} ms")
                    FilledTonalIconButton(onClick = { onAudioOffset(prefs.audioOffsetMs + 100) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.player_audio_offset))
                    }
                }
                Text(stringResource(R.string.player_performance_profile))
                PerformanceProfile.entries.forEach { value ->
                    val label = when (value) {
                        PerformanceProfile.AUTO -> R.string.profile_auto
                        PerformanceProfile.PERFORMANCE -> R.string.profile_performance
                        PerformanceProfile.QUALITY -> R.string.profile_quality
                        PerformanceProfile.LOW_LATENCY -> R.string.profile_low_latency
                        PerformanceProfile.STABLE -> R.string.profile_stable
                        PerformanceProfile.POWER_SAVE -> R.string.profile_power
                    }
                    TextButton(onClick = { onPerformanceProfile(value) }) {
                        Text(
                            stringResource(label),
                            color = if (value == prefs.performanceProfile) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TrackDialog(controller: androidx.media3.session.MediaController, onDismiss: () -> Unit) {
    val tracks: Tracks = controller.currentTracks
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

    fun override(type: Int, group: Tracks.Group) {
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    fun automatic(type: Int) {
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .build()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.player_audio))
                TextButton(onClick = { automatic(C.TRACK_TYPE_AUDIO) }) {
                    Text(stringResource(R.string.player_auto))
                }
                if (audioGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                audioGroups.forEachIndexed { index, group ->
                    TextButton(onClick = { override(C.TRACK_TYPE_AUDIO, group) }) {
                        Text(
                            group.getTrackFormat(0).label
                                ?: "${stringResource(R.string.player_audio)} ${index + 1}",
                        )
                    }
                }

                SectionTitle(stringResource(R.string.player_subtitles))
                TextButton(onClick = {
                    controller.trackSelectionParameters = controller.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }) { Text(stringResource(R.string.player_subtitles_off)) }
                if (textGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                textGroups.forEachIndexed { index, group ->
                    TextButton(onClick = {
                        controller.trackSelectionParameters = controller.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()
                        override(C.TRACK_TYPE_TEXT, group)
                    }) {
                        Text(
                            group.getTrackFormat(0).label
                                ?: "${stringResource(R.string.player_subtitles)} ${index + 1}",
                        )
                    }
                }

                SectionTitle(stringResource(R.string.player_quality))
                TextButton(onClick = { automatic(C.TRACK_TYPE_VIDEO) }) {
                    Text(stringResource(R.string.player_auto))
                }
                if (videoGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                videoGroups.forEachIndexed { index, group ->
                    TextButton(onClick = { override(C.TRACK_TYPE_VIDEO, group) }) {
                        val format = group.getTrackFormat(0)
                        Text(
                            format.label
                                ?: "${format.width}x${format.height}".takeIf { format.width > 0 }
                                ?: "${stringResource(R.string.player_quality)} ${index + 1}",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
