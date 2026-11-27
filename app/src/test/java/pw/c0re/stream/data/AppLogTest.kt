package pw.c0re.stream.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogTest {

    @Test
    fun `log redacts urls, user info and credential parameters`() {
        AppLog.log(
            "Test",
            "connect https://user:secret@example.test/live password=hunter2 " +
                "xtream://user:secret@example.test token=abc123 user:secret@example.test",
        )
        val line = AppLog.lines.last()
        assertFalse(line.contains("example.test"))
        assertFalse(line.contains("hunter2"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("abc123"))
        assertTrue(line.contains("[url]"))
        assertTrue(line.contains("[xtream]"))
    }
}
