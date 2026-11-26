package pw.c0re.stream.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String?,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val useProxy: Boolean = false,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val channelCount: Int = 0,
    val lastUpdated: Long = 0,
)

@Entity(
    tableName = "channels",
    indices = [Index("playlistId"), Index("name")],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val url: String,
    val tvgId: String?,
    val catchupSource: String? = null,
)

@Entity(tableName = "favorites", indices = [Index("channelId", unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val name: String,
    val logoUrl: String?,
    val url: String,
)

@Entity(tableName = "history", indices = [Index("watchedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val name: String,
    val logoUrl: String?,
    val url: String,
    val watchedAt: Long,
)
