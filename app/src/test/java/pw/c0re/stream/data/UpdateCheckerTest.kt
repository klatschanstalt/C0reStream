package pw.c0re.stream.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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

    @Test
    fun `handles multi-digit minor and patch versions numerically`() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.7.10", "1.7.9"))
        assertTrue(UpdateChecker.isNewer("1.11.0", "1.10.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.9"))
        assertFalse(UpdateChecker.isNewer("1.10.0", "1.10.0"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "1.10.0"))
    }

    @Test
    fun `selects localized release notes with fallback`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRENCH)
            val json = JSONObject()
                .put("notes", "fallback")
                .put("notes_de", "Deutsch")
                .put("notes_en", "English")
                .put("notes_fr", "Français")
            assertEquals("Français", UpdateChecker.localizedNotes(json))
            Locale.setDefault(Locale.GERMAN)
            assertEquals("Deutsch", UpdateChecker.localizedNotes(json))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("English", UpdateChecker.localizedNotes(json))
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("fallback", UpdateChecker.localizedNotes(json))
            assertEquals("fallback", UpdateChecker.localizedNotes(JSONObject().put("notes", "fallback")))
        } finally {
            Locale.setDefault(original)
        }
    }
}
