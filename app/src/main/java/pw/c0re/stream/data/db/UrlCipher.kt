package pw.c0re.stream.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.TypeConverter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound authenticated encryption. Never silently replace an unavailable key. */
internal object UrlCipher {
    private const val alias = "c0restream.database.urls.v1"
    private const val prefix = "enc:v1:"
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
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val iv = cipher.iv
        check(iv.size == 12)
        return prefix + Base64.encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        check(value.startsWith(prefix)) { "Unencrypted database value" }
        val bytes = Base64.decode(value.removePrefix(prefix), Base64.NO_WRAP)
        check(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}

data class ProtectedUrl(val value: String) {
    override fun toString(): String = "[protected URL]"
}

class ProtectedUrlConverters {
    @TypeConverter fun encode(value: ProtectedUrl?): String? = value?.let { UrlCipher.encrypt(it.value) }
    @TypeConverter fun decode(value: String?): ProtectedUrl? = value?.let { ProtectedUrl(UrlCipher.decrypt(it)) }
}
