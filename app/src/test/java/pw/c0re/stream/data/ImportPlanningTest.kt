package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pw.c0re.stream.data.db.ChannelEntity

/** JVM tests for the DB-free import planning/stats layer. */
class ImportPlanningTest {

    @Test
    fun `large playlist keeps provider order groups and numbers`() {
        val raw = (0 until 5_000).map { index ->
            M3UParser.RawChannel(
                name = "Channel $index",
                logo = null,
                group = "Group ${index % 5}",
                tvgId = "id-$index",
                url = "https://stream.example/$index.ts",
                channelNumber = "${index + 1}",
            )
        }
        val parsed = M3UParser.parseWithStats(
            (0 until 5_000).joinToString("\n") {
                "#EXTINF:${it + 1} tvg-id=\"id-$it\" group-title=\"Group ${it % 5}\",Channel $it\nhttps://stream.example/$it.ts"
            },
        )
        val plan = PlaylistRepository.planImport(parsed.channels, parsed.stats)
        assertEquals(5_000, plan.ordered.size)
        assertEquals(5_000, plan.stats.received)
        assertEquals(5_000, plan.stats.accepted)
        assertEquals(0, plan.merged)
        assertEquals(0, plan.collisions)
        assertFalse(plan.stats.truncated)
        plan.ordered.forEachIndexed { index, entry ->
            assertEquals("Channel $index", entry.second.name)
            assertEquals("Group ${index % 5}", entry.second.group)
            assertEquals("${index + 1}", entry.second.channelNumber)
        }
        assertEquals(raw.map { it.name }, plan.ordered.map { it.second.name })
        // No ordering by name: the provider sequence survives into the keys list.
        assertEquals(5_000, plan.ordered.map { it.first }.toSet().size)
    }

    @Test
    fun `duplicate url with different channels is not merged`() {
        val raw = listOf(
            M3UParser.RawChannel("News HD", null, "News", "news", "https://example.com/shared.ts"),
            M3UParser.RawChannel("Sport HD", null, "Sport", "sport", "https://example.com/shared.ts"),
            M3UParser.RawChannel("News SD", null, "News", null, "https://example.com/shared.ts"),
        )
        val plan = PlaylistRepository.planImport(raw)
        assertEquals(3, plan.ordered.size)
        assertEquals(0, plan.merged)
        assertEquals(3, plan.stats.accepted)
        assertEquals(3, plan.stats.received)
        assertEquals(3, plan.ordered.map { it.first }.toSet().size)
    }

    @Test
    fun `exact duplicates are merged and counted`() {
        val entry = M3UParser.RawChannel("News", null, "News", "news", "https://example.com/a.ts")
        val plan = PlaylistRepository.planImport(listOf(entry, entry.copy(), entry.copy()))
        assertEquals(1, plan.ordered.size)
        assertEquals(2, plan.merged)
        assertEquals(1, plan.stats.accepted)
        assertEquals(3, plan.stats.received)
    }

    @Test
    fun `same tvg id with different urls or qualities stays distinct`() {
        val raw = listOf(
            M3UParser.RawChannel("News HD", null, "News", "news", "https://example.com/hd.ts?token=1", quality = "HD"),
            M3UParser.RawChannel("News SD", null, "News", "news", "https://example.com/sd.ts?token=2", quality = "SD"),
            M3UParser.RawChannel("News 4K", null, "News", "news", "https://example.com/hd.ts?token=3", quality = "4K"),
        )
        val plan = PlaylistRepository.planImport(raw)
        assertEquals(3, plan.ordered.size)
        assertEquals(0, plan.merged)
        assertEquals(3, plan.ordered.map { it.first }.toSet().size)
        // Token-only changes on the same variant do not create a new channel.
        val refreshed = PlaylistRepository.planImport(
            listOf(raw[0].copy(url = "https://example.com/hd.ts?token=999")),
        )
        assertEquals(1, refreshed.ordered.size)
    }

    @Test
    fun `key collisions between distinct variants are resolved deterministically`() {
        val raw = listOf(
            M3UParser.RawChannel("News HD", null, "News", "news", "https://example.com/a.ts"),
            M3UParser.RawChannel("News International", null, "World", "news", "https://example.com/a.ts"),
        )
        val first = PlaylistRepository.planImport(raw)
        val second = PlaylistRepository.planImport(raw)
        assertEquals(2, first.ordered.size)
        assertEquals(0, first.merged)
        assertTrue(first.collisions >= 1)
        assertEquals(
            1,
            first.stats.rejectedCount(ImportRejectionCode.DUPLICATE_KEY_COLLISION),
        )
        assertEquals(first.ordered.map { it.first }, second.ordered.map { it.first })
        assertNotEquals(first.ordered[0].first, first.ordered[1].first)
    }

    @Test
    fun `parser rejection counters survive into the repository stats`() {
        val text = """
            #EXTM3U
            file:///bad.ts
            #EXTINF:-1,Valid
            https://example.com/valid.ts
        """.trimIndent()
        val parsed = M3UParser.parseWithStats(text)
        val plan = PlaylistRepository.planImport(parsed.channels, parsed.stats)
        assertEquals(2, plan.stats.received)
        assertEquals(1, plan.stats.accepted)
        assertEquals(1, plan.stats.rejected)
        assertEquals(1, plan.stats.rejectedCount(ImportRejectionCode.INVALID_STREAM_URL))
    }

    @Test
    fun `shrink guard is configurable and reported`() {
        assertTrue(PlaylistRepository.shouldBlockShrink(10, 2))
        assertFalse(PlaylistRepository.shouldBlockShrink(10, 5))
        assertFalse(PlaylistRepository.shouldBlockShrink(3, 1))
        assertFalse(PlaylistRepository.shouldBlockShrink(10, 2, ShrinkGuard(enabled = false)))
        assertFalse(PlaylistRepository.shouldBlockShrink(10, 2, ShrinkGuard(minKeepRatio = 0.1f)))
        assertTrue(PlaylistRepository.shouldBlockShrink(10, 2, ShrinkGuard(minKeepRatio = 0.3f)))
        assertEquals(0.5f, ShrinkGuard().minKeepRatio)
    }

    @Test
    fun `snapshot can be reordered into provider order without a schema change`() {
        val entities = listOf(
            ChannelEntity(id = 3, playlistId = 1, name = "Zulu", logoUrl = null, group = null,
                url = "https://example.com/z.ts", tvgId = "z", stableKey = "z"),
            ChannelEntity(id = 1, playlistId = 1, name = "Alpha", logoUrl = null, group = null,
                url = "https://example.com/a.ts", tvgId = "a", stableKey = "a"),
        )
        val ordered = PlaylistRepository.orderSnapshot(entities, listOf("a", "z"))
        assertEquals(listOf("Alpha", "Zulu"), ordered.map { it.name })
    }

    @Test
    fun `import stats aggregate rejection counts and merge counts`() {
        val stats = ImportStats(received = 3, accepted = 1)
            .withRejection(ImportRejection("INVALID_STREAM_URL", 2))
            .plus(ImportStats(received = 2, accepted = 2, merged = 1))
        assertEquals(5, stats.received)
        assertEquals(3, stats.accepted)
        assertEquals(1, stats.merged)
        assertEquals(1, stats.rejected)
        assertEquals(1, stats.rejectionCounts["INVALID_STREAM_URL"])
    }
}
