package pw.c0re.stream.ui.favorites

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.playlistdetail.ChannelLogo

@Composable
fun FavoritesScreen(onPlay: (Long) -> Unit, vm: FavoritesViewModel = viewModel()) {
    val favorites by vm.favorites.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Favoriten", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(favorites, key = { it.id }) { f ->
                Card(onClick = { onPlay(f.channelId) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(f.logoUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).padding(end = 12.dp))
                        Text(f.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { PoweredByFooter() }
        }
    }
}
