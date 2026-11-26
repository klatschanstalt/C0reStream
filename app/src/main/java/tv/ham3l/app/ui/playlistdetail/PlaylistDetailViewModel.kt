package tv.ham3l.app.ui.playlistdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp
import tv.ham3l.app.data.db.ChannelEntity
import tv.ham3l.app.data.db.FavoriteEntity

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database
    private val epgStore = tv.ham3l.app.data.EpgStore(db, tv.ham3l.app.data.PlaylistRepository(db))
    private val playlistIdFlow = MutableStateFlow(0L)
    private val queryFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow<String?>(null)

    val query: StateFlow<String> = queryFlow.asStateFlow()
    val selectedGroup: StateFlow<String?> = groupFlow.asStateFlow()

    val groups = playlistIdFlow.flatMapLatest { id -> db.channelDao().groups(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels = combine(playlistIdFlow, queryFlow, groupFlow) { id, q, g -> Triple(id, q, g) }
        .flatMapLatest { (id, q, g) ->
            if (g == null) db.channelDao().search(id, q)
            else db.channelDao().byGroup(id, g).map { list -> if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(id: Long) { playlistIdFlow.value = id }
    fun setQuery(q: String) { queryFlow.value = q }
    fun selectGroup(g: String?) { groupFlow.value = g }

    fun isFavorite(channelId: Long): Flow<Boolean> =
        db.favoriteDao().countFor(channelId).map { it > 0 }

    // Aktuell laufendes Programm fuer einen Kanal (EPG via tvg-id)
    fun nowFor(playlistId: Long, ch: ChannelEntity): Flow<tv.ham3l.app.data.model.EpgEntry?> =
        kotlinx.coroutines.flow.flow { emit(epgStore.nowFor(playlistId, ch.tvgId)) }

    // Tagesprogramm eines Kanals fuer den EPG-Guide
    fun guideFor(playlistId: Long, ch: ChannelEntity): Flow<List<tv.ham3l.app.data.model.EpgEntry>> =
        kotlinx.coroutines.flow.flow { emit(epgStore.dayFor(playlistId, ch.tvgId)) }

    fun toggleFavorite(ch: ChannelEntity) = viewModelScope.launch {
        val count = db.favoriteDao().countFor(ch.id).first()
        if (count > 0) db.favoriteDao().remove(ch.id)
        else db.favoriteDao().add(FavoriteEntity(channelId = ch.id, name = ch.name, logoUrl = ch.logoUrl, url = ch.url))
    }
}
