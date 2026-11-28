package pw.c0re.stream.data.profiles

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate state-machine tests: closed is the default and errors never open protected UI. */
class ProfileGateTest {
    private class FakeStore : ProfilePinStore {
        var entities = mutableMapOf<Long, ProfileEntity>()
        var onRead: (suspend () -> Unit)? = null

        override suspend fun byId(id: Long): ProfileEntity? {
            onRead?.invoke()
            return entities[id]
        }

        override suspend fun upsert(profile: ProfileEntity) {
            entities[profile.id] = profile
        }
    }

    private class Fixture {
        val pins = PinManager()
        val record = pins.hash("1234".toCharArray())
        val store = FakeStore().apply {
            entities[1L] = ProfileEntity(id = 1L, name = "Kids", pinHash = record, createdAt = 1L)
        }
        val gate = ProfileGate(store, pins)
    }

    @Test
    fun `gate starts loading with no profile information`() {
        val f = Fixture()
        assertEquals(ProfileGatePhase.LOADING, f.gate.state.value.phase)
        assertNull(f.gate.state.value.profileName)
    }

    @Test
    fun `absent selection opens the gate but a protected profile locks it`() = runTest {
        val f = Fixture()
        f.gate.observeActive(ActiveProfile.Unset)
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)

        f.gate.observeActive(ActiveProfile.Selected(1L))
        assertEquals(ProfileGatePhase.LOADING, f.gate.state.value.phase)
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        assertEquals("Kids", f.gate.state.value.profileName)
    }

    @Test
    fun `unprotected profile opens the gate after loading`() = runTest {
        val f = Fixture()
        f.store.entities[1L] = f.store.entities[1L]!!.copy(pinHash = null)
        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)
    }

    @Test
    fun `delayed profile query keeps the gate closed until the record is known`() = runTest {
        val f = Fixture()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.store.onRead = { reached.complete(Unit); release.await() }
        f.gate.observeActive(ActiveProfile.Selected(1L))
        val job = async { f.gate.evaluate() }
        reached.await()
        assertEquals(ProfileGatePhase.LOADING, f.gate.state.value.phase)
        release.complete(Unit)
        job.await()
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
    }

    @Test
    fun `correct pin unlocks and wrong pin reports a distinct failure`() = runTest {
        val f = Fixture()
        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.gate.evaluate()
        f.gate.unlock("9999".toCharArray())
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        assertEquals(PinFailure.WRONG_CURRENT, f.gate.state.value.failure)
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)
        assertNull(f.gate.state.value.failure)
    }

    @Test
    fun `profile switch closes the gate again and requires the new profile pin`() = runTest {
        val f = Fixture()
        f.store.entities[2L] = ProfileEntity(
            id = 2L,
            name = "Second",
            pinHash = f.pins.hash("9876".toCharArray()),
            createdAt = 2L,
        )
        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.gate.evaluate()
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)

        assertTrue(f.gate.observeActive(ActiveProfile.Selected(2L)))
        assertEquals(ProfileGatePhase.LOADING, f.gate.state.value.phase)
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        assertEquals("Second", f.gate.state.value.profileName)
        // The previous profile's PIN must not unlock the new one.
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        assertEquals(PinFailure.WRONG_CURRENT, f.gate.state.value.failure)
        f.gate.unlock("9876".toCharArray())
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)
    }

    @Test
    fun `missing profile and corrupt record stay locked and recover only on retry`() = runTest {
        val f = Fixture()
        f.gate.observeActive(ActiveProfile.Selected(99L))
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.ERROR, f.gate.state.value.phase)
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.ERROR, f.gate.state.value.phase)

        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.store.entities[1L] = f.store.entities[1L]!!.copy(pinHash = "corrupt")
        f.gate.evaluate()
        assertEquals(ProfileGatePhase.ERROR, f.gate.state.value.phase)
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.ERROR, f.gate.state.value.phase)

        f.store.entities[1L] = f.store.entities[1L]!!.copy(pinHash = f.record)
        f.gate.retry()
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
    }

    @Test
    fun `stale record cannot unlock after a pin change`() = runTest {
        val f = Fixture()
        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.gate.evaluate()
        val changed = f.pins.hash("5678".toCharArray())
        f.store.entities[1L] = f.store.entities[1L]!!.copy(pinHash = changed)
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        f.gate.unlock("5678".toCharArray())
        assertEquals(ProfileGatePhase.UNLOCKED, f.gate.state.value.phase)
    }

    @Test
    fun `repeated wrong gate attempts end in lockout`() = runTest {
        val f = Fixture()
        f.gate.observeActive(ActiveProfile.Selected(1L))
        f.gate.evaluate()
        repeat(4) { f.gate.unlock("9999".toCharArray()) }
        assertEquals(PinFailure.WRONG_CURRENT, f.gate.state.value.failure)
        repeat(2) { f.gate.unlock("9999".toCharArray()) }
        assertEquals(PinFailure.TOO_MANY_ATTEMPTS, f.gate.state.value.failure)
        f.gate.unlock("1234".toCharArray())
        assertEquals(ProfileGatePhase.LOCKED, f.gate.state.value.phase)
        assertEquals(PinFailure.TOO_MANY_ATTEMPTS, f.gate.state.value.failure)
    }
}
