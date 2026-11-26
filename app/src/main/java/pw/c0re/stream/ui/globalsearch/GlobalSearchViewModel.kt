package pw.c0re.stream.ui.globalsearch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.db.ChannelEntity

/**
 * Globale Suche ueber alle Playlists hinweg.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val queryFlow = MutableStateFlow("")

    val query: StateFlow<String> = queryFlow.asStateFlow()

    private val matched = queryFlow.flatMapLatest { q ->
        if (q.length < 2) flowOf(emptyList<ChannelEntity>())
        else db.channelDao().searchAll(q)
    }

    val results = combine(db.playlistDao().all(), matched) { playlists, channels ->
        val names = playlists.associate { it.id to it.name }
        channels.map { it to (names[it.playlistId] ?: "?") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { queryFlow.value = q }
}
