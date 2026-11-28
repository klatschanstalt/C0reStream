package pw.c0re.stream.data.profiles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Active-profile state as reported by DataStore, including "not known yet". */
sealed interface ActiveProfile {
    object Unknown : ActiveProfile
    object Unset : ActiveProfile
    data class Selected(val id: Long) : ActiveProfile
}

enum class ProfileGatePhase { LOADING, LOCKED, UNLOCKED, ERROR }

/** UI state of the gate. Never carries a PIN hash. */
data class ProfileGateState(
    val phase: ProfileGatePhase = ProfileGatePhase.LOADING,
    val profileName: String? = null,
    val failure: PinFailure? = null,
    val retryAfterMs: Long = 0L,
)

/**
 * Decides whether protected UI is reachable. Every state other than [ProfileGatePhase.UNLOCKED]
 * means "closed"; callers must render no protected content and expose no protected route.
 *
 * The active profile is observed first, then its record is loaded and validated. An absent
 * profile id is a known "no profile selected" state; an id that cannot be resolved, a malformed
 * record or any load error leaves the gate in [ProfileGatePhase.ERROR] (closed). Unlock verifies
 * against a freshly re-read record so a PIN change or profile switch cannot be bypassed with a
 * stale snapshot. Profile switches re-lock the gate via [observeActive].
 */
class ProfileGate(
    private val store: ProfilePinStore,
    private val pins: PinManager,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ProfileGateState())
    val state: StateFlow<ProfileGateState> = _state.asStateFlow()

    @Volatile
    private var requested: ActiveProfile = ActiveProfile.Unknown

    @Volatile
    private var current: ProfileEntity? = null

    /**
     * Closes the gate synchronously when the selected profile changes; callers then run [evaluate].
     * Returns true when the selection actually changed.
     */
    fun observeActive(active: ActiveProfile): Boolean {
        if (requested == active) return false
        requested = active
        current = null
        _state.value = ProfileGateState(ProfileGatePhase.LOADING)
        return true
    }

    /** Loads and classifies the currently requested profile. Never unlocks from a stale id. */
    suspend fun evaluate() = mutex.withLock { evaluateLocked() }

    /** Verifies a PIN for the locked profile. The resulting state is the single source of truth. */
    suspend fun unlock(pin: CharArray) = mutex.withLock {
        val entity = current ?: return@withLock
        val active = requested
        if (active !is ActiveProfile.Selected || active.id != entity.id) {
            evaluateLocked()
            return@withLock
        }
        val record = entity.pinHash
        if (record.isNullOrEmpty() || !pins.isValidRecord(record)) {
            lockError()
            return@withLock
        }
        val fresh = try {
            store.byId(entity.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            lockError()
            return@withLock
        }
        if (fresh == null || fresh.pinHash != record) {
            // The PIN or profile changed after loading: do not unlock with the old record.
            evaluateLocked()
            return@withLock
        }
        val result = try {
            pins.verify(record, pin, entity.id)
        } catch (_: IllegalArgumentException) {
            lockError()
            return@withLock
        }
        // A switch may have been observed while verification was in flight.
        val activeNow = requested
        if (activeNow !is ActiveProfile.Selected || activeNow.id != entity.id) {
            evaluateLocked()
            return@withLock
        }
        when (result) {
            PinVerification.Success -> {
                current = null
                _state.value = ProfileGateState(ProfileGatePhase.UNLOCKED)
            }
            // A record existed when we started; "no PIN" now means storage changed. Fail closed.
            PinVerification.NoPin -> lockError()
            is PinVerification.Failed -> _state.value = ProfileGateState(
                phase = ProfileGatePhase.LOCKED,
                profileName = entity.name,
                failure = PinFailure.WRONG_CURRENT,
            )
            is PinVerification.Locked -> _state.value = ProfileGateState(
                phase = ProfileGatePhase.LOCKED,
                profileName = entity.name,
                failure = PinFailure.TOO_MANY_ATTEMPTS,
                retryAfterMs = result.retryAfterMs,
            )
        }
    }

    /** Re-reads storage from an error state. Never opens the gate for an ambiguous profile. */
    suspend fun retry() = mutex.withLock { evaluateLocked() }

    private suspend fun evaluateLocked() {
        current = null
        when (val active = requested) {
            ActiveProfile.Unknown -> _state.value = ProfileGateState(ProfileGatePhase.LOADING)
            // No active profile exists yet, so no profile PIN applies to this session.
            ActiveProfile.Unset -> _state.value = ProfileGateState(ProfileGatePhase.UNLOCKED)
            is ActiveProfile.Selected -> {
                if (active.id <= 0L) {
                    lockError()
                    return
                }
                val entity = try {
                    store.byId(active.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    lockError()
                    return
                }
                if (entity == null) {
                    lockError()
                    return
                }
                val record = entity.pinHash
                if (record.isNullOrEmpty()) {
                    _state.value = ProfileGateState(ProfileGatePhase.UNLOCKED)
                } else if (!pins.isValidRecord(record)) {
                    lockError()
                } else {
                    current = entity
                    _state.value = ProfileGateState(
                        phase = ProfileGatePhase.LOCKED,
                        profileName = entity.name,
                    )
                }
            }
        }
    }

    private fun lockError() {
        current = null
        _state.value = ProfileGateState(ProfileGatePhase.ERROR)
    }
}
