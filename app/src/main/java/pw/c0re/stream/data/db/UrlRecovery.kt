package pw.c0re.stream.data.db

/**
 * Value-free helpers for routing unreadable URL cells to the existing re-entry flows.
 * Nothing here logs or exposes stored ciphertext or plaintext.
 */
object UrlRecovery {
    /** Reason codes safe to map to string resources; deliberately contain no URL or ciphertext. */
    enum class Reason { KEY_UNAVAILABLE, CIPHERTEXT_CORRUPT, UNSUPPORTED_FORMAT }

    /** Pure on-disk format classification. No Keystore access and no payload parsing. */
    enum class Format { EMPTY, ENCRYPTED, UNSUPPORTED }

    fun format(stored: String?): Format = when {
        stored.isNullOrEmpty() -> Format.EMPTY
        stored.startsWith(URL_CIPHER_PREFIX) -> Format.ENCRYPTED
        else -> Format.UNSUPPORTED
    }

    fun isEncrypted(stored: String?): Boolean = format(stored) == Format.ENCRYPTED

    /** True when a cell must be re-entered by the user before it can be used. */
    fun needsReentry(url: ProtectedUrl?): Boolean = url?.failure != null

    fun reason(failure: UrlDecryptResult.Failure): Reason = when (failure) {
        UrlDecryptResult.Failure.KeyUnavailable -> Reason.KEY_UNAVAILABLE
        UrlDecryptResult.Failure.CiphertextCorrupt -> Reason.CIPHERTEXT_CORRUPT
        UrlDecryptResult.Failure.UnsupportedFormat -> Reason.UNSUPPORTED_FORMAT
    }

    fun reason(url: ProtectedUrl?): Reason? = url?.failure?.let(::reason)

    /** Recognises the read-failure placeholder without inspecting exception state. */
    fun isPlaceholder(value: String?): Boolean = value == ProtectedUrl.UNAVAILABLE_VALUE

    /**
     * Encrypts a freshly entered credential for the existing repository/DAO update flow.
     * Strict by design: throws if the Keystore is unusable, so a failed write cannot silently
     * persist plaintext. The previous value is neither read nor returned here.
     */
    fun prepareReplacement(raw: String): ProtectedUrl = ProtectedUrl(UrlCipher.encrypt(raw))
}
