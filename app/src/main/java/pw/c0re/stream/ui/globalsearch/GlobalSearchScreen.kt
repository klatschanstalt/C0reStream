package pw.c0re.stream.ui.globalsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import pw.c0re.stream.R
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.gridItems
import pw.c0re.stream.ui.components.listItems
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.components.rememberResponsivePadding
import pw.c0re.stream.ui.playlistdetail.ChannelLogo
import pw.c0re.stream.ui.playlistdetail.pagingStatusItems

@Composable
fun GlobalSearchScreen(onPlay: (Long) -> Unit, vm: GlobalSearchViewModel = viewModel()) {
    val query by vm.query.collectAsState()
    val results = vm.results.collectAsLazyPagingItems()
    val playlistNames by vm.playlistNames.collectAsState()
    val horizontalPadding = rememberResponsivePadding()

    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.search_title),
                actions = { BrandBadge(results.itemCount.toString()) },
            )
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            val emptyLabel = stringResource(R.string.search_hint)
            AdaptivePane(
                modifier = Modifier.weight(1f),
                compact = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listItems(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                            results[index]?.let { channel ->
                                SearchResultCard(channel, playlistNames[channel.playlistId] ?: "?", onPlay)
                            }
                        }
                        pagingStatusItems(results, emptyMessage = emptyLabel)
                        item(key = "footer") { PoweredByFooter() }
                    }
                },
                medium = { SearchGrid(results, playlistNames, onPlay) },
                expanded = { SearchGrid(results, playlistNames, onPlay) },
            )
        }
    }
}

@Composable
private fun SearchGrid(
    results: LazyPagingItems<ChannelEntity>,
    playlistNames: Map<Long, String>,
    onPlay: (Long) -> Unit,
) {
    val emptyLabel = stringResource(R.string.search_hint)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(count = results.itemCount, key = results.itemKey { it.id }) { index ->
            results[index]?.let { channel ->
                SearchResultCard(channel, playlistNames[channel.playlistId] ?: "?", onPlay)
            }
        }
        pagingStatusItems(results, emptyMessage = emptyLabel)
        gridItems(count = 1, key = { "footer" }) { PoweredByFooter() }
    }
}

@Composable
private fun SearchResultCard(
    channel: ChannelEntity,
    playlistName: String,
    onPlay: (Long) -> Unit,
) {
    BrandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onPlay(channel.id) },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel.logoUrl,
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    playlistName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
