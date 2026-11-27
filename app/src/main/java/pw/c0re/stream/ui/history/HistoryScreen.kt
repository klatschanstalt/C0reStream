package pw.c0re.stream.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.R
import pw.c0re.stream.data.db.HistoryEntity
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandEmptyState
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.components.rememberResponsivePadding
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onPlay: (Long) -> Unit, vm: HistoryViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    val horizontalPadding = rememberResponsivePadding()

    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.history_title),
                actions = {
                    BrandBadge(history.size.toString())
                    IconButton(onClick = vm::clear) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_clear))
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                BrandEmptyState(stringResource(R.string.watchlist_empty))
            } else {
                AdaptivePane(
                    modifier = Modifier.weight(1f),
                    compact = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(history, key = { it.id }) { entry ->
                                HistoryCard(entry, onPlay)
                            }
                            item { PoweredByFooter() }
                        }
                    },
                    medium = { HistoryGrid(history, onPlay) },
                    expanded = { HistoryGrid(history, onPlay) },
                )
            }
        }
    }
}

@Composable
private fun HistoryGrid(history: List<HistoryEntity>, onPlay: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(history, key = { it.id }) { entry ->
            HistoryCard(entry, onPlay)
        }
        item { PoweredByFooter() }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntity, onPlay: (Long) -> Unit) {
    BrandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onPlay(entry.channelId) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateFormat.getDateTimeInstance().format(Date(entry.watchedAt)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
