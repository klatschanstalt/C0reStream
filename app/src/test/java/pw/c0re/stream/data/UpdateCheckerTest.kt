package pw.c0re.stream.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `detects newer semantic versions`() {
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.3.0"))
        assertTrue(UpdateChecker.isNewer("1.3.1", "1.3.0"))
        assertTrue(UpdateChecker.isNewer("2", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("v1.4", "1.3.9"))
        assertTrue(UpdateChecker.isNewer("1.3.0.1", "1.3"))
    }

    @Test
    fun `does not offer equal or older versions`() {
        assertFalse(UpdateChecker.isNewer("1.3.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.2.9", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.3", "1.3.0"))
    }

    @Test
    fun `release outranks prerelease`() {
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.4.0-beta"))
        assertFalse(UpdateChecker.isNewer("1.4.0-beta", "1.4.0"))
    }

    @Test
    fun `falls back to inequality for unparseable versions`() {
        assertTrue(UpdateChecker.isNewer("custom", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("custom", "custom"))
    }
}
