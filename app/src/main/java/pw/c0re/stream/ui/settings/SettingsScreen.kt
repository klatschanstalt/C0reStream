package pw.c0re.stream.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val updateInstallHint = stringResource(R.string.settings_update_install)
    val updateVerifyFailed = stringResource(R.string.settings_update_verify_failed)

    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check(BuildConfig.VERSION_NAME)
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

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { vm.setThemeMode(item) },
                            label = { Text(themeLabel(item)) },
                        )
                    }
                }
                Text(stringResource(R.string.settings_brand), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brand.entries.forEach { item ->
                        FilterChip(
                            selected = brand == item,
                            onClick = { vm.setBrand(item) },
                            label = {
                                Text(
                                    stringResource(
                                        if (item == Brand.HAM3L) R.string.brand_cyan else R.string.brand_ember,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_playback), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_buffer), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_buffer_hint), style = MaterialTheme.typography.bodyMedium)
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
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_refresh), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_auto_refresh), Modifier.weight(1f))
                    Switch(checked = settings.autoRefresh, onCheckedChange = vm::setAutoRefresh)
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
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
                BackupControls()
                Text(stringResource(R.string.backup_export_m3u_warning), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onExportBackup) { Text(stringResource(R.string.settings_export_m3u)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_http_warning), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { logSnapshot = AppLog.lines }) {
                    Text(stringResource(R.string.settings_log_refresh))
                }
                logSnapshot.takeLast(20).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall)
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
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.AMOLED -> R.string.theme_amoled
    },
)
