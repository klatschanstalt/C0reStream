package pw.c0re.stream.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for Xtream array/paging assumptions and explicit limit reporting. */
class XtreamApiTest {

    private val categoriesJson = """
        [{"category_id":"1","category_name":"News"},{"category_id":"2","category_name":"Sport"}]
    """.trimIndent()

    private fun stream(index: Int, id: Int = index + 1) = """
        {"stream_id":"$id","name":"Channel $index","category_id":"${index % 2 + 1}",
         "container_extension":"ts","epg_channel_id":"epg-$index",
         "channel_num":"${index + 1}","quality":"HD"}
    """.trimIndent()

    private fun server(
        streams: String,
        categories: String = categoriesJson,
        categoryCode: Int = 200,
    ): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                return when (action) {
                    "get_live_categories" -> MockResponse().setResponseCode(categoryCode).setBody(categories)
                    "get_live_streams" -> MockResponse().setBody(streams)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `live channels keep provider order group number and tvg id`() = runTest {
        val streams = "[${stream(0)},${stream(1)},${stream(2)}]"
        val server = server(streams)
        try {
            val creds = XtreamApi.Credentials(server.url("/").toString(), "user", "pass")
            val report = XtreamApi(NetworkPolicy.client()).fetchLiveChannelsWithReport(creds)
            assertEquals(3, report.channels.size)
            assertEquals(listOf("Channel 0", "Channel 1", "Channel 2"), report.channels.map { it.name })
            assertEquals("News", report.channels[0].group)
            assertEquals("Sport", report.channels[1].group)
            assertEquals("1", report.channels[0].channelNumber)
            assertEquals("HD", report.channels[0].quality)
            assertEquals("epg-0", report.channels[0].tvgId)
            assertEquals(3, report.stats.accepted)
            assertEquals(0, report.stats.rejected)
            assertFalse(report.stats.truncated)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `paging hints are reported instead of being treated as a complete list`() = runTest {
        val streams = "{\"data\":[${stream(0)}],\"page\":1,\"per_page\":1,\"has_more\":true}"
        val server = server(streams)
        try {
            val creds = XtreamApi.Credentials(server.url("/").toString(), "user", "pass")
            val report = XtreamApi(NetworkPolicy.client()).fetchLiveChannelsWithReport(creds)
            assertTrue(report.paginationDetected)
            assertEquals(1, report.channels.size)
            assertTrue(report.notes.any { it.contains("paging", ignoreCase = true) })
            assertEquals(
                1,
                report.stats.rejectedCount(ImportRejectionCode.XTREAM_PAGINATION_UNSUPPORTED),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `record limit is reported and not silently applied`() = runTest {
        val streams = "[${stream(0)},${stream(1)},${stream(2)},${stream(3)}]"
        val server = server(streams)
        try {
            val creds = XtreamApi.Credentials(server.url("/").toString(), "user", "pass")
            val api = XtreamApi(NetworkPolicy.client(), XtreamLimits(maxJsonRecords = 2))
            val report = api.fetchLiveChannelsWithReport(creds)
            assertEquals(2, report.channels.size)
            assertTrue(report.stats.truncated)
            assertEquals(4, report.stats.received)
            assertEquals(1, report.stats.rejectedCount(ImportRejectionCode.XTREAM_RECORD_LIMIT))
            assertTrue(report.notes.any { it.contains("first 2") })
            assertEquals(2, report.limits.maxJsonRecords)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `category failure is reported without losing channels`() = runTest {
        val streams = "[${stream(0)},${stream(1)}]"
        val server = server(streams, categoryCode = 500)
        try {
            val creds = XtreamApi.Credentials(server.url("/").toString(), "user", "pass")
            val report = XtreamApi(NetworkPolicy.client()).fetchLiveChannelsWithReport(creds)
            assertEquals(2, report.channels.size)
            assertNull(report.channels[0].group)
            assertEquals(
                1,
                report.stats.rejectedCount(ImportRejectionCode.XTREAM_CATEGORIES_UNAVAILABLE),
            )
            assertTrue(report.notes.any { it.contains("group labels") })
        } finally {
            server.shutdown()
        }
    }
}
