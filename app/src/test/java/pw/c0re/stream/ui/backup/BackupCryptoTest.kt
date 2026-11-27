package pw.c0re.stream.ui.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    private val password = "correct horse battery".toCharArray()

    @Test
    fun `encrypts and decrypts round trip`() {
        val payload = """{"version":2,"playlists":[]}""".toByteArray()
        val envelope = BackupCrypto.encrypt(payload, password)
        assertFalse(envelope.contentEquals(payload))
        assertArrayEquals(payload, BackupCrypto.decrypt(envelope, password))
    }

    @Test
    fun `rejects wrong password`() {
        val envelope = BackupCrypto.encrypt("secret".toByteArray(), password)
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(envelope, "wrong password!!".toCharArray())
        }
    }

    @Test
    fun `rejects tampered envelope`() {
        val envelope = BackupCrypto.encrypt("secret".toByteArray(), password)
        envelope[envelope.size - 1] = (envelope.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(envelope, password)
        }
    }

    @Test
    fun `rejects unknown magic`() {
        val envelope = BackupCrypto.encrypt("secret".toByteArray(), password)
        envelope[0] = 'X'.code.toByte()
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(envelope, password)
        }
    }

    @Test
    fun `enforces password length`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt("secret".toByteArray(), "short".toCharArray())
        }
        val envelope = BackupCrypto.encrypt("secret".toByteArray(), password)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(envelope, "short".toCharArray())
        }
    }
}
