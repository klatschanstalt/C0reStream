package pw.c0re.stream.data.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPagingQueryTest {
    private val pagingSql = listOf(
        ChannelPagingQuery.FILTERED_SQL,
        ChannelPagingQuery.SEARCH_ALL_SQL,
        ChannelPagingQuery.SEARCH_SQL,
        ChannelPagingQuery.BY_GROUP_SQL,
        ChannelPagingQuery.BY_PLAYLIST_SQL,
    )

    @Test
    fun `unfiltered page is scoped to one playlist only`() {
        val spec = ChannelPagingQuery.buildFilter(groupActive = false, queryActive = false)
        assertEquals("playlistId = :playlistId", spec.where)
        assertEquals(listOf("playlistId"), spec.namedArgs)
    }

    @Test
    fun `group and name predicates are combined in one sql filter`() {
        val spec = ChannelPagingQuery.buildFilter(groupActive = true, queryActive = true)
        assertTrue(spec.where.contains(ChannelPagingQuery.GROUP_FILTER))
        assertTrue(spec.where.contains(ChannelPagingQuery.QUERY_FILTER))
        assertEquals(listOf("playlistId", "group", "query"), spec.namedArgs)
    }

    @Test
    fun `individual predicates keep named args aligned with clauses`() {
        assertEquals(
            listOf("playlistId", "group"),
            ChannelPagingQuery.buildFilter(true, false).namedArgs,
        )
        assertEquals(
            listOf("playlistId", "query"),
            ChannelPagingQuery.buildFilter(false, true).namedArgs,
        )
    }

    @Test
    fun `dao filtered sql is exactly the combined helper spec`() {
        val spec = ChannelPagingQuery.buildFilter(groupActive = true, queryActive = true)
        assertEquals(
            "SELECT * FROM channels WHERE " + spec.where + ChannelPagingQuery.ORDER_BY,
            ChannelPagingQuery.FILTERED_SQL,
        )
    }

    @Test
    fun `no paging query has a silent limit`() {
        pagingSql.forEach { sql ->
            assertFalse(sql.contains("LIMIT", ignoreCase = true))
            assertTrue(sql.contains(ChannelPagingQuery.ORDER_BY))
        }
    }

    @Test
    fun `all paging queries preserve provider order`() {
        pagingSql.forEach { sql ->
            assertTrue(sql.endsWith("ORDER BY orderIndex, id"))
        }
    }
}
