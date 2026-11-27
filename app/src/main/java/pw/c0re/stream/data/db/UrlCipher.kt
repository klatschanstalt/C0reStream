package pw.c0re.stream.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.TypeConverter
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Prefix written by [UrlCipher.encrypt] and expected by [UrlCipher.decrypt]. */
internal const val URL_CIPHER_PREFIX = "enc:v1:"

/** Device-bound authenticated encryption. Never silently replace an unavailable key. */
internal object UrlCipher {
    private const val alias = "c0restream.database.urls.v1"
    private const val ivBytes = 12
    private const val tagBits = 128
    private const val minEncryptedBytes = ivBytes + tagBits / 8

    @Synchronized private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing != null) return existing as SecretKey
        check(create) { "Database encryption key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    /**
     * Strict writer, unchanged wire format so the v2 -> v3 plaintext migration keeps working.
     * Creates the Keystore key when allowed and propagates failures: a caller must never fall
     * back to persisting plaintext or a placeholder instead of a real credential.
     */
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val iv = cipher.iv
        check(iv.size == ivBytes)
        return URL_CIPHER_PREFIX + Base64.encodeToString(
            iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP,
        )
    }

    /**
     * Non-throwing reader for a stored value. Expected Keystore and AEAD failures are returned
     * as classified failures. No key is created, no database is touched, and neither ciphertext
     * nor plaintext is logged or included in a failure.
     */
    fun decrypt(value: String): UrlDecryptResult {
        // Values without the prefix are legacy plaintext or a foreign format. The v2 -> v3
        // migration is the only place that may turn those into encrypted rows.
        if (!value.startsWith(URL_CIPHER_PREFIX)) return UrlDecryptResult.Failure.UnsupportedFormat
        val bytes = try {
            Base64.decode(value.removePrefix(URL_CIPHER_PREFIX), Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return UrlDecryptResult.Failure.CiphertextCorrupt
        }
        if (bytes.size < minEncryptedBytes) return UrlDecryptResult.Failure.CiphertextCorrupt

        val key = existingKeyOrNull() ?: return UrlDecryptResult.Failure.KeyUnavailable
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagBits, bytes.copyOfRange(0, ivBytes)))
            val plaintext = cipher.doFinal(bytes.copyOfRange(ivBytes, bytes.size))
            UrlDecryptResult.Success(plaintext.toString(Charsets.UTF_8))
        } catch (e: InvalidKeyException) {
            UrlDecryptResult.Failure.KeyUnavailable
        } catch (e: GeneralSecurityException) {
            // AEADBadTagException / BadPaddingException: authenticated decryption failed.
            UrlDecryptResult.Failure.CiphertextCorrupt
        }
    }

    /** Reads the existing alias without ever creating it; null means missing or unusable. */
    @Synchronized private fun existingKeyOrNull(): SecretKey? = try {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(alias, null) as? SecretKey
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IOException) {
        null
    }
}

/** Outcome of reading one stored URL. Failures carry no value, ciphertext or exception detail. */
sealed interface UrlDecryptResult {
    data class Success(val value: String) : UrlDecryptResult

    sealed interface Failure : UrlDecryptResult {
        /** Keystore alias missing, locked, destroyed or otherwise unusable. */
        data object KeyUnavailable : Failure
        /** Bad GCM tag, malformed Base64 or truncated payload: authenticated data did not verify. */
        data object CiphertextCorrupt : Failure
        /** No `enc:v1:` prefix: legacy plaintext or an unknown format, never decrypted here. */
        data object UnsupportedFormat : Failure
    }
}

/**
 * One URL cell. When [failure] is non-null the original stored bytes are retained in
 * [storedCiphertext] so a write-back cannot overwrite them with the placeholder.
 */
data class ProtectedUrl(
    val value: String,
    val failure: UrlDecryptResult.Failure? = null,
    internal val storedCiphertext: String? = null,
) {
    val isUnavailable: Boolean get() = failure != null

    override fun toString(): String = "[protected URL]"

    companion object {
        /**
         * Contains spaces and angle brackets, so it is not a valid URI and can never be a
         * fetchable http(s) source. Also identifiable via [UrlRecovery.isPlaceholder].
         */
        const val UNAVAILABLE_VALUE = "<unavailable: protected URL; re-entry required>"

        /** Builds a non-fetchable placeholder that retains the original bytes for write-back. */
        fun unavailable(stored: String, failure: UrlDecryptResult.Failure): ProtectedUrl =
            ProtectedUrl(UNAVAILABLE_VALUE, failure, stored)
    }
}

class ProtectedUrlConverters {
    @TypeConverter
    fun encode(value: ProtectedUrl?): String? = when {
        value == null -> null
        // Write back the original bytes; never persist a placeholder over an unreadable value.
        value.failure != null -> value.storedCiphertext ?: value.value
        else -> UrlCipher.encrypt(value.value)
    }

    @TypeConverter
    fun decode(value: String?): ProtectedUrl? = value?.let { stored ->
        when (val result = UrlCipher.decrypt(stored)) {
            is UrlDecryptResult.Success -> ProtectedUrl(result.value)
            is UrlDecryptResult.Failure -> ProtectedUrl.unavailable(stored, result)
        }
    }
}
