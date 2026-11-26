package pw.c0re.stream.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.ui.components.PoweredByFooter
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onPlay: (Long) -> Unit, vm: HistoryViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Verlauf", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = vm::clear) { Icon(Icons.Filled.DeleteSweep, "Leeren") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(history, key = { it.id }) { h ->
                Card(onClick = { onPlay(h.channelId) }, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(h.name, style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance().format(Date(h.watchedAt)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { PoweredByFooter() }
        }
    }
}
