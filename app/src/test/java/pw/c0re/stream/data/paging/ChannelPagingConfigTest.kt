package pw.c0re.stream.data.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPagingConfigTest {

    @Test
    fun `paging config is bounded in memory`() {
        val config = ChannelPagingConfig.config
        assertTrue(config.pageSize <= ChannelPagingConfig.PAGE_SIZE)
        assertTrue(config.maxSize <= ChannelPagingConfig.MAX_SIZE)
        assertTrue(config.maxSize >= config.pageSize + 2 * config.prefetchDistance)
        assertTrue(config.initialLoadSize >= config.pageSize)
        assertTrue(config.initialLoadSize <= config.maxSize)
        assertFalse(config.enablePlaceholders)
    }

    @Test
    fun `pages are small and prefetch stays below half a page`() {
        assertEquals(60, ChannelPagingConfig.PAGE_SIZE)
        assertEquals(10, ChannelPagingConfig.PREFETCH_DISTANCE)
        assertTrue(ChannelPagingConfig.PREFETCH_DISTANCE < ChannelPagingConfig.PAGE_SIZE / 2)
        assertEquals(ChannelPagingConfig.PAGE_SIZE, ChannelPagingConfig.config.pageSize)
        assertEquals(ChannelPagingConfig.PREFETCH_DISTANCE, ChannelPagingConfig.config.prefetchDistance)
    }
}
