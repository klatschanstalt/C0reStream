package pw.c0re.stream.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
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
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.rememberResponsivePadding
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandLoading
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandSectionTitle
import pw.c0re.stream.ui.components.BrandTopBar
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
            text = {
                Text(
                    stringResource(R.string.settings_reset_defaults_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        vm.resetToDefaults()
                        showResetDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            dismissButton = {
                BrandSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showResetDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = rememberResponsivePadding().coerceAtMost(maxWidth / 8)
        BrandBackdrop(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandTopBar(
                    title = stringResource(R.string.nav_settings),
                    onBack = null,
                    actions = {},
                )
                C0reStreamWordmark()

                BrandSecondaryButton(
                    text = stringResource(R.string.profiles_title),
                    onClick = onOpenProfiles,
                    modifier = Modifier.fillMaxWidth(),
                )

                updateInfo?.let { update ->
                    BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_update_available, update.latestVersion),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            update.notes?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (updateBusy) {
                                BrandLoading(label = stringResource(R.string.vod_loading))
                            } else {
                                BrandPrimaryButton(
                                    text = stringResource(R.string.settings_update_download),
                                    onClick = {
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
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            updateStatus?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                val categories = listOf<@Composable () -> Unit>(
                    { // General
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_general),
                            explanation = stringResource(R.string.settings_cat_general_hint),
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
                            SwitchSetting(
                                label = stringResource(R.string.settings_expert_mode),
                                hint = stringResource(R.string.settings_expert_mode_hint),
                                checked = settings.expertMode,
                                onCheckedChange = { vm.setExpertMode(it) },
                            )
                        }
                    },
                    { // Video
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_video),
                            explanation = stringResource(R.string.settings_cat_video_hint),
                        ) {
                            Text(stringResource(R.string.video_mode), style = MaterialTheme.typography.titleSmall)
                            ChipRow(
                                values = AppSettings.VIDEO_MODES,
                                selected = settings.videoMode,
                                labelRes = ::videoModeLabelRes,
                                onSelect = { vm.setVideoMode(it) },
                            )
                            BrandSecondaryButton(
                                text = stringResource(R.string.player_reset_video),
                                onClick = { vm.resetVideo() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    { // Audio
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_audio),
                            explanation = stringResource(R.string.settings_cat_audio_hint),
                        ) {
                            var langText by remember(settings.audioLanguage) { mutableStateOf(settings.audioLanguage) }
                            TextFieldSetting(
                                value = langText,
                                onValueChange = { langText = it.take(64) },
                                label = stringResource(R.string.player_audio_language),
                                onSave = { vm.setAudioLanguage(langText.trim()) },
                            )
                            if (settings.expertMode) {
                                Text(stringResource(R.string.player_audio_offset), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.settings_audio_offset_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                var offsetText by remember(settings.audioOffsetMs) {
                                    mutableStateOf(settings.audioOffsetMs.toString())
                                }
                                TextFieldSetting(
                                    value = offsetText,
                                    onValueChange = { text -> offsetText = text.filter { it == '-' || it.isDigit() }.take(6) },
                                    label = stringResource(R.string.player_audio_offset),
                                    onSave = { vm.setAudioOffsetMs(offsetText.toIntOrNull() ?: 0) },
                                )
                            }
                        }
                    },
                    { // Decoder (expert only)
                        if (settings.expertMode) {
                            SettingsCategory(
                                title = stringResource(R.string.settings_cat_decoder),
                                explanation = stringResource(R.string.settings_cat_decoder_hint),
                            ) {
                                SwitchSetting(
                                    label = stringResource(R.string.player_hw_decoder),
                                    checked = settings.hwDecoder,
                                    onCheckedChange = { vm.setHwDecoder(it) },
                                )
                                SwitchSetting(
                                    label = stringResource(R.string.player_decoder_fallback),
                                    checked = settings.decoderFallback,
                                    onCheckedChange = { vm.setDecoderFallback(it) },
                                )
                            }
                        }
                    },
                    { // Subtitles
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_subtitles),
                            explanation = stringResource(R.string.settings_cat_subtitles_hint),
                        ) {
                            SwitchSetting(
                                label = stringResource(R.string.settings_subtitles_enabled),
                                checked = settings.subtitlesEnabled,
                                onCheckedChange = { vm.setSubtitlesEnabled(it) },
                            )
                            if (settings.expertMode) {
                                var subLangText by remember(settings.subtitleLanguage) {
                                    mutableStateOf(settings.subtitleLanguage)
                                }
                                TextFieldSetting(
                                    value = subLangText,
                                    onValueChange = { subLangText = it.take(64) },
                                    label = stringResource(R.string.settings_subtitle_language),
                                    onSave = { vm.setSubtitleLanguage(subLangText.trim()) },
                                )
                            }
                        }
                    },
                    { // Streaming
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_streaming),
                            explanation = stringResource(R.string.settings_cat_streaming_hint),
                        ) {
                            Text(stringResource(R.string.settings_buffer), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.settings_buffer_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            var minText by remember(settings.minBufferSec) { mutableStateOf(settings.minBufferSec.toString()) }
                            var maxText by remember(settings.maxBufferSec) { mutableStateOf(settings.maxBufferSec.toString()) }
                            OutlinedTextField(
                                value = minText,
                                onValueChange = { minText = it.filter(Char::isDigit).take(3) },
                                label = { Text(stringResource(R.string.settings_buffer_min)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = maxText,
                                onValueChange = { maxText = it.filter(Char::isDigit).take(3) },
                                label = { Text(stringResource(R.string.settings_buffer_max)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            BrandSecondaryButton(
                                text = stringResource(R.string.action_save),
                                onClick = {
                                    vm.setBuffer(minText.toIntOrNull() ?: 50, maxText.toIntOrNull() ?: 120)
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .heightIn(min = 48.dp),
                            )
                        }
                    },
                    { // Network (expert only)
                        if (settings.expertMode) {
                            SettingsCategory(
                                title = stringResource(R.string.settings_cat_network),
                                explanation = stringResource(R.string.settings_cat_network_hint),
                            ) {
                                var timeoutText by remember(settings.connectTimeoutSec) {
                                    mutableStateOf(settings.connectTimeoutSec.toString())
                                }
                                TextFieldSetting(
                                    value = timeoutText,
                                    onValueChange = { timeoutText = it.filter(Char::isDigit).take(3) },
                                    label = stringResource(R.string.settings_connect_timeout),
                                    onSave = { vm.setConnectTimeoutSec(timeoutText.toIntOrNull() ?: 15) },
                                )
                            }
                        }
                    },
                    { // Playback
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_playback),
                            explanation = stringResource(R.string.settings_cat_playback_hint),
                        ) {
                            SwitchSetting(
                                label = stringResource(R.string.player_fullscreen),
                                checked = settings.fullscreen,
                                onCheckedChange = { vm.setFullscreen(it) },
                            )
                        }
                    },
                    { // Gestures
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_gestures),
                            explanation = stringResource(R.string.settings_cat_gestures_hint),
                        ) {
                            SwitchSetting(
                                label = stringResource(R.string.settings_gesture_seek),
                                checked = settings.gestureSeek,
                                onCheckedChange = { vm.setGestureSeek(it) },
                            )
                        }
                    },
                    { // Playlists
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_playlists),
                            explanation = stringResource(R.string.settings_cat_playlists_hint),
                        ) {
                            SwitchSetting(
                                label = stringResource(R.string.settings_auto_refresh),
                                checked = settings.autoRefresh,
                                onCheckedChange = { vm.setAutoRefresh(it) },
                            )
                            var hoursText by remember(settings.refreshHours) { mutableStateOf(settings.refreshHours.toString()) }
                            TextFieldSetting(
                                value = hoursText,
                                onValueChange = { hoursText = it.filter(Char::isDigit).take(3) },
                                label = stringResource(R.string.settings_refresh_hours),
                                onSave = { vm.setRefreshHours(hoursText.toIntOrNull() ?: 12) },
                            )
                        }
                    },
                    { // EPG
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_epg),
                            explanation = stringResource(R.string.settings_cat_epg_hint),
                        ) {
                            SwitchSetting(
                                label = stringResource(R.string.settings_epg_enabled),
                                checked = settings.epgEnabled,
                                onCheckedChange = { vm.setEpgEnabled(it) },
                            )
                        }
                    },
                    { // Storage
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_storage),
                            explanation = stringResource(R.string.settings_cat_storage_hint),
                        ) {
                            BackupControls()
                            Text(
                                stringResource(R.string.backup_export_m3u_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BrandSecondaryButton(
                                text = stringResource(R.string.settings_export_m3u),
                                onClick = onExportBackup,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    { // Performance (expert only)
                        if (settings.expertMode) {
                            SettingsCategory(
                                title = stringResource(R.string.settings_cat_performance),
                                explanation = stringResource(R.string.settings_cat_performance_hint),
                            ) {
                                Text(stringResource(R.string.player_performance_profile), style = MaterialTheme.typography.titleSmall)
                                ChipRow(
                                    values = AppSettings.PERFORMANCE_PROFILES,
                                    selected = settings.performanceProfile,
                                    labelRes = ::performanceProfileLabelRes,
                                    onSelect = { vm.setPerformanceProfile(it) },
                                )
                            }
                        }
                    },
                    { // Diagnostics
                        SettingsCategory(
                            title = stringResource(R.string.settings_cat_diagnostics),
                            explanation = stringResource(R.string.settings_cat_diagnostics_hint),
                        ) {
                            BrandSecondaryButton(
                                text = stringResource(R.string.settings_log_refresh),
                                onClick = { logSnapshot = AppLog.lines },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            logSnapshot.takeLast(20).forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                )

                AdaptivePane(
                    modifier = Modifier.fillMaxWidth(),
                    compact = { SettingsCategoryColumns(categories = categories, columns = 1) },
                    medium = { SettingsCategoryColumns(categories = categories, columns = 2) },
                    expanded = { SettingsCategoryColumns(categories = categories, columns = 3) },
                )

                BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.settings_reset_defaults), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_reset_defaults_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BrandSecondaryButton(
                            text = stringResource(R.string.settings_reset_defaults),
                            onClick = { showResetDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.settings_about_text),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PoweredByFooter()
            }
        }
    }
}

@Composable
private fun SettingsCategoryColumns(categories: List<@Composable () -> Unit>, columns: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(columns) { column ->
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                categories.filterIndexed { index, _ -> index % columns == column }
                    .forEach { category -> category() }
            }
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    explanation: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrandSectionTitle(text = title)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextFieldSetting(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        BrandSecondaryButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun ChipRow(
    values: List<String>,
    selected: String,
    labelRes: (String) -> Int,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        stringResource(labelRes(value)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
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
