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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.ui.components.C0reStreamWordmark
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.theme.ThemeMode

@Composable
fun SettingsScreen(onExportBackup: () -> Unit = {}, vm: SettingsViewModel = viewModel()) {
    val mode by vm.themeMode.collectAsState()
    val brand by vm.brand.collectAsState()
    val minBuffer by vm.minBufferSec.collectAsState()
    val maxBuffer by vm.maxBufferSec.collectAsState()
    var updateInfo by remember { mutableStateOf<pw.c0re.stream.data.UpdateChecker.UpdateInfo?>(null) }
    var logSnapshot by remember { mutableStateOf(pw.c0re.stream.data.AppLog.lines) }

    LaunchedEffect(Unit) {
        updateInfo = pw.c0re.stream.data.UpdateChecker.check("1.2.0")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        C0reStreamWordmark()
        Spacer(Modifier.height(20.dp))

        updateInfo?.let { u ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Update verfuegbar: " + u.latestVersion, style = MaterialTheme.typography.titleMedium)
                    u.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Erscheinungsbild", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { vm.setThemeMode(m) }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("Brand-Theme", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pw.c0re.stream.ui.theme.Brand.entries.forEach { b ->
                        FilterChip(selected = brand == b, onClick = { vm.setBrand(b) }, label = { Text(if (b == pw.c0re.stream.ui.theme.Brand.HAM3L) "C0reStream (Cyan)" else "C0reStream Ember") })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wiedergabe-Puffer", style = MaterialTheme.typography.titleMedium)
                Text("Bei ruckelnden Streams hoehere Werte probieren. Wirkt nach App-Neustart.", style = MaterialTheme.typography.bodyMedium)
                var minTxt by remember(minBuffer) { mutableStateOf(minBuffer.toString()) }
                var maxTxt by remember(maxBuffer) { mutableStateOf(maxBuffer.toString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(minTxt, { minTxt = it }, label = { Text("Min. (Sek.)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(maxTxt, { maxTxt = it }, label = { Text("Max. (Sek.)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                TextButton(onClick = { vm.setBuffer(minTxt.toIntOrNull() ?: 50, maxTxt.toIntOrNull() ?: 120) }) { Text("Speichern") }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Backup & Export", style = MaterialTheme.typography.titleMedium)
                Text("Alle Playlists als M3U-Datei exportieren und teilen.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextButton(onClick = onExportBackup) { Text("Backup erstellen") }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnose", style = MaterialTheme.typography.titleMedium)
                Text("Lokales Protokoll (bleibt auf dem Geraet).", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { logSnapshot = pw.c0re.stream.data.AppLog.lines }) { Text("Protokoll aktualisieren") }
                logSnapshot.takeLast(20).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("C0reStream TV v1.2.0", style = MaterialTheme.typography.titleMedium)
                Text("Eigenstaendiger IPTV-Client fuer M3U, Xtream-Codes, VOD und Serien mit EPG, Catch-up, Favoriten, Verlauf, Suche, Mini-Player und Proxy-Unterstuetzung. Powered by C0reStream.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        PoweredByFooter()
    }
}
