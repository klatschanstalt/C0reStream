package tv.ham3l.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp
import tv.ham3l.app.data.db.ChannelEntity
import tv.ham3l.app.data.db.HistoryEntity

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database

    fun channelFor(id: Long): Flow<ChannelEntity?> = flow { emit(if (id <= 0) null else db.channelDao().byId(id)) }

    // Alle Kanaele derselben Playlist, sortiert, fuer Sender-Zapping
    fun siblingsOf(ch: ChannelEntity?): Flow<List<ChannelEntity>> =
        flow { emit(if (ch == null) emptyList() else db.channelDao().byPlaylist(ch.playlistId).first()) }

    // Verlauf fuer einen Kanal loeschen (doppelte Eintraege vermeiden)
    fun logWatch(ch: ChannelEntity) = viewModelScope.launch {
        db.historyDao().add(HistoryEntity(channelId = ch.id, name = ch.name, logoUrl = ch.logoUrl, url = ch.url, watchedAt = System.currentTimeMillis()))
    }
}
