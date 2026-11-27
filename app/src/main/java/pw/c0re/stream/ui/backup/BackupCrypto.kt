package pw.c0re.stream.ui.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, versioned authenticated backup envelope. */
object BackupCrypto {
    private val magic = "C0REBK01".toByteArray(Charsets.US_ASCII)
    private const val iterations = 600000
    private const val maxPayload = 32 * 1024 * 1024
    private const val headerSize = 36

    private fun cipher(mode: Int, password: CharArray, header: ByteArray): Cipher {
        val spec = PBEKeySpec(password, header.copyOfRange(8, 24), iterations, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(24, 36)))
                updateAAD(header)
            }
        } finally { key.fill(0) }
    }

    fun encrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(password.size in 12..1024) { "Backup password must contain 12–1024 characters" }
        require(payload.size <= maxPayload) { "Backup too large" }
        val random = ByteArray(28).also { SecureRandom().nextBytes(it) }
        val header = magic + random
        return header + cipher(Cipher.ENCRYPT_MODE, password, header).doFinal(payload)
    }

    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        require(password.size in 12..1024)
        require(envelope.size in (headerSize + 16)..(headerSize + maxPayload + 16))
        val header = envelope.copyOfRange(0, headerSize)
        require(header.copyOfRange(0, 8).contentEquals(magic)) { "Unsupported backup format" }
        return cipher(Cipher.DECRYPT_MODE, password, header).doFinal(envelope, headerSize, envelope.size - headerSize)
    }
}
