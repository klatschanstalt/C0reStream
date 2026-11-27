package pw.c0re.stream.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import pw.c0re.stream.data.M3UParser.RawChannel
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.db.ProtectedUrl
import pw.c0re.stream.data.model.EpgEntry
import java.util.Locale

/** Configurable guard against a broken/truncated refresh deleting most of a playlist. */
data class ShrinkGuard(
    val enabled: Boolean = true,
    val minKeepRatio: Float = 0.5f,
    val minChannels: Int = 4,
)

/** Full accounting for one store operation. */
data class ImportResult(
    val playlistId: Long,
    val stats: ImportStats,
    /** Stable keys in provider order. */
    val orderedKeys: List<String>,
    /** Row IDs in provider order; an ID is resolved after insertion for new rows. */
    val orderedChannelIds: List<Long>,
    val preservedIds: Int,
    val shrinkGuard: ShrinkGuard,
)

/** Failure carrying the reason codes so the UI can show why an import was refused. */
class ImportGuardException(message: String, val stats: ImportStats) : IllegalStateException(message)

/** Pure, DB-free import plan: deduplicated keys in provider order plus explicit counters. */
data class ImportPlan(
    val ordered: List<Pair<String, RawChannel>>,
    val merged: Int,
    val stats: ImportStats,
    val collisions: Int,
)

class PlaylistRepository(private val db: AppDatabase) {

    suspend fun fetchAndStore(
        displayName: String,
        url: String,
        epgUrl: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        existingId: Long? = null,
    ): Long = fetchAndStoreWithStats(
        displayName, url, epgUrl, userAgent, useProxy, proxyHost, proxyPort, existingId,
    ).playlistId

    suspend fun fetchAndStoreWithStats(
        displayName: String,
        url: String,
        epgUrl: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        existingId: Long? = null,
        guard: ShrinkGuard = ShrinkGuard(),
    ): ImportResult = withContext(Dispatchers.IO) {
        val client = NetworkPolicy.client(userAgent, useProxy, proxyHost, proxyPort)
        val bytes = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(url)).build())
            .execute().use { response -> response.readBytes() }
        val parsed = M3UParser.parseWithStats(bytes)
        val plan = planImport(parsed.channels, parsed.stats)
        if (plan.ordered.isEmpty()) {
            throw ImportGuardException("No channels found", plan.stats)
        }
        storePlan(
            displayName, url, epgUrl, userAgent, useProxy, proxyHost, proxyPort,
            plan, existingId, guard,
        )
    }

    suspend fun refresh(playlist: PlaylistEntity): Result<Long> {
        val result = refreshWithStats(playlist)
        return result.map { it.playlistId }
    }

    suspend fun refreshWithStats(playlist: PlaylistEntity): Result<ImportResult> {
        val url = playlist.url ?: return Result.failure(IllegalArgumentException("Local playlist"))
        return if (url.startsWith("xtream://")) {
            runCatching {
                val credentials = requireNotNull(XtreamApi.parseSyntheticUrl(url)) { "Invalid Xtream credentials" }
                val client = NetworkPolicy.client(
                    playlist.userAgent, playlist.useProxy, playlist.proxyHost, playlist.proxyPort,
                )
                val report = XtreamApi(client).fetchLiveChannelsWithReport(credentials)
                val plan = planImport(report.channels, report.stats)
                if (plan.ordered.isEmpty()) {
                    throw ImportGuardException("No channels found", plan.stats)
                }
                storePlan(
                    playlist.name, url, playlist.epgUrl, playlist.userAgent, playlist.useProxy,
                    playlist.proxyHost, playlist.proxyPort, plan, playlist.id, ShrinkGuard(),
                )
            }.onFailure { if (it is CancellationException) throw it }
        } else {
            runCatching {
                fetchAndStoreWithStats(
                    playlist.name, url, playlist.epgUrl, playlist.userAgent, playlist.useProxy,
                    playlist.proxyHost, playlist.proxyPort, playlist.id,
                )
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    /** Legacy entry point; use [storeChannelsWithStats] to receive [ImportStats]. */
    suspend fun storeChannels(
        name: String,
        url: String?,
        epg: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        raw: List<RawChannel>,
        existingId: Long? = null,
    ): Long = storeChannelsWithStats(
        name, url, epg, userAgent, useProxy, proxyHost, proxyPort, raw, existingId,
    ).playlistId

    suspend fun storeChannelsWithStats(
        name: String,
        url: String?,
        epg: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        raw: List<RawChannel>,
        existingId: Long? = null,
        sourceStats: ImportStats? = null,
        guard: ShrinkGuard = ShrinkGuard(),
    ): ImportResult {
        val plan = planImport(raw, sourceStats)
        check(plan.ordered.isNotEmpty()) { "No channels found" }
        return storePlan(
            name, url, epg, userAgent, useProxy, proxyHost, proxyPort, plan, existingId, guard,
        )
    }

    private suspend fun storePlan(
        name: String,
        url: String?,
        epg: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        plan: ImportPlan,
        existingId: Long?,
        guard: ShrinkGuard,
    ): ImportResult = db.withTransaction {
        val playlistId = if (existingId != null) {
            val existing = requireNotNull(db.playlistDao().byId(existingId)) { "Playlist missing" }
            db.playlistDao().upsert(
                existing.copy(
                    name = name,
                    protectedUrl = url?.let { ProtectedUrl(it) },
                    protectedEpgUrl = epg?.let { ProtectedUrl(it) },
                    userAgent = NetworkPolicy.userAgent(userAgent),
                    useProxy = useProxy,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                ),
            )
            existingId
        } else {
            db.playlistDao().upsert(
                PlaylistEntity(
                    name = name,
                    url = url,
                    epgUrl = epg,
                    userAgent = NetworkPolicy.userAgent(userAgent),
                    useProxy = useProxy,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                ),
            )
        }

        // snapshot() is ordered by row id (insertion order), never by name.
        val old = db.channelDao().snapshot(playlistId)
        if (existingId != null && shouldBlockShrink(old.size, plan.ordered.size, guard)) {
            val guardStats = plan.stats.copy(
                accepted = old.size,
                rejected = plan.stats.rejected + 1,
                rejections = plan.stats.rejections + ImportRejection(
                    ImportRejectionCode.SHRINK_GUARD.code,
                    0,
                    "old=${old.size}, new=${plan.ordered.size}, minRatio=${guard.minKeepRatio}",
                ),
            )
            throw ImportGuardException("Playlist shrank unexpectedly; keeping previous channels", guardStats)
        }

        val usedIds = HashSet<Long>()
        val oldByKey = old.filter { it.stableKey.isNotBlank() }.groupBy { it.stableKey }
        val oldByTvg = old.filter { !it.tvgId.isNullOrBlank() }
            .groupBy { it.tvgId!!.trim().lowercase(Locale.ROOT) }
        val oldByUrl = old.groupBy { ChannelKey.normalizeUrl(it.url) ?: it.url }
        val assignments = arrayOfNulls<ChannelEntity>(plan.ordered.size)

        // Strict matches first so a variant does not steal another variant's row ID.
        for ((index, entry) in plan.ordered.withIndex()) {
            val (key, channel) = entry
            val normalized = ChannelKey.normalizeUrl(channel.url) ?: channel.url
            val tvg = channel.tvgId?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
            val legacy = ChannelKey.legacyKey(channel.url, channel.tvgId)
            val variantNow = ChannelKey.variantKey(channel.url)
            val strict = sequenceOf(
                oldByKey[key].orEmpty(),
                oldByKey[legacy].orEmpty(),
                tvg?.let { oldByTvg[it] }.orEmpty()
                    .filter { (ChannelKey.normalizeUrl(it.url) ?: it.url) == normalized },
                tvg?.let { oldByTvg[it] }.orEmpty()
                    .filter { ChannelKey.variantKey(it.url) == variantNow },
                tvg?.let { oldByTvg[it] }.orEmpty().filter {
                    it.name.equals(channel.name, ignoreCase = true) && it.group.equals(channel.group, ignoreCase = true)
                },
                oldByUrl[normalized].orEmpty().filter {
                    ChannelKey.stableKey(it.url, it.tvgId, name = it.name, group = it.group) ==
                        ChannelKey.stableKey(channel.url, channel.tvgId, name = channel.name, group = channel.group)
                },
            )
            assignments[index] = strict.firstNotNullOfOrNull { candidates -> takeUnused(candidates, usedIds) }
        }
        // Loose matches only for entries that still have no row; this is what keeps favourites and
        // history when a key changes because of an identity/variant fix or a URL token refresh.
        for ((index, entry) in plan.ordered.withIndex()) {
            if (assignments[index] != null) continue
            val (_, channel) = entry
            val tvg = channel.tvgId?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
            val normalized = ChannelKey.normalizeUrl(channel.url) ?: channel.url
            val loose = sequenceOf(
                tvg?.let { oldByTvg[it] }.orEmpty(),
                oldByUrl[normalized].orEmpty(),
            )
            assignments[index] = loose.firstNotNullOfOrNull { candidates -> takeUnused(candidates, usedIds) }
        }

        val channels = plan.ordered.mapIndexed { index, entry ->
            val (key, channel) = entry
            ChannelEntity(
                id = assignments[index]?.id ?: 0L,
                playlistId = playlistId,
                name = channel.name,
                logoUrl = channel.logo,
                group = channel.group,
                url = channel.url,
                tvgId = channel.tvgId,
                catchupSource = channel.catchupSource,
                stableKey = key,
                orderIndex = index,
                tvgName = channel.tvgName,
                channelNumber = channel.channelNumber,
                quality = channel.quality,
            )
        }
        val retainedIds = channels.mapNotNull { it.id.takeIf { id -> id > 0 } }.toSet()
        val removedIds = old.filter { it.id !in retainedIds }.map { it.id }
        if (removedIds.isNotEmpty()) {
            db.channelDao().deleteByIds(removedIds)
            // Reminders are time-bound and tied to the removed channel; favourites/history of
            // retained IDs are untouched and orphan cleanup cannot touch them.
            db.reminderDao().deleteForChannels(removedIds)
        }
        // Insertion is in provider order: new rows get monotonically increasing generated IDs.
        db.channelDao().insertAll(channels)
        db.favoriteDao().removeOrphans()
        db.historyDao().removeOrphans()
        db.playlistDao().updateMeta(playlistId, channels.size, System.currentTimeMillis())

        val byKey = db.channelDao().snapshot(playlistId).associateBy { it.stableKey }
        val stats = plan.stats.copy(
            accepted = channels.size,
            merged = plan.merged,
            collisions = plan.collisions,
            preservedIds = retainedIds.size,
        )
        ImportResult(
            playlistId = playlistId,
            stats = stats,
            orderedKeys = plan.ordered.map { it.first },
            orderedChannelIds = plan.ordered.map { byKey[it.first]?.id ?: 0L },
            preservedIds = retainedIds.size,
            shrinkGuard = guard,
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = db.withTransaction {
        db.favoriteDao().removeForPlaylist(playlistId)
        db.historyDao().removeForPlaylist(playlistId)
        db.progressDao().deleteForPlaylist(playlistId)
        db.watchlistDao().deleteForPlaylist(playlistId)
        db.reminderDao().deleteForPlaylist(playlistId)
        db.epgDao().deleteForPlaylist(playlistId)
        db.channelDao().deleteForPlaylist(playlistId)
        db.playlistDao().delete(playlistId)
    }

    suspend fun loadEpg(playlistId: Long): Map<String, List<EpgEntry>> = withContext(Dispatchers.IO) {
        val playlist = db.playlistDao().byId(playlistId) ?: return@withContext emptyMap()
        val epgUrl = playlist.epgUrl ?: return@withContext emptyMap()
        try {
            val client = NetworkPolicy.client(
                playlist.userAgent, playlist.useProxy, playlist.proxyHost, playlist.proxyPort,
            )
            val text = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(epgUrl)).build())
                .execute()
                .use { response -> response.readText(48L * 1024 * 1024) }
            EpgParser.parse(text)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("EPG", error.message ?: "?")
            emptyMap()
        }
    }

    suspend fun setRefreshPolicy(playlistId: Long, enabled: Boolean, hours: Int) =
        db.playlistDao().setRefreshPolicy(playlistId, enabled, hours.coerceIn(1, 168))

    companion object {
        const val DEFAULT_MIN_KEEP_RATIO = 0.5f

        /** True when a refresh would drop more than the configured share of existing channels. */
        fun shouldBlockShrink(oldSize: Int, newSize: Int, guard: ShrinkGuard = ShrinkGuard()): Boolean =
            guard.enabled && oldSize > guard.minChannels && newSize < oldSize * guard.minKeepRatio

        /**
         * Reorders a DB snapshot into provider order using the keys reported by [ImportResult].
         * Needed until an `orderIndex` column exists; DAO reads still sort by name/ID.
         */
        fun orderSnapshot(snapshot: List<ChannelEntity>, orderedKeys: List<String>): List<ChannelEntity> {
            val byKey = snapshot.associateBy { it.stableKey }
            return orderedKeys.mapNotNull { byKey[it] }
        }

        /**
         * Deduplicates on the new identity key while keeping provider order and distinct
         * variants. Only entries that are identical in identity and stream are merged.
         */
        fun planImport(raw: List<RawChannel>, sourceStats: ImportStats? = null): ImportPlan {
            val ordered = ArrayList<Pair<String, RawChannel>>(raw.size)
            val seen = LinkedHashMap<String, RawChannel>()
            var merged = 0
            var collisions = 0
            for (channel in raw) {
                val key = ChannelKey.stableKey(
                    url = channel.url,
                    tvgId = channel.tvgId,
                    tvgName = channel.tvgName,
                    name = channel.name,
                    group = channel.group,
                    channelNumber = channel.channelNumber,
                    variantKey = ChannelKey.variantKey(channel.url, channel.channelNumber, channel.quality),
                )
                var finalKey = key
                var attempt = 0
                while (true) {
                    val existing = seen[finalKey] ?: break
                    if (sameChannel(existing, channel)) {
                        merged++
                        break
                    }
                    // Same identity key but a genuinely different variant: never drop it.
                    collisions++
                    attempt++
                    finalKey = ChannelKey.disambiguate(key, discriminator(channel) + "#" + attempt)
                }
                if (!seen.containsKey(finalKey)) {
                    seen[finalKey] = channel
                    ordered += finalKey to channel
                }
            }
            var stats = (sourceStats ?: ImportStats(received = raw.size, accepted = raw.size)).copy(
                received = maxOf(sourceStats?.received ?: raw.size, raw.size),
                accepted = ordered.size,
                merged = merged,
                collisions = collisions,
            )
            if (collisions > 0) {
                stats = stats.withRejection(
                    ImportRejection(
                        ImportRejectionCode.DUPLICATE_KEY_COLLISION.code,
                        0,
                        "collisions=$collisions",
                    ),
                    incrementRejected = false,
                )
            }
            return ImportPlan(ordered, merged, stats, collisions)
        }

        private fun sameChannel(a: RawChannel, b: RawChannel): Boolean =
            a.tvgId.equals(b.tvgId, ignoreCase = true) &&
                a.name.equals(b.name, ignoreCase = true) &&
                a.group.equals(b.group, ignoreCase = true) &&
                a.channelNumber.equals(b.channelNumber, ignoreCase = true) &&
                a.quality.equals(b.quality, ignoreCase = true) &&
                ChannelKey.normalizeUrl(a.url) == ChannelKey.normalizeUrl(b.url)

        private fun discriminator(channel: RawChannel): String = listOfNotNull(
            channel.name, channel.group, channel.channelNumber, channel.quality,
            ChannelKey.normalizeUrl(channel.url),
        ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

        private fun takeUnused(
            candidates: List<ChannelEntity>,
            usedIds: MutableSet<Long>,
        ): ChannelEntity? = candidates.firstOrNull { entity -> entity.id > 0 && usedIds.add(entity.id) }
    }
}
