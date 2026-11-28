package pw.c0re.stream.data.profiles

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/** Narrow storage surface for PIN operations; fakes can implement it in JVM tests. */
interface ProfilePinStore {
    suspend fun byId(id: Long): ProfileEntity?
    suspend fun upsert(profile: ProfileEntity)
}

/** Narrow active-profile surface for PIN operations. */
interface PinSession {
    suspend fun activeId(): Long
    suspend fun setActive(id: Long)
}

class RoomProfilePinStore(private val dao: ProfileDao) : ProfilePinStore {
    override suspend fun byId(id: Long): ProfileEntity? = dao.byId(id)
    override suspend fun upsert(profile: ProfileEntity) {
        dao.upsert(profile)
    }
}

class DataStorePinSession(private val repository: ProfileSessionRepository) : PinSession {
    override suspend fun activeId(): Long = repository.activeId.first()
    override suspend fun setActive(id: Long) {
        repository.setActive(id)
    }
}

/** Distinct, UI-independent reasons why a PIN operation was refused. */
enum class PinFailure {
    WRONG_CURRENT,
    MISMATCHED_NEW,
    INVALID_FORMAT,
    TOO_MANY_ATTEMPTS,
    UNREADABLE_RECORD,
    PROFILE_UNAVAILABLE,
    PROFILE_CHANGED,
    BUSY,
    CANCELLED,
}

sealed interface PinOperationResult {
    object Success : PinOperationResult
    data class Failure(val reason: PinFailure, val retryAfterMs: Long = 0L) : PinOperationResult
}

/** Notifies the gate when a committed PIN change makes stored access configuration stale. */
object ProfileConfigEvents {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    fun notifyChanged(profileId: Long) {
        _changes.tryEmit(profileId)
    }
}

/**
 * All PIN-bearing profile changes and profile switches. The authorization decision lives here,
 * never in a dialog: callers only supply the profile id and PIN input.
 *
 * A single non-reentrant guard serializes operations, the entity is re-read from storage inside
 * the guard (so a stale UI model cannot select a different profile), and the active profile is
 * checked again immediately before any write. Verification failures are counted by [PinManager],
 * which bounds repeated wrong attempts per profile. No write happens on cancel, wrong PIN,
 * mismatch or invalid input; the existing PBKDF2 record format is unchanged.
 */
class ProfilePinOperations(
    private val store: ProfilePinStore,
    private val session: PinSession,
    private val pins: PinManager,
) {
    private val mutex = Mutex()

    /** Sets a PIN on an unprotected profile, or replaces a PIN after verifying the old one. */
    suspend fun changePin(
        profileId: Long,
        currentPin: CharArray,
        newPin: CharArray,
        confirmation: CharArray,
    ): PinOperationResult = exclusive {
        val activeAtStart = session.activeId()
        val entity = store.byId(profileId)
            ?: return@exclusive PinOperationResult.Failure(PinFailure.PROFILE_UNAVAILABLE)
        verifyExisting(profileId, entity.pinHash, currentPin)?.let { return@exclusive it }
        if (!newPin.contentEquals(confirmation)) {
            return@exclusive PinOperationResult.Failure(PinFailure.MISMATCHED_NEW)
        }
        if (!isValidFormat(newPin)) {
            return@exclusive PinOperationResult.Failure(PinFailure.INVALID_FORMAT)
        }
        val record = pins.hash(newPin)
        currentCoroutineContext().ensureActive()
        if (session.activeId() != activeAtStart) {
            return@exclusive PinOperationResult.Failure(PinFailure.PROFILE_CHANGED)
        }
        store.upsert(entity.copy(pinHash = record))
        pins.reset(profileId)
        ProfileConfigEvents.notifyChanged(profileId)
        PinOperationResult.Success
    }

    /** Removes a PIN only after the current PIN is verified. */
    suspend fun removePin(profileId: Long, currentPin: CharArray): PinOperationResult = exclusive {
        val activeAtStart = session.activeId()
        val entity = store.byId(profileId)
            ?: return@exclusive PinOperationResult.Failure(PinFailure.PROFILE_UNAVAILABLE)
        if (entity.pinHash.isNullOrEmpty()) {
            // Nothing to remove: succeed without touching storage.
            return@exclusive PinOperationResult.Success
        }
        verifyExisting(profileId, entity.pinHash, currentPin)?.let { return@exclusive it }
        currentCoroutineContext().ensureActive()
        if (session.activeId() != activeAtStart) {
            return@exclusive PinOperationResult.Failure(PinFailure.PROFILE_CHANGED)
        }
        store.upsert(entity.copy(pinHash = null))
        pins.reset(profileId)
        ProfileConfigEvents.notifyChanged(profileId)
        PinOperationResult.Success
    }

    /** Switches profiles through the same authorization and serialization path. */
    suspend fun unlockAndSwitch(profileId: Long, pin: CharArray): PinOperationResult = exclusive {
        val entity = store.byId(profileId)
            ?: return@exclusive PinOperationResult.Failure(PinFailure.PROFILE_UNAVAILABLE)
        verifyExisting(profileId, entity.pinHash, pin)?.let { return@exclusive it }
        currentCoroutineContext().ensureActive()
        session.setActive(profileId)
        PinOperationResult.Success
    }

    private suspend fun exclusive(block: suspend () -> PinOperationResult): PinOperationResult {
        if (!mutex.tryLock()) return PinOperationResult.Failure(PinFailure.BUSY)
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }

    /** Null means the supplied PIN is acceptable for [record]. */
    private fun verifyExisting(
        profileId: Long,
        record: String?,
        pin: CharArray,
    ): PinOperationResult.Failure? {
        if (record.isNullOrEmpty()) return null
        if (!pins.isValidRecord(record)) {
            return PinOperationResult.Failure(PinFailure.UNREADABLE_RECORD)
        }
        val result = try {
            pins.verify(record, pin, profileId)
        } catch (_: IllegalArgumentException) {
            return PinOperationResult.Failure(PinFailure.UNREADABLE_RECORD)
        }
        return when (result) {
            PinVerification.Success -> null
            // A record exists, so "no PIN" means storage changed underneath us: fail closed.
            PinVerification.NoPin -> PinOperationResult.Failure(PinFailure.UNREADABLE_RECORD)
            is PinVerification.Failed -> PinOperationResult.Failure(PinFailure.WRONG_CURRENT)
            is PinVerification.Locked ->
                PinOperationResult.Failure(PinFailure.TOO_MANY_ATTEMPTS, result.retryAfterMs)
        }
    }

    private fun isValidFormat(pin: CharArray): Boolean =
        pin.size in 4..64 && pin.all { it in '0'..'9' }
}
