package pw.c0re.stream.ui.favorites

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import pw.c0re.stream.R
import pw.c0re.stream.data.db.FavoriteEntity
import pw.c0re.stream.ui.components.AdaptivePane
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandEmptyState
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.PoweredByFooter
import pw.c0re.stream.ui.components.rememberResponsivePadding
import pw.c0re.stream.ui.playlistdetail.ChannelLogo

@Composable
fun FavoritesScreen(onPlay: (Long) -> Unit, vm: FavoritesViewModel = viewModel()) {
    val favorites by vm.favorites.collectAsState()
    val horizontalPadding = rememberResponsivePadding()

    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.favorites_title),
                actions = { BrandBadge(favorites.size.toString()) },
            )
            Spacer(Modifier.height(12.dp))
            if (favorites.isEmpty()) {
                BrandEmptyState(stringResource(R.string.watchlist_empty))
            } else {
                AdaptivePane(
                    modifier = Modifier.weight(1f),
                    compact = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(favorites, key = { it.id }) { favorite ->
                                FavoriteCard(favorite, onPlay)
                            }
                            item { PoweredByFooter() }
                        }
                    },
                    medium = { FavoritesGrid(favorites, onPlay) },
                    expanded = { FavoritesGrid(favorites, onPlay) },
                )
            }
        }
    }
}

@Composable
private fun FavoritesGrid(favorites: List<FavoriteEntity>, onPlay: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(favorites, key = { it.id }) { favorite ->
            FavoriteCard(favorite, onPlay)
        }
        item { PoweredByFooter() }
    }
}

@Composable
private fun FavoriteCard(favorite: FavoriteEntity, onPlay: (Long) -> Unit) {
    BrandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onPlay(favorite.channelId) },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                favorite.logoUrl,
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            )
            Text(
                favorite.name,
                Modifier.padding(start = 12.dp).weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
