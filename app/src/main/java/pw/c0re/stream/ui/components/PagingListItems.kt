package pw.c0re.stream.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import pw.c0re.stream.R

/** Small adapters that keep paged screens concise and avoid hidden silent limits. */
fun LazyListScope.listItems(
    count: Int,
    key: ((index: Int) -> Any)? = null,
    content: @Composable LazyItemScope.(index: Int) -> Unit,
) {
    items(count = count, key = key, itemContent = content)
}

fun LazyGridScope.gridItems(
    count: Int,
    key: ((index: Int) -> Any)? = null,
    content: @Composable LazyGridItemScope.(index: Int) -> Unit,
) {
    items(count = count, key = key, itemContent = content)
}

fun <T> LazyListScope.listItems(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    content: @Composable LazyItemScope.(item: T) -> Unit,
) {
    items(items = items, key = key, itemContent = content)
}

fun <T> LazyGridScope.gridItems(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    content: @Composable LazyGridItemScope.(item: T) -> Unit,
) {
    items(items = items, key = key, itemContent = content)
}

fun <T : Any> LazyListScope.pagingStatusItems(
    paging: LazyPagingItems<T>,
    emptyMessage: String? = null,
) {
    when {
        paging.loadState.refresh is LoadState.Loading -> item(key = "paging_loading") { BrandLoading() }
        paging.loadState.refresh is LoadState.Error -> item(key = "paging_error") {
            BrandCard {
                BrandPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_retry),
                    onClick = { paging.retry() },
                )
            }
        }
        paging.itemCount == 0 && emptyMessage != null -> item(key = "paging_empty") {
            BrandEmptyState(message = emptyMessage)
        }
    }
    if (paging.loadState.append is LoadState.Loading) item(key = "paging_append") { BrandLoading() }
}

fun <T : Any> LazyGridScope.pagingStatusItems(
    paging: LazyPagingItems<T>,
    emptyMessage: String? = null,
) {
    when {
        paging.loadState.refresh is LoadState.Loading -> gridItems(count = 1, key = { "paging_loading" }) { BrandLoading() }
        paging.loadState.refresh is LoadState.Error -> gridItems(count = 1, key = { "paging_error" }) {
            BrandCard {
                BrandPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.action_retry),
                    onClick = { paging.retry() },
                )
            }
        }
        paging.itemCount == 0 && emptyMessage != null -> gridItems(count = 1, key = { "paging_empty" }) {
            BrandEmptyState(message = emptyMessage)
        }
    }
    if (paging.loadState.append is LoadState.Loading) gridItems(count = 1, key = { "paging_append" }) { BrandLoading() }
}
