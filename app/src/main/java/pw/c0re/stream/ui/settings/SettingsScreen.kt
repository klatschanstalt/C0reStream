package pw.c0re.stream.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pw.c0re.stream.BuildConfig
import pw.c0re.stream.R
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.AppSettings
import pw.c0re.stream.data.UpdateChecker
import pw.c0re.stream.data.UpdateInstaller
import pw.c0re.stream.ui.backup.BackupControls
import pw.c0re.stream.ui.components.C0reStreamWordmark
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.theme.Brand
import pw.c0re.stream.ui.theme.ThemeMode

@Composable
fun SettingsScreen(onExportBackup: () -> Unit = {}, onOpenProfiles: () -> Unit = {}, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.DARK)
    val brand = runCatching { Brand.valueOf(settings.brand) }.getOrDefault(Brand.HAM3L)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var logSnapshot by remember { mutableStateOf(AppLog.lines) }
    var showResetDialog by remember { mutableStateOf(false) }
    val updateInstallHint = stringResource(R.string.settings_update_install)
    val updateVerifyFailed = stringResource(R.string.settings_update_verify_failed)

    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check(BuildConfig.VERSION_NAME)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_defaults_title)) },
            text = { Text(stringResource(R.string.settings_reset_defaults_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetToDefaults()
                    showResetDialog = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        C0reStreamWordmark()
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onOpenProfiles) { Text(stringResource(R.string.profiles_title)) }

        updateInfo?.let { update ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_update_available, update.latestVersion),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    update.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (updateBusy) {
                        CircularProgressIndicator()
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                updateBusy = true
                                updateStatus = null
                                when (val result = UpdateInstaller.downloadAndVerify(context, update)) {
                                    is UpdateInstaller.Result.Ready -> {
                                        updateStatus = updateInstallHint
                                        runCatching { UpdateInstaller.install(context, result.file) }
                                            .onFailure { updateStatus = updateVerifyFailed }
                                    }
                                    is UpdateInstaller.Result.Failure -> {
                                        updateStatus = updateVerifyFailed
                                        AppLog.log("Update", result.reason)
                                    }
                                }
                                updateBusy = false
                            }
                        }) { Text(stringResource(R.string.settings_update_download)) }
                    }
                    updateStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // General
        SettingsCategory(
            stringResource(R.string.settings_cat_general),
            stringResource(R.string.settings_cat_general_hint),
        ) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            ChipRow(
                values = ThemeMode.entries.map { it.name },
                selected = mode.name,
                labelRes = { themeLabelRes(ThemeMode.valueOf(it)) },
                onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
            )
            Text(stringResource(R.string.settings_brand), style = MaterialTheme.typography.titleSmall)
            ChipRow(
                values = Brand.entries.map { it.name },
                selected = brand.name,
                labelRes = { brandLabelRes(Brand.valueOf(it)) },
                onSelect = { vm.setBrand(Brand.valueOf(it)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_expert_mode), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_expert_mode_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.expertMode, onCheckedChange = { vm.setExpertMode(it) })
            }
        }
        Spacer(Modifier.height(16.dp))

        // Video
        SettingsCategory(
            stringResource(R.string.settings_cat_video),
            stringResource(R.string.settings_cat_video_hint),
        ) {
            Text(stringResource(R.string.video_mode), style = MaterialTheme.typography.titleSmall)
            ChipRow(
                values = AppSettings.VIDEO_MODES,
                selected = settings.videoMode,
                labelRes = ::videoModeLabelRes,
                onSelect = { vm.setVideoMode(it) },
            )
            TextButton(onClick = { vm.resetVideo() }) {
                Text(stringResource(R.string.player_reset_video))
            }
        }
        Spacer(Modifier.height(16.dp))

        // Audio
        SettingsCategory(
            stringResource(R.string.settings_cat_audio),
            stringResource(R.string.settings_cat_audio_hint),
        ) {
            var langText by remember(settings.audioLanguage) { mutableStateOf(settings.audioLanguage) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = langText,
                    onValueChange = { langText = it.take(64) },
                    label = { Text(stringResource(R.string.player_audio_language)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = { vm.setAudioLanguage(langText.trim()) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
            if (settings.expertMode) {
                Text(stringResource(R.string.player_audio_offset), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_audio_offset_hint), style = MaterialTheme.typography.bodySmall)
                var offsetText by remember(settings.audioOffsetMs) { mutableStateOf(settings.audioOffsetMs.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { text -> offsetText = text.filter { it == '-' || it.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.player_audio_offset)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { vm.setAudioOffsetMs(offsetText.toIntOrNull() ?: 0) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Decoder (expert only)
        if (settings.expertMode) {
            SettingsCategory(
                stringResource(R.string.settings_cat_decoder),
                stringResource(R.string.settings_cat_decoder_hint),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.player_hw_decoder), Modifier.weight(1f))
                    Switch(checked = settings.hwDecoder, onCheckedChange = { vm.setHwDecoder(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.player_decoder_fallback), Modifier.weight(1f))
                    Switch(checked = settings.decoderFallback, onCheckedChange = { vm.setDecoderFallback(it) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Subtitles
        SettingsCategory(
            stringResource(R.string.settings_cat_subtitles),
            stringResource(R.string.settings_cat_subtitles_hint),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_subtitles_enabled), Modifier.weight(1f))
                Switch(checked = settings.subtitlesEnabled, onCheckedChange = { vm.setSubtitlesEnabled(it) })
            }
            if (settings.expertMode) {
                var subLangText by remember(settings.subtitleLanguage) { mutableStateOf(settings.subtitleLanguage) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subLangText,
                        onValueChange = { subLangText = it.take(64) },
                        label = { Text(stringResource(R.string.settings_subtitle_language)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { vm.setSubtitleLanguage(subLangText.trim()) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Streaming
        SettingsCategory(
            stringResource(R.string.settings_cat_streaming),
            stringResource(R.string.settings_cat_streaming_hint),
        ) {
            Text(stringResource(R.string.settings_buffer), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.settings_buffer_hint), style = MaterialTheme.typography.bodySmall)
            var minText by remember(settings.minBufferSec) { mutableStateOf(settings.minBufferSec.toString()) }
            var maxText by remember(settings.maxBufferSec) { mutableStateOf(settings.maxBufferSec.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.settings_buffer_min)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.settings_buffer_max)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            TextButton(onClick = {
                vm.setBuffer(minText.toIntOrNull() ?: 50, maxText.toIntOrNull() ?: 120)
            }) { Text(stringResource(R.string.action_save)) }
        }
        Spacer(Modifier.height(16.dp))

        // Network (expert only)
        if (settings.expertMode) {
            SettingsCategory(
                stringResource(R.string.settings_cat_network),
                stringResource(R.string.settings_cat_network_hint),
            ) {
                var timeoutText by remember(settings.connectTimeoutSec) {
                    mutableStateOf(settings.connectTimeoutSec.toString())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeoutText,
                        onValueChange = { timeoutText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.settings_connect_timeout)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { vm.setConnectTimeoutSec(timeoutText.toIntOrNull() ?: 15) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Playback
        SettingsCategory(
            stringResource(R.string.settings_cat_playback),
            stringResource(R.string.settings_cat_playback_hint),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_fullscreen), Modifier.weight(1f))
                Switch(checked = settings.fullscreen, onCheckedChange = { vm.setFullscreen(it) })
            }
        }
        Spacer(Modifier.height(16.dp))

        // Gestures
        SettingsCategory(
            stringResource(R.string.settings_cat_gestures),
            stringResource(R.string.settings_cat_gestures_hint),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gesture_seek), Modifier.weight(1f))
                Switch(checked = settings.gestureSeek, onCheckedChange = { vm.setGestureSeek(it) })
            }
        }
        Spacer(Modifier.height(16.dp))

        // Playlists
        SettingsCategory(
            stringResource(R.string.settings_cat_playlists),
            stringResource(R.string.settings_cat_playlists_hint),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto_refresh), Modifier.weight(1f))
                Switch(checked = settings.autoRefresh, onCheckedChange = { vm.setAutoRefresh(it) })
            }
            var hoursText by remember(settings.refreshHours) { mutableStateOf(settings.refreshHours.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.settings_refresh_hours)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = { vm.setRefreshHours(hoursText.toIntOrNull() ?: 12) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // EPG
        SettingsCategory(
            stringResource(R.string.settings_cat_epg),
            stringResource(R.string.settings_cat_epg_hint),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_epg_enabled), Modifier.weight(1f))
                Switch(checked = settings.epgEnabled, onCheckedChange = { vm.setEpgEnabled(it) })
            }
        }
        Spacer(Modifier.height(16.dp))

        // Storage
        SettingsCategory(
            stringResource(R.string.settings_cat_storage),
            stringResource(R.string.settings_cat_storage_hint),
        ) {
            BackupControls()
            Text(stringResource(R.string.backup_export_m3u_warning), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onExportBackup) { Text(stringResource(R.string.settings_export_m3u)) }
        }
        Spacer(Modifier.height(16.dp))

        // Performance (expert only)
        if (settings.expertMode) {
            SettingsCategory(
                stringResource(R.string.settings_cat_performance),
                stringResource(R.string.settings_cat_performance_hint),
            ) {
                Text(stringResource(R.string.player_performance_profile), style = MaterialTheme.typography.titleSmall)
                ChipRow(
                    values = AppSettings.PERFORMANCE_PROFILES,
                    selected = settings.performanceProfile,
                    labelRes = ::performanceProfileLabelRes,
                    onSelect = { vm.setPerformanceProfile(it) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Diagnostics
        SettingsCategory(
            stringResource(R.string.settings_cat_diagnostics),
            stringResource(R.string.settings_cat_diagnostics_hint),
        ) {
            TextButton(onClick = { logSnapshot = AppLog.lines }) {
                Text(stringResource(R.string.settings_log_refresh))
            }
            logSnapshot.takeLast(20).forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_reset_defaults), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_reset_defaults_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showResetDialog = true }) {
                    Text(stringResource(R.string.settings_reset_defaults))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
        PoweredByFooter()
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    explanation: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun ChipRow(
    values: List<String>,
    selected: String,
    labelRes: (String) -> Int,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes(value))) },
            )
        }
    }
}

private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.AMOLED -> R.string.theme_amoled
}

private fun brandLabelRes(brand: Brand): Int = when (brand) {
    Brand.HAM3L -> R.string.brand_cyan
    Brand.EMBER -> R.string.brand_ember
}

private fun videoModeLabelRes(value: String): Int = when (value) {
    "AUTO" -> R.string.video_mode_auto
    "ORIGINAL" -> R.string.video_mode_original
    "FIT" -> R.string.video_mode_fit
    "FILL" -> R.string.video_mode_fill
    "ZOOM" -> R.string.video_mode_zoom
    "STRETCH" -> R.string.video_mode_stretch
    "RATIO_16_9" -> R.string.video_mode_16_9
    "RATIO_4_3" -> R.string.video_mode_4_3
    "RATIO_21_9" -> R.string.video_mode_21_9
    else -> R.string.video_mode_auto
}

private fun performanceProfileLabelRes(value: String): Int = when (value) {
    "AUTO" -> R.string.profile_auto
    "PERFORMANCE" -> R.string.profile_performance
    "QUALITY" -> R.string.profile_quality
    "LOW_LATENCY" -> R.string.profile_low_latency
    "STABLE" -> R.string.profile_stable
    "POWER" -> R.string.profile_power
    else -> R.string.profile_auto
}
