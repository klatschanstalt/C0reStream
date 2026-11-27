package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for RTSP/UDP playlist sources and their DB-free import planning. */
class RtspPlaylistTest {

    @Test
    fun `direct stream detection keeps http playlist urls on the http path`() {
        assertTrue(PlaylistRepository.isDirectStreamUrl("rtsp://host/live"))
        assertTrue(PlaylistRepository.isDirectStreamUrl("rtspu://host:554/live"))
        assertTrue(PlaylistRepository.isDirectStreamUrl("udp://@239.255.1.1:5000"))
        assertFalse(PlaylistRepository.isDirectStreamUrl("https://example.com/list.m3u8"))
        assertFalse(PlaylistRepository.isDirectStreamUrl("http://example.com/list.m3u"))
        assertFalse(PlaylistRepository.isDirectStreamUrl("file:///tmp/list.m3u"))
        assertFalse(PlaylistRepository.isDirectStreamUrl("rtsp://user:pass@host/live"))
    }

    @Test
    fun `planImport keeps provider order and unique stable keys for rtsp and udp channels`() {
        val raw = listOf(
            M3UParser.RawChannel("Cam 1", null, "Cams", "cam1", "rtsp://Host:554/live/1"),
            M3UParser.RawChannel("Cam 2", null, "Cams", "cam2", "rtspu://host/live/2"),
            M3UParser.RawChannel("Cam 3", null, "Cams", "cam3", "udp://239.0.0.1:5000"),
        )
        val plan = PlaylistRepository.planImport(raw)
        assertEquals(3, plan.ordered.size)
        assertEquals(listOf("Cam 1", "Cam 2", "Cam 3"), plan.ordered.map { it.second.name })
        assertEquals(3, plan.ordered.map { it.first }.toSet().size)
        assertEquals(0, plan.merged)
        assertEquals(3, plan.stats.accepted)
        assertEquals(3, plan.stats.received)
        assertTrue(plan.ordered.all { it.first.length == ChannelKey.KEY_LENGTH })
    }

    @Test
    fun `equivalent rtsp spellings produce the same key without a tvg id`() {
        val original = PlaylistRepository.planImport(
            listOf(M3UParser.RawChannel("Cam", null, "Cams", null, "rtsp://Host:554/live/")),
        ).ordered.single().first
        val rewritten = PlaylistRepository.planImport(
            listOf(M3UParser.RawChannel("Cam", null, "Cams", null, "rtsp://host/live")),
        ).ordered.single().first
        assertEquals(original, rewritten)
    }

    @Test
    fun `exact rtsp duplicates are merged while distinct variants stay separate`() {
        val entry = M3UParser.RawChannel("Cam", null, "Cams", null, "rtsp://host/live")
        val merged = PlaylistRepository.planImport(listOf(entry, entry.copy(), entry.copy()))
        assertEquals(1, merged.ordered.size)
        assertEquals(2, merged.merged)

        val distinct = PlaylistRepository.planImport(
            listOf(entry, entry.copy(name = "Cam HD", quality = "HD")),
        )
        assertEquals(2, distinct.ordered.size)
        assertEquals(2, distinct.ordered.map { it.first }.toSet().size)
    }

    @Test
    fun `rejection counters survive planning of a mixed rtsp playlist`() {
        val parsed = M3UParser.parseWithStats(
            """
            #EXTM3U
            rtsp://user:pass@example.com/live
            rtsp://example.com/live
            udp://@239.255.1.1:5000
            file:///tmp/local.ts
            """.trimIndent(),
        )
        val plan = PlaylistRepository.planImport(parsed.channels, parsed.stats)
        assertEquals(2, plan.ordered.size)
        assertEquals(2, plan.stats.rejectedCount(ImportRejectionCode.INVALID_STREAM_URL))
        assertEquals(listOf("rtsp://example.com/live", "udp://@239.255.1.1:5000"),
            plan.ordered.map { it.second.url })
    }

    @Test
    fun `shrink guard is unchanged for rtsp playlists`() {
        assertTrue(PlaylistRepository.shouldBlockShrink(10, 2))
        assertFalse(PlaylistRepository.shouldBlockShrink(10, 5))
        assertFalse(PlaylistRepository.shouldBlockShrink(10, 2, ShrinkGuard(enabled = false)))
    }
}
