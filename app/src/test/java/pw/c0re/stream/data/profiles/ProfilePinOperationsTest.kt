package pw.c0re.stream.data.profiles

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression tests for PIN changes/removals; no Android classes are involved. */
class ProfilePinOperationsTest {
    private class FakeStore(initial: ProfileEntity?) : ProfilePinStore {
        var entity = initial
        var upserts = 0
        var onRead: (suspend () -> Unit)? = null
        var onBeforeWrite: (suspend () -> Unit)? = null

        override suspend fun byId(id: Long): ProfileEntity? {
            onRead?.invoke()
            return entity?.takeIf { it.id == id }
        }

        override suspend fun upsert(profile: ProfileEntity) {
            onBeforeWrite?.invoke()
            upserts++
            entity = profile
        }
    }

    private class FakeSession(initial: Long = 1L) : PinSession {
        var active: Long = initial

        override suspend fun activeId(): Long = active
        override suspend fun setActive(id: Long) {
            active = id
        }
    }

    private class Fixture {
        val pins = PinManager()
        val record = pins.hash("1234".toCharArray())
        val store = FakeStore(ProfileEntity(id = 1L, name = "Kids", pinHash = record, createdAt = 1L))
        val session = FakeSession(1L)
        val operations = ProfilePinOperations(store, session, pins)
    }

    private fun failure(reason: PinFailure) = PinOperationResult.Failure(reason)

    @Test
    fun `correct change verifies old pin and stores a new hash`() = runTest {
        val f = Fixture()
        val result = f.operations.changePin(
            1L,
            "1234".toCharArray(),
            "5678".toCharArray(),
            "5678".toCharArray(),
        )
        assertEquals(PinOperationResult.Success, result)
        assertEquals(1, f.store.upserts)
        val stored = f.store.entity?.pinHash
        assertNotEquals(f.record, stored)
        assertEquals(PinVerification.Success, f.pins.verify(stored, "5678".toCharArray(), 1L))
        assertNotEquals(PinVerification.Success, f.pins.verify(stored, "1234".toCharArray(), 1L))
    }

    @Test
    fun `change with wrong current pin is rejected without mutation`() = runTest {
        val f = Fixture()
        val result = f.operations.changePin(
            1L,
            "9999".toCharArray(),
            "5678".toCharArray(),
            "5678".toCharArray(),
        )
        assertEquals(failure(PinFailure.WRONG_CURRENT), result)
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `mismatched confirmation and invalid format are rejected separately`() = runTest {
        val f = Fixture()
        assertEquals(
            failure(PinFailure.MISMATCHED_NEW),
            f.operations.changePin(1L, "1234".toCharArray(), "5678".toCharArray(), "5679".toCharArray()),
        )
        assertEquals(
            failure(PinFailure.INVALID_FORMAT),
            f.operations.changePin(1L, "1234".toCharArray(), "12ab".toCharArray(), "12ab".toCharArray()),
        )
        assertEquals(
            failure(PinFailure.INVALID_FORMAT),
            f.operations.changePin(1L, "1234".toCharArray(), "12".toCharArray(), "12".toCharArray()),
        )
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `setting a pin on an unprotected profile needs no current pin`() = runTest {
        val f = Fixture()
        f.store.entity = f.store.entity?.copy(pinHash = null)
        assertEquals(
            PinOperationResult.Success,
            f.operations.changePin(1L, charArrayOf(), "5678".toCharArray(), "5678".toCharArray()),
        )
        assertEquals(1, f.store.upserts)
        assertEquals(PinVerification.Success, f.pins.verify(f.store.entity?.pinHash, "5678".toCharArray(), 1L))
    }

    @Test
    fun `correct removal clears the stored hash`() = runTest {
        val f = Fixture()
        assertEquals(PinOperationResult.Success, f.operations.removePin(1L, "1234".toCharArray()))
        assertEquals(1, f.store.upserts)
        assertNull(f.store.entity?.pinHash)
        assertEquals(PinVerification.NoPin, f.pins.verify(f.store.entity?.pinHash, "1234".toCharArray(), 1L))
    }

    @Test
    fun `removal without valid authorization is rejected`() = runTest {
        val f = Fixture()
        assertEquals(failure(PinFailure.WRONG_CURRENT), f.operations.removePin(1L, charArrayOf()))
        assertEquals(failure(PinFailure.WRONG_CURRENT), f.operations.removePin(1L, "9999".toCharArray()))
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `unknown profile is never mutated`() = runTest {
        val f = Fixture()
        assertEquals(
            failure(PinFailure.PROFILE_UNAVAILABLE),
            f.operations.changePin(2L, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray()),
        )
        assertEquals(failure(PinFailure.PROFILE_UNAVAILABLE), f.operations.removePin(2L, "1234".toCharArray()))
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `cancellation before commit leaves data unchanged`() = runTest {
        val f = Fixture()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.store.onBeforeWrite = { reached.complete(Unit); release.await() }
        val job = launch {
            f.operations.changePin(1L, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        }
        reached.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `profile switch during operation aborts the write`() = runTest {
        val f = Fixture()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.store.onRead = { reached.complete(Unit); release.await() }
        val job = async {
            f.operations.changePin(1L, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        }
        reached.await()
        // Model a switch that lands after the operation started but before its write.
        f.session.active = 2L
        release.complete(Unit)
        assertEquals(failure(PinFailure.PROFILE_CHANGED), job.await())
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }

    @Test
    fun `parallel pin operations and switches are refused`() = runTest {
        val f = Fixture()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.store.onRead = { reached.complete(Unit); release.await() }
        val first = async {
            f.operations.changePin(1L, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        }
        reached.await()
        assertEquals(
            failure(PinFailure.BUSY),
            f.operations.changePin(1L, "1234".toCharArray(), "9999".toCharArray(), "9999".toCharArray()),
        )
        assertEquals(failure(PinFailure.BUSY), f.operations.unlockAndSwitch(1L, "1234".toCharArray()))
        assertEquals(1L, f.session.active)
        release.complete(Unit)
        assertEquals(PinOperationResult.Success, first.await())
    }

    @Test
    fun `repeated wrong pins hit a bounded lockout`() = runTest {
        val f = Fixture()
        val wrong = "9999".toCharArray()
        repeat(4) {
            assertEquals(failure(PinFailure.WRONG_CURRENT), f.operations.removePin(1L, wrong))
        }
        val fifth = f.operations.removePin(1L, wrong)
        assertTrue(fifth is PinOperationResult.Failure)
        val fifthFailure = fifth as PinOperationResult.Failure
        assertEquals(PinFailure.TOO_MANY_ATTEMPTS, fifthFailure.reason)
        assertTrue("lockout must report a retry delay", fifthFailure.retryAfterMs > 0L)
        val locked = f.operations.changePin(1L, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        assertTrue(locked is PinOperationResult.Failure)
        val lockedFailure = locked as PinOperationResult.Failure
        assertEquals(PinFailure.TOO_MANY_ATTEMPTS, lockedFailure.reason)
        assertTrue("locked operation must report a retry delay", lockedFailure.retryAfterMs > 0L)
        assertEquals(0, f.store.upserts)
        assertEquals(f.record, f.store.entity?.pinHash)
    }
}
