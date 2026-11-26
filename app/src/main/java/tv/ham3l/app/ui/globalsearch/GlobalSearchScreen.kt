package tv.ham3l.app.ui.globalsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.ham3l.app.ui.components.PoweredByFooter
import tv.ham3l.app.ui.playlistdetail.ChannelLogo

@Composable
fun GlobalSearchScreen(onPlay: (Long) -> Unit, vm: GlobalSearchViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Alle Kanaele durchsuchen", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Mindestens 2 Zeichen ...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            singleLine = true,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(results, key = { it.first.id }) { (ch, playlistName) ->
                Card(onClick = { onPlay(ch.id) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(ch.logoUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(playlistName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { PoweredByFooter() }
        }
    }
}
