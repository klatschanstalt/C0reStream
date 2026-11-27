package pw.c0re.stream.data.paging

import androidx.paging.PagingConfig

/**
 * Bounded paging configuration shared by the playlist channel list and global search.
 *
 * Pages stay small so large playlists remain responsive, and [maxSize] stops Paging from
 * retaining an entire result set in memory. Placeholders are disabled: the UI only ever
 * materializes rows that have actually been loaded.
 */
object ChannelPagingConfig {
    const val PAGE_SIZE = 60
    const val PREFETCH_DISTANCE = 10
    const val INITIAL_LOAD_SIZE = 120
    const val MAX_SIZE = 600

    val config: PagingConfig = PagingConfig(
        pageSize = PAGE_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false,
        initialLoadSize = INITIAL_LOAD_SIZE,
        maxSize = MAX_SIZE,
    )
}
