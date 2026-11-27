package pw.c0re.stream.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
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
    @ColumnInfo(defaultValue = "1") val autoRefresh: Boolean = true,
    @ColumnInfo(defaultValue = "12") val refreshHours: Int = 12,
    @ColumnInfo(defaultValue = "0") val epgUpdatedAt: Long = 0,
) {
    @Ignore
    constructor(
        id: Long = 0,
        name: String,
        url: String?,
        epgUrl: String? = null,
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
        channelCount: Int = 0,
        lastUpdated: Long = 0,
        autoRefresh: Boolean = true,
        refreshHours: Int = 12,
        epgUpdatedAt: Long = 0,
    ) : this(
        id, name, url?.let(::ProtectedUrl), epgUrl?.let(::ProtectedUrl), userAgent, useProxy,
        proxyHost, proxyPort, channelCount, lastUpdated, autoRefresh, refreshHours, epgUpdatedAt,
    )

    @get:Ignore
    val url: String? get() = protectedUrl?.value

    @get:Ignore
    val epgUrl: String? get() = protectedEpgUrl?.value
}

@Entity(
    tableName = "channels",
    indices = [
        Index("playlistId"),
        Index("name"),
        Index(value = ["playlistId", "stableKey"]),
    ],
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
    @ColumnInfo(defaultValue = "''") val stableKey: String = "",
) {
    @Ignore
    constructor(
        id: Long = 0,
        playlistId: Long,
        name: String,
        logoUrl: String?,
        group: String?,
        url: String,
        tvgId: String?,
        catchupSource: String? = null,
        stableKey: String = "",
    ) : this(
        id, playlistId, name, logoUrl?.let(::ProtectedUrl), group, ProtectedUrl(url), tvgId,
        catchupSource?.let(::ProtectedUrl), stableKey,
    )

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
    constructor(id: Long = 0, channelId: Long, name: String, logoUrl: String?, url: String) :
        this(id, channelId, name, logoUrl?.let(::ProtectedUrl), ProtectedUrl(url))

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
    constructor(id: Long = 0, channelId: Long, name: String, logoUrl: String?, url: String, watchedAt: Long) :
        this(id, channelId, name, logoUrl?.let(::ProtectedUrl), ProtectedUrl(url), watchedAt)

    @get:Ignore
    val logoUrl: String? get() = protectedLogoUrl?.value

    @get:Ignore
    val url: String get() = protectedUrl.value
}

@Entity(
    tableName = "progress",
    indices = [Index(value = ["mediaKey"], unique = true), Index("updatedAt"), Index("playlistId")],
)
data class ProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaKey: String,
    val title: String,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl,
    val positionMs: Long,
    val durationMs: Long,
    val kind: String,
    val updatedAt: Long,
) {
    @Ignore
    constructor(
        id: Long = 0,
        playlistId: Long,
        mediaKey: String,
        title: String,
        url: String,
        positionMs: Long,
        durationMs: Long,
        kind: String,
        updatedAt: Long,
    ) : this(id, playlistId, mediaKey, title, ProtectedUrl(url), positionMs, durationMs, kind, updatedAt)

    @get:Ignore
    val url: String get() = protectedUrl.value
}

@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["mediaKey"], unique = true), Index("addedAt"), Index("playlistId")],
)
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaKey: String,
    val title: String,
    @ColumnInfo(name = "posterUrl") val protectedPosterUrl: ProtectedUrl?,
    @ColumnInfo(name = "url") val protectedUrl: ProtectedUrl?,
    val kind: String,
    val addedAt: Long,
) {
    @Ignore
    constructor(
        id: Long = 0,
        playlistId: Long,
        mediaKey: String,
        title: String,
        posterUrl: String?,
        url: String?,
        kind: String,
        addedAt: Long,
    ) : this(id, playlistId, mediaKey, title, posterUrl?.let(::ProtectedUrl), url?.let(::ProtectedUrl), kind, addedAt)

    @get:Ignore
    val posterUrl: String? get() = protectedPosterUrl?.value

    @get:Ignore
    val url: String? get() = protectedUrl?.value
}

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["playlistId", "tvgId", "start"], unique = true),
        Index("playlistId"),
        Index("stop"),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val tvgId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["channelId", "start"], unique = true), Index("start")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val channelName: String,
    val tvgId: String?,
    val title: String,
    val start: Long,
    val stop: Long,
    val createdAt: Long,
)
