package pw.c0re.stream.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM coverage: no Keystore, no Room, no network. */
class UrlRecoveryTest {

    @Test fun `format classifies encrypted prefix`() {
        assertEquals(UrlRecovery.Format.ENCRYPTED, UrlRecovery.format("enc:v1:AAAA"))
        assertTrue(UrlRecovery.isEncrypted("enc:v1:"))
    }

    @Test fun `format rejects legacy plaintext and unknown prefixes`() {
        assertEquals(UrlRecovery.Format.UNSUPPORTED, UrlRecovery.format("https://example.com/a.m3u"))
        assertEquals(UrlRecovery.Format.UNSUPPORTED, UrlRecovery.format("enc:v2:AAAA"))
        assertFalse(UrlRecovery.isEncrypted("https://example.com/a.m3u"))
    }

    @Test fun `format treats null and empty as empty`() {
        assertEquals(UrlRecovery.Format.EMPTY, UrlRecovery.format(null))
        assertEquals(UrlRecovery.Format.EMPTY, UrlRecovery.format(""))
        assertFalse(UrlRecovery.isEncrypted(null))
    }

    @Test fun `failure categories map to value-free reasons`() {
        assertEquals(UrlRecovery.Reason.KEY_UNAVAILABLE, UrlRecovery.reason(UrlDecryptResult.Failure.KeyUnavailable))
        assertEquals(UrlRecovery.Reason.CIPHERTEXT_CORRUPT, UrlRecovery.reason(UrlDecryptResult.Failure.CiphertextCorrupt))
        assertEquals(UrlRecovery.Reason.UNSUPPORTED_FORMAT, UrlRecovery.reason(UrlDecryptResult.Failure.UnsupportedFormat))
    }

    @Test fun `placeholder is not a fetchable URL and hides stored value`() {
        val p = ProtectedUrl.unavailable("enc:v1:secret-looking", UrlDecryptResult.Failure.KeyUnavailable)
        assertTrue(p.isUnavailable)
        assertTrue(UrlRecovery.needsReentry(p))
        assertTrue(UrlRecovery.isPlaceholder(p.value))
        assertFalse(UrlRecovery.isPlaceholder("https://example.com/a.m3u"))
        assertTrue(p.value.any { it.isWhitespace() })
        assertFalse(p.value.startsWith("http://"))
        assertFalse(p.value.startsWith("https://"))
        assertEquals("[protected URL]", p.toString())
        assertFalse(p.toString().contains("secret-looking"))
        assertEquals(UrlRecovery.Reason.KEY_UNAVAILABLE, UrlRecovery.reason(p))
    }

    @Test fun `readable urls are not placeholders and need no reentry`() {
        val p = ProtectedUrl("https://example.com/a.m3u")
        assertFalse(p.isUnavailable)
        assertFalse(UrlRecovery.needsReentry(p))
        assertFalse(UrlRecovery.isPlaceholder(p.value))
        assertNull(UrlRecovery.reason(p))
        assertEquals("https://example.com/a.m3u", p.value)
    }

    @Test fun `converter returns placeholder for legacy plaintext without throwing`() {
        val p = ProtectedUrlConverters().decode("https://example.com/a.m3u")
        assertNotNull(p)
        assertTrue(p!!.isUnavailable)
        assertEquals(UrlRecovery.Reason.UNSUPPORTED_FORMAT, UrlRecovery.reason(p))
        assertNull(ProtectedUrlConverters().decode(null))
    }

    @Test fun `converter preserves original stored bytes when writing back an unreadable cell`() {
        val stored = "enc:v1:damaged-but-preserved"
        val p = ProtectedUrl.unavailable(stored, UrlDecryptResult.Failure.CiphertextCorrupt)
        assertEquals(stored, ProtectedUrlConverters().encode(p))
        assertNull(ProtectedUrlConverters().encode(null))
    }
}
