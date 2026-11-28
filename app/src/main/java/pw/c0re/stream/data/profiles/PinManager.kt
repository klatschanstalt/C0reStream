package pw.c0re.stream.data.profiles

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Outcome of a PIN check. */
sealed interface PinVerification {
    object Success : PinVerification

    /** [attemptsRemaining] counts failed attempts still allowed before a lockout. */
    data class Failed(val attemptsRemaining: Int) : PinVerification

    /** No attempt is made while locked; [retryAfterMs] is the wait before another check. */
    data class Locked(val retryAfterMs: Long) : PinVerification

    /** The profile has no PIN configured, so it is unrestricted. */
    object NoPin : PinVerification
}

/**
 * PIN hashing and verification using PBKDF2-HMAC-SHA256 with a fresh 16-byte salt per hash.
 *
 * Stored records have the form `v1$120000$<base64 salt>$<base64 derived key>`; the PIN itself is
 * never stored. Verification uses a constant-time comparison and rejects a profile after five
 * failures within a rolling one-minute window for one minute.
 *
 * Lockout state is in memory and scoped to this instance (process-local). Callers that need it
 * to survive a process restart must own that persistence; no plaintext or derived PIN data is
 * written by this class. All time arguments use `System.currentTimeMillis()` semantics so tests
 * are deterministic; this class has no Android dependencies.
 */
class PinManager {
    companion object {
        const val MAX_FAILED_ATTEMPTS: Int = 5
        private const val VERSION = "v1"
        private const val ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val WINDOW_MS = 60_000L
        private const val LOCK_MS = 60_000L
    }

    private val secureRandom = SecureRandom()
    private val lock = Any()
    private val failures = HashMap<Long, MutableList<Long>>()

    /**
     * Hashes a PIN (4–64 characters). Callers should wipe [pin] after use; this class copies it
     * into a throwaway `PBEKeySpec` and clears that copy.
     */
    fun hash(pin: CharArray): String {
        require(pin.size in 4..64) { "PIN must contain 4–64 characters" }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        return listOf(
            VERSION,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derive(pin, salt)),
        ).joinToString(separator = "\$")
    }

    /**
     * Verifies [pin] against [record]. A null/empty record returns [PinVerification.NoPin].
     * A malformed record throws `IllegalArgumentException` (corrupt data, not a wrong PIN).
     */
    fun verify(
        record: String?,
        pin: CharArray,
        profileId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): PinVerification {
        if (record.isNullOrEmpty()) return PinVerification.NoPin
        val (salt, expected) = parse(record)
        synchronized(lock) {
            prune(profileId, nowMs)
            val until = lockedUntil(profileId, nowMs)
            if (until > 0L) return PinVerification.Locked(until - nowMs)
        }
        val actual = derive(pin, salt)
        val matches = MessageDigest.isEqual(expected, actual)
        actual.fill(0)
        synchronized(lock) {
            if (matches) {
                failures.remove(profileId)
                return PinVerification.Success
            }
            val timestamps = failures.getOrPut(profileId) { mutableListOf() }
            timestamps.add(nowMs)
            val until = lockedUntil(profileId, nowMs)
            return if (until > 0L) {
                PinVerification.Locked(until - nowMs)
            } else {
                PinVerification.Failed(MAX_FAILED_ATTEMPTS - timestamps.size)
            }
        }
    }

    /**
     * Whether [record] is absent (unrestricted) or a supported hash record. This parses the
     * record only; it makes no verification attempt and does not affect lockout state. Callers
     * use it to fail closed on corrupt storage before asking for a PIN.
     */
    fun isValidRecord(record: String?): Boolean {
        if (record.isNullOrEmpty()) return true
        return try {
            parse(record)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Whether [profileId] is currently locked out. */
    fun isLocked(profileId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            prune(profileId, nowMs)
            lockedUntil(profileId, nowMs) > 0L
        }

    /** Clears failure history for one profile, e.g. after its PIN is changed. */
    fun reset(profileId: Long) {
        synchronized(lock) { failures.remove(profileId) }
    }

    /** Clears all failure history. */
    fun clear() {
        synchronized(lock) { failures.clear() }
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun parse(record: String): Pair<ByteArray, ByteArray> {
        val parts = record.split('\$')
        require(parts.size == 4 && parts[0] == VERSION && parts[1] == ITERATIONS.toString()) {
            "Unsupported PIN record"
        }
        val salt = Base64.getDecoder().decode(parts[2])
        val hash = Base64.getDecoder().decode(parts[3])
        require(salt.size == SALT_BYTES && hash.size == KEY_BITS / 8) { "Invalid PIN record" }
        return salt to hash
    }

    /** Must be called while holding [lock]. */
    private fun prune(profileId: Long, nowMs: Long) {
        val timestamps = failures[profileId] ?: return
        timestamps.removeAll { it <= nowMs - WINDOW_MS }
        if (timestamps.isEmpty()) failures.remove(profileId)
    }

    /** Returns the lockout expiry in ms, or 0 when not locked. Must hold [lock]. */
    private fun lockedUntil(profileId: Long, nowMs: Long): Long {
        val timestamps = failures[profileId] ?: return 0L
        if (timestamps.size < MAX_FAILED_ATTEMPTS) return 0L
        val until = timestamps.last() + LOCK_MS
        return if (nowMs < until) until else 0L
    }
}
