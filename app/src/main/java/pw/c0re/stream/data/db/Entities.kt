package pw.c0re.stream.data.db

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl?,
    @ColumnInfo(name = "epgUrl") val protectedEpgUrl: ProtectedUrl? = null,
    val userAgent: String? = null,
    val useProxy: Boolean = false,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val channelCount: Int = 0,
    val lastUpdated: Long = 0,
) {
    @Ignore
    constructor(id: Long = 0, name: String, url: String?, epgUrl: String? = null, userAgent: String? = null, useProxy: Boolean = false, proxyHost: String? = null, proxyPort: Int? = null, channelCount: Int = 0, lastUpdated: Long = 0) : this(id, name, url?.let(::ProtectedUrl), epgUrl?.let(::ProtectedUrl), userAgent, useProxy, proxyHost, proxyPort, channelCount, lastUpdated)
    @get:Ignore
    val url: String? get() = protectedUrl?.value
    @get:Ignore
    val epgUrl: String? get() = protectedEpgUrl?.value
}

@Entity(
    tableName = "channels",
    indices = [Index("playlistId"), Index("name")],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    @ColumnInfo(name = "logoUrl") val protectedLogoUrl: ProtectedUrl?,
    val group: String?,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl,
    val tvgId: String?,
    @ColumnInfo(name = "catchupSource") val protectedCatchupSource: ProtectedUrl? = null,
) {
    @Ignore
    constructor(id: Long = 0, playlistId: Long, name: String, logoUrl: String?, group: String?, url: String, tvgId: String?, catchupSource: String? = null) : this(id, playlistId, name, logoUrl?.let(::ProtectedUrl), group, ProtectedUrl(url), tvgId, catchupSource?.let(::ProtectedUrl))
    @get:Ignore
    val logoUrl: String? get() = protectedLogoUrl?.value
    @get:Ignore
    val url: String get() = protectedUrl.value
    @get:Ignore
    val catchupSource: String? get() = protectedCatchupSource?.value
}

@Entity(tableName = "favorites", indices = [Index("channelId", unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val name: String,
    @ColumnInfo(name = "logoUrl") val protectedLogoUrl: ProtectedUrl?,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl,
) {
    @Ignore
    constructor(id: Long = 0, channelId: Long, name: String, logoUrl: String?, url: String) : this(id, channelId, name, logoUrl?.let(::ProtectedUrl), ProtectedUrl(url))
    @get:Ignore
    val logoUrl: String? get() = protectedLogoUrl?.value
    @get:Ignore
    val url: String get() = protectedUrl.value
}

@Entity(tableName = "history", indices = [Index("watchedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val name: String,
    @ColumnInfo(name = "logoUrl") val protectedLogoUrl: ProtectedUrl?,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl,
    val watchedAt: Long,
) {
    @Ignore
    constructor(id: Long = 0, channelId: Long, name: String, logoUrl: String?, url: String, watchedAt: Long) : this(id, channelId, name, logoUrl?.let(::ProtectedUrl), ProtectedUrl(url), watchedAt)
    @get:Ignore
    val logoUrl: String? get() = protectedLogoUrl?.value
    @get:Ignore
    val url: String get() = protectedUrl.value
}
