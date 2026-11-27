package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatchupTest {

    private val start = 1_700_000_000_000L
    private val stop = start + 3_600_000L
    private val now = stop + 86_400_000L

    @Test
    fun `substitutes dollar placeholders`() {
        val url = Catchup.build(
            "https://cdn.example/timeshift?start=\${start}&end=\${end}&duration=\${duration}",
            start,
            stop,
            now,
        )
        assertEquals(
            "https://cdn.example/timeshift?start=${start / 1000}&end=${stop / 1000}&duration=3600",
            url,
        )
    }

    @Test
    fun `substitutes brace placeholders`() {
        val url = Catchup.build(
            "https://cdn.example/play/{utc}/{lutc}/{utcend}/{start}/{end}/{duration}",
            start,
            stop,
            now,
        )
        assertEquals(
            "https://cdn.example/play/${start / 1000}/${now / 1000}/${stop / 1000}/${start / 1000}/${stop / 1000}/3600",
            url,
        )
    }

    @Test
    fun `returns null for sources without placeholders`() {
        assertNull(Catchup.build("https://cdn.example/live.ts", start, stop, now))
    }

    @Test
    fun `returns null for invalid input`() {
        assertNull(Catchup.build("https://cdn.example/{start}", 0, stop, now))
        assertNull(Catchup.build("https://cdn.example/{start}", start, start, now))
        assertNull(Catchup.build("javascript:{start}", start, stop, now))
    }

    @Test
    fun `rejects substituted urls with embedded credentials`() {
        assertNull(Catchup.build("https://user:pass@cdn.example/{start}", start, stop, now))
    }
}
