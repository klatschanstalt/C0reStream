package pw.c0re.stream.ui.globalsearch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.paging.ChannelPagingConfig

/**
 * Globale Suche ueber alle Playlists hinweg.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val channelDao = db.channelDao()
    private val queryFlow = MutableStateFlow("")

    val query: StateFlow<String> = queryFlow.asStateFlow()

    /** Playlist names for decorating paged results without loading channels into memory. */
    val playlistNames: StateFlow<Map<Long, String>> = db.playlistDao().all()
        .map { playlists -> playlists.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Short queries produce no results; longer queries get a fresh PagingSource. There is no
     * LIMIT in the SQL, so all matches are reachable by scrolling.
     */
    val results: Flow<PagingData<ChannelEntity>> = queryFlow
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(PagingData.empty<ChannelEntity>())
            } else {
                Pager(config = ChannelPagingConfig.config) {
                    channelDao.searchAllPaged(q)
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    /** True total independently of the bounded paging window. */
    val totalCount: StateFlow<Int> = queryFlow
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow { emit(if (q.length < 2) 0 else channelDao.countSearchAll(q)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(q: String) { queryFlow.value = q }
}
