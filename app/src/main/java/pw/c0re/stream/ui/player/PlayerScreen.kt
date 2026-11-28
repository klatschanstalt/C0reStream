package pw.c0re.stream.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandTopBar
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
    var sleepDeadline by remember { mutableLongStateOf(0L) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var resumedPosition by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    // One coordinator per screen instance; every delayed action checks a generation snapshot.
    val retry = remember { PlaybackRetryCoordinator(scope) }
    var mediaGeneration by remember { mutableLongStateOf(0L) }
    val liveLabel = stringResource(R.string.player_live)
    val showControlsLabel = stringResource(R.string.player_show_controls)
    val orientation = LocalConfiguration.current.orientation

    // Compact chrome auto-hides during playback. No animation is used: the state change is an
    // immediate composition change, which is also the correct behaviour under reduced motion.
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsToken by remember { mutableIntStateOf(0) }

    fun showControls() {
        controlsVisible = true
        controlsToken++
    }

    LaunchedEffect(playing) { showControls() }
    LaunchedEffect(controlsVisible, controlsToken, playing) {
        if (!controlsVisible || !playing) return@LaunchedEffect
        delay(4_000)
        controlsVisible = false
    }

    val effectiveUrl = channel?.url ?: pending?.url ?: PlayerHub.streamUrl
    val effectiveTitle = channel?.name ?: pending?.title ?: PlayerHub.title
    val effectiveMediaKey = if (channel != null) null else pending?.mediaKey ?: PlayerHub.mediaKey
    val effectiveKind = if (channel != null) "live" else pending?.kind ?: PlayerHub.kind
    val effectivePlaylistId = channel?.playlistId ?: pending?.playlistId ?: PlayerHub.playlistId
    // RTSP/RTP/UDP sources are playable but cannot be recorded by the OkHttp-based recorder.
    val effectiveStream = effectiveUrl?.let(NetworkPolicy::parseStreamUrl)
    val canRecord = effectiveStream?.isHttp == true

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
            retry.onStoppedOrReleased()
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
            pipActivity.setPictureInPictureParams(pipParams(playing))
        }
    }

    // Load the selected media item and restore a saved VOD position. Every load starts a new
    // media generation; pending retries/progress writes for the previous medium are cancelled.
    LaunchedEffect(controller, channel?.id, pending?.url, effectiveUrl, currentId) {
        val player = controller ?: return@LaunchedEffect
        val url = effectiveUrl
        if (url == null || NetworkPolicy.parseStreamUrl(url) == null) {
            retry.invalidatePending()
            playbackError = "Invalid stream URL"
            return@LaunchedEffect
        }
        val loadKey = "$url|${effectiveMediaKey.orEmpty()}"
        if (retry.isActive(loadKey, player)) return@LaunchedEffect
        retry.onMediaChanged(loadKey, player)
        mediaGeneration = retry.generation
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
        retry.onPlaybackRequest()
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

    // Persist VOD progress every five seconds while playing. The generation snapshot prevents a
    // stale loop from writing the old medium's position from a controller that is now playing B.
    LaunchedEffect(controller, mediaGeneration, effectiveMediaKey, channel?.id) {
        if (effectiveKind == "live" || effectiveMediaKey == null) return@LaunchedEffect
        val player = controller ?: return@LaunchedEffect
        val generation = retry.generationFor(player) ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (!retry.isMediumCurrent(generation)) return@LaunchedEffect
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

    // Absolute deadline: switching medium does not restart or silently extend the timer, and a
    // pending timer can never pause a different medium than the one it was set for.
    LaunchedEffect(controller, mediaGeneration, sleepDeadline) {
        if (sleepDeadline <= 0L) return@LaunchedEffect
        val player = controller ?: return@LaunchedEffect
        val generation = retry.generationFor(player) ?: return@LaunchedEffect
        val remaining = sleepDeadline - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        if (!retry.isMediumCurrent(generation)) return@LaunchedEffect
        player.pause()
        sleepDeadline = 0L
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
                retry.scheduleRetry(player) {
                    runCatching {
                        player.prepare()
                        player.play()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    retry.onReady()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            retry.onPlayerChanged(null)
        }
    }

    fun zap(step: Int) {
        val list = siblings
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == currentId }
        val next = list[((index + step) % list.size + list.size) % list.size]
        // Kill pending work for A immediately; do not wait for B's asynchronous channel load.
        retry.invalidatePending()
        currentId = next.id
        PlayerHub.channelId = next.id
    }

    val mode = prefs.videoMode
    // Media3 AspectRatioFrameLayout exposes FIT (contain, no crop), FILL (stretch, distorted),
    // ZOOM (cover, cropped) and fixed-width/height. There is no "cover without cropping" mode:
    // FILL therefore uses FIT (letterbox/pillarbox) as the closest undistorted behaviour, while
    // ZOOM is the only mode that crops. STRETCH is the only mode that deliberately distorts.
    val resizeMode = when (mode) {
        VideoMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        VideoMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
                VideoMode.ORIGINAL -> naturalRatio
                else -> containerRatio
            }
            // ORIGINAL and forced-ratio modes get a centred frame bounded by BoxWithConstraints;
            // all other modes use the full edge-to-edge surface. System bars and cutouts pad only
            // the overlay chrome (overlayInsets below), never this video surface, so immersive
            // mode, landscape and external sizing constraints cannot change the resize mapping.
            val sizedFrame = mode.forcedAspectRatio != null || mode == VideoMode.ORIGINAL
            val frameModifier = if (sizedFrame && frameRatio > 0f) {
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

        val error = playbackError ?: hubError

        // Transparent tap-to-reveal layer sits above the Surface only while chrome is hidden.
        if (!controlsVisible && error == null && controller != null) {
            Box(
                Modifier.fillMaxSize()
                    .clickable(onClick = { showControls() })
                    .semantics(mergeDescendants = true) {
                        contentDescription = showControlsLabel
                        role = Role.Button
                    },
            )
        }

        if (controller == null) {
            BrandBackdrop(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BrandCard(modifier = Modifier.widthIn(max = 280.dp)) {
                        Text(
                            stringResource(R.string.player_connecting),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (error != null) {
            BrandCard(
                modifier = Modifier.align(Alignment.Center)
                    .widthIn(max = 320.dp)
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.player_failed, playerErrorText(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                BrandPrimaryButton(
                    text = stringResource(R.string.action_retry),
                    onClick = {
                        val player = controller
                        if (player != null && retry.retryNow(player, {
                            runCatching {
                                player.prepare()
                                player.play()
                            }
                        })) {
                            playbackError = null
                        }
                    },
                )
            }
        }

        val overlayInsets = Modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .windowInsetsPadding(WindowInsets.displayCutout)

        Column(
            modifier = overlayInsets
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 76.dp)
                .widthIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDiagnostics) {
                BrandCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.diagnostics_title),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DiagnosticLine(R.string.diagnostics_fps, String.format(Locale.ROOT, "%.1f", diagnostics.fps))
                    DiagnosticLine(R.string.diagnostics_bitrate, "${diagnostics.bitrateKbps} kbps")
                    DiagnosticLine(
                        R.string.diagnostics_resolution,
                        if (diagnostics.videoWidth > 0 && diagnostics.videoHeight > 0) {
                            buildString {
                                append(diagnostics.videoWidth).append('x').append(diagnostics.videoHeight)
                                // Existing label only: decoded size, display aspect ratio and HDR
                                // description are all measured values, never stream locations.
                                val sizeInfo = diagnostics.videoSizeInfo.ifBlank {
                                    diagnostics.displayAspectRatio
                                        .takeIf { it > 0f }
                                        ?.let { String.format(Locale.ROOT, "%.2f:1", it) }
                                        .orEmpty()
                                }
                                if (sizeInfo.isNotBlank()) append(' ').append(sizeInfo)
                                if (diagnostics.colorInfo.isNotBlank()) append(' ').append(diagnostics.colorInfo)
                            }
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

            recording?.let { name ->
                BrandCard(modifier = Modifier.fillMaxWidth()) {
                    BrandBadge(text = stringResource(R.string.player_record))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.player_recording, name),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        resumedPosition?.let { position ->
            BrandCard(
                modifier = overlayInsets
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .widthIn(max = 280.dp),
            ) {
                Text(
                    stringResource(R.string.player_resumed, formatTime(position)),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (controlsVisible) {
            val chromeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            val chromeShape = RoundedCornerShape(16.dp)
            val iconColor = MaterialTheme.colorScheme.onSurface

            Row(
                modifier = overlayInsets
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .revealControls { showControls() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = chromeColor, shape = chromeShape, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                                tint = iconColor,
                            )
                        }
                        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            if (effectiveTitle != null) {
                                Text(
                                    effectiveTitle.orEmpty(),
                                    modifier = Modifier.widthIn(max = 160.dp).padding(end = 4.dp),
                                    color = iconColor,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (effectiveKind == "live") {
                                BrandBadge(text = liveLabel)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = overlayInsets
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .revealControls { showControls() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = chromeColor, shape = chromeShape, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { showPlaybackOptions = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                stringResource(R.string.video_mode),
                                tint = iconColor,
                            )
                        }
                        IconButton(
                            onClick = { showDiagnostics = !showDiagnostics },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                stringResource(R.string.diagnostics_title),
                                tint = iconColor,
                            )
                        }
                        IconButton(
                            onClick = { immersive = !immersive },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                if (immersive) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                stringResource(R.string.player_fullscreen),
                                tint = iconColor,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = overlayInsets
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .revealControls { showControls() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = chromeColor, shape = chromeShape, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (siblings.isNotEmpty()) {
                            IconButton(onClick = { zap(-1) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    stringResource(R.string.action_previous_channel),
                                    tint = iconColor,
                                )
                            }
                            IconButton(onClick = { zap(1) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    stringResource(R.string.action_next_channel),
                                    tint = iconColor,
                                )
                            }
                        }
                        IconButton(
                            onClick = { showTracks = true },
                            enabled = controller != null,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Audiotrack,
                                stringResource(R.string.player_tracks),
                                tint = iconColor,
                            )
                        }
                        IconButton(
                            onClick = { showSleep = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Bedtime,
                                stringResource(R.string.player_sleep_timer),
                                tint = iconColor,
                            )
                        }
                        IconButton(
                            enabled = recording != null || canRecord,
                            onClick = {
                                if (recording != null) {
                                    RecordingManager.stop()
                                } else if (canRecord) {
                                    val url = effectiveUrl
                                    if (NetworkPolicy.isRecordableStream(url)) {
                                        scope.launch {
                                            val source = vm.sourceFor(effectivePlaylistId)
                                            RecordingManager.start(
                                                context,
                                                url,
                                                effectiveTitle,
                                                source?.userAgent,
                                                source?.useProxy ?: false,
                                                source?.proxyHost,
                                                source?.proxyPort,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                if (recording != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                stringResource(R.string.player_record),
                                tint = if (recording != null) MaterialTheme.colorScheme.error else iconColor,
                            )
                        }
                        IconButton(
                            enabled = pipSupported && controller != null,
                            onClick = { runCatching { activity?.enterPictureInPictureMode(pipParams(false)) } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                stringResource(R.string.action_pip),
                                tint = iconColor,
                            )
                        }
                    }
                }
            }
        }
    }

    val error = playbackError ?: hubError
    LaunchedEffect(error) { if (error != null) showControls() }

    controller?.let { activeController ->
        if (showTracks) {
            TrackDialog(controller = activeController, onDismiss = { showTracks = false })
        }
    }

    if (showPlaybackOptions) {
        // Every field uses the same scope as the settings flow above: a channel key stores a
        // sparse override, null writes the global value. Video mode and audio language apply
        // through this screen; decoder/buffering values are read by the service when it creates
        // its player. No restart or artificial delay is introduced here, so changing a setting
        // never interrupts the active stream or loses its position.
        PlaybackOptionsDialog(
            prefs = prefs,
            onDismiss = { showPlaybackOptions = false },
            onVideoMode = { value ->
                scope.launch { vm.playbackPrefs.setVideoMode(value, preferenceKey) }
            },
            onHardwareDecoder = { value ->
                scope.launch { vm.playbackPrefs.setHardwareDecoder(value, preferenceKey) }
            },
            onDecoderFallback = { value ->
                scope.launch { vm.playbackPrefs.setDecoderFallback(value, preferenceKey) }
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
                scope.launch { vm.playbackPrefs.setAudioOffsetMs(value, preferenceKey) }
            },
            onPerformanceProfile = { value ->
                scope.launch { vm.playbackPrefs.setPerformanceProfile(value, preferenceKey) }
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
            modifier = Modifier.dialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                BrandTopBar(
                    title = stringResource(R.string.player_sleep_timer),
                    onBack = { showSleep = false },
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    listOf(0, 15, 30, 60, 90).forEach { minutes ->
                        BrandSecondaryButton(
                            text = if (minutes == 0) {
                                stringResource(R.string.player_sleep_off)
                            } else {
                                pluralStringResource(R.plurals.player_sleep_minutes, minutes, minutes)
                            },
                            onClick = {
                                sleepDeadline = if (minutes == 0) {
                                    0L
                                } else {
                                    System.currentTimeMillis() + minutes * 60_000L
                                }
                                showSleep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_close),
                    onClick = { showSleep = false },
                )
            },
        )
    }
}

@Composable
private fun Modifier.dialogModifier(): Modifier = this
    .widthIn(max = 520.dp)
    .windowInsetsPadding(WindowInsets.systemBars)
    .windowInsetsPadding(WindowInsets.displayCutout)

private fun Modifier.revealControls(onInteraction: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures(onTap = { onInteraction() }) }

@Composable
private fun playerErrorText(code: String?): String = when {
    code == null -> stringResource(R.string.player_error_unknown)
    code == "Invalid stream URL" -> stringResource(R.string.reason_invalid_stream_url)
    code == "Playback service unavailable" -> stringResource(R.string.player_service_unavailable)
    code.contains("IO", ignoreCase = true) || code.contains("NETWORK", ignoreCase = true) ->
        stringResource(R.string.player_error_network)
    code.contains("DECOD", ignoreCase = true) || code.contains("FORMAT", ignoreCase = true) ->
        stringResource(R.string.player_error_decode)
    else -> stringResource(R.string.player_error_unknown)
}

@Composable
private fun DiagnosticLine(labelRes: Int, value: String) {
    // Labels are plain translations; the measured value is rendered next to the label.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(labelRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        modifier = Modifier.dialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            BrandTopBar(
                title = stringResource(R.string.video_mode),
                onBack = onDismiss,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                    BrandSecondaryButton(
                        text = stringResource(label),
                        onClick = { onVideoMode(value) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BrandSecondaryButton(
                    text = stringResource(R.string.player_reset_video),
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = prefs.hardwareDecoder, onCheckedChange = onHardwareDecoder)
                    Text(
                        stringResource(R.string.player_hw_decoder),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = prefs.decoderFallback, onCheckedChange = onDecoderFallback)
                    Text(
                        stringResource(R.string.player_decoder_fallback),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = languageDraft,
                    onValueChange = { languageDraft = it },
                    label = { Text(stringResource(R.string.player_audio_language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BrandPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onAudioLanguage(languageDraft) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.player_audio_offset),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAudioOffset(prefs.audioOffsetMs - 100) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Remove, stringResource(R.string.player_audio_offset))
                    }
                    Text("${prefs.audioOffsetMs} ms", style = MaterialTheme.typography.bodyMedium)
                    IconButton(
                        onClick = { onAudioOffset(prefs.audioOffsetMs + 100) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, stringResource(R.string.player_audio_offset))
                    }
                }
                Text(
                    stringResource(R.string.player_performance_profile),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PerformanceProfile.entries.forEach { value ->
                    val label = when (value) {
                        PerformanceProfile.AUTO -> R.string.profile_auto
                        PerformanceProfile.PERFORMANCE -> R.string.profile_performance
                        PerformanceProfile.QUALITY -> R.string.profile_quality
                        PerformanceProfile.LOW_LATENCY -> R.string.profile_low_latency
                        PerformanceProfile.STABLE -> R.string.profile_stable
                        PerformanceProfile.POWER_SAVE -> R.string.profile_power
                    }
                    BrandSecondaryButton(
                        text = stringResource(label),
                        onClick = { onPerformanceProfile(value) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            BrandSecondaryButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
            )
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
        modifier = Modifier.dialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            BrandTopBar(
                title = stringResource(R.string.player_tracks),
                onBack = onDismiss,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.player_audio))
                TextButton(
                    onClick = { automatic(C.TRACK_TYPE_AUDIO) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.player_auto),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (audioGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                audioGroups.forEachIndexed { index, group ->
                    TextButton(
                        onClick = { override(C.TRACK_TYPE_AUDIO, group) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            group.getTrackFormat(0).label
                                ?: "${stringResource(R.string.player_audio)} ${index + 1}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                SectionTitle(stringResource(R.string.player_subtitles))
                TextButton(
                    onClick = {
                        controller.trackSelectionParameters = controller.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.player_subtitles_off),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (textGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                textGroups.forEachIndexed { index, group ->
                    TextButton(
                        onClick = {
                            controller.trackSelectionParameters = controller.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            override(C.TRACK_TYPE_TEXT, group)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            group.getTrackFormat(0).label
                                ?: "${stringResource(R.string.player_subtitles)} ${index + 1}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                SectionTitle(stringResource(R.string.player_quality))
                TextButton(
                    onClick = { automatic(C.TRACK_TYPE_VIDEO) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.player_auto),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (videoGroups.isEmpty()) Text(stringResource(R.string.player_no_tracks))
                videoGroups.forEachIndexed { index, group ->
                    TextButton(
                        onClick = { override(C.TRACK_TYPE_VIDEO, group) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        val format = group.getTrackFormat(0)
                        Text(
                            format.label
                                ?: "${format.width}x${format.height}".takeIf { format.width > 0 }
                                ?: "${stringResource(R.string.player_quality)} ${index + 1}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            BrandPrimaryButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
