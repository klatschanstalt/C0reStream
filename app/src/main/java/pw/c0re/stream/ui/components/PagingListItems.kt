package pw.c0re.stream.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable

/** Small adapters that keep paged screens concise and avoid hidden silent limits. */
fun LazyListScope.listItems(
    count: Int,
    key: ((index: Int) -> Any)? = null,
    content: @Composable LazyItemScope.(index: Int) -> Unit,
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

fun LazyGridScope.gridItems(
    count: Int,
    key: ((index: Int) -> Any)? = null,
    content: @Composable LazyGridItemScope.(index: Int) -> Unit,
) {
    items(count = count, key = key, itemContent = content)
}

fun <T> LazyGridScope.gridItems(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    content: @Composable LazyGridItemScope.(item: T) -> Unit,
) {
    items(items = items, key = key, itemContent = content)
}
