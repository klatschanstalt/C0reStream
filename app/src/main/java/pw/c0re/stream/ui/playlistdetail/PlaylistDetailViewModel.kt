package pw.c0re.stream.ui.playlistdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.EpgStore
import pw.c0re.stream.data.PlaylistRepository
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.FavoriteEntity
import pw.c0re.stream.data.db.ReminderEntity
import pw.c0re.stream.data.model.EpgEntry
import pw.c0re.stream.work.ReminderScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    private val epgStore = EpgStore(db, PlaylistRepository(db))
    private val playlistIdFlow = MutableStateFlow(0L)
    private val queryFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow<String?>(null)

    val query: StateFlow<String> = queryFlow.asStateFlow()
    val selectedGroup: StateFlow<String?> = groupFlow.asStateFlow()

    val groups = playlistIdFlow.flatMapLatest { id -> db.channelDao().groups(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels = combine(playlistIdFlow, queryFlow, groupFlow) { id, q, g -> Triple(id, q, g) }
        .flatMapLatest { (id, q, g) ->
            if (g == null) {
                if (q.isBlank()) db.channelDao().byPlaylist(id) else db.channelDao().search(id, q)
            } else {
                db.channelDao().byGroup(id, g).map { list ->
                    if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun init(id: Long) {
        playlistIdFlow.value = id
    }

    fun setQuery(q: String) {
        queryFlow.value = q.take(128)
    }

    fun selectGroup(g: String?) {
        groupFlow.value = g
    }

    fun isFavorite(channelId: Long): Flow<Boolean> =
        db.favoriteDao().countFor(channelId).map { it > 0 }

    fun nowFor(playlistId: Long, channel: ChannelEntity): Flow<EpgEntry?> = flow {
        emit(epgStore.nowFor(playlistId, channel.tvgId))
    }

    fun guideFor(playlistId: Long, channel: ChannelEntity): Flow<List<EpgEntry>> = flow {
        emit(epgStore.guideFor(playlistId, channel.tvgId))
    }

    fun toggleFavorite(channel: ChannelEntity) = viewModelScope.launch {
        val count = db.favoriteDao().countForOnce(channel.id)
        if (count > 0) {
            db.favoriteDao().remove(channel.id)
        } else {
            db.favoriteDao().add(
                FavoriteEntity(
                    channelId = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    url = channel.url,
                ),
            )
        }
    }

    fun hasReminder(channelId: Long, start: Long): Flow<Boolean> = flow {
        emit(db.reminderDao().byChannelStart(channelId, start) != null)
    }

    fun toggleReminder(channel: ChannelEntity, entry: EpgEntry) = viewModelScope.launch {
        val existing = db.reminderDao().byChannelStart(channel.id, entry.start)
        if (existing != null) {
            db.reminderDao().delete(existing.id)
            ReminderScheduler.cancel(getApplication(), existing.id)
            return@launch
        }
        if (entry.start <= System.currentTimeMillis()) return@launch
        val reminder = ReminderEntity(
            channelId = channel.id,
            channelName = channel.name.take(512),
            tvgId = channel.tvgId,
            title = entry.title.take(512),
            start = entry.start,
            stop = entry.stop,
            createdAt = System.currentTimeMillis(),
        )
        val id = db.reminderDao().upsert(reminder)
        ReminderScheduler.schedule(getApplication(), reminder.copy(id = id))
    }

    fun refreshGuide(playlistId: Long) = viewModelScope.launch {
        epgStore.refresh(playlistId)
    }

    suspend fun playlistName(playlistId: Long): String? = db.playlistDao().byId(playlistId)?.name
}
