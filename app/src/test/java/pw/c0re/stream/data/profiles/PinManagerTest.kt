package pw.c0re.stream.data.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-only tests: PinManager must not depend on Android classes. */
class PinManagerTest {
    private val pin = "1234".toCharArray()

    @Test
    fun `hash produces a versioned pbkdf2 record without the plaintext pin`() {
        val manager = PinManager()
        val record = manager.hash(pin)
        assertTrue(record.startsWith("v1\$120000\$"))
        assertFalse(record.contains("1234"))
        assertEquals(PinVerification.Success, manager.verify(record, pin, profileId = 1, nowMs = 1_000L))
    }

    @Test
    fun `wrong pin fails and a correct pin clears the failure`() {
        val manager = PinManager()
        val record = manager.hash(pin)
        val wrong = manager.verify(record, "9999".toCharArray(), profileId = 2, nowMs = 1_000L)
        assertTrue(wrong is PinVerification.Failed)
        assertEquals(4, (wrong as PinVerification.Failed).attemptsRemaining)
        assertEquals(PinVerification.Success, manager.verify(record, pin, profileId = 2, nowMs = 2_000L))
        assertFalse(manager.isLocked(2, nowMs = 2_000L))
    }

    @Test
    fun `five failures lock the profile until the cooldown expires`() {
        val manager = PinManager()
        val record = manager.hash(pin)
        val wrong = "0000".toCharArray()
        repeat(4) { index ->
            val result = manager.verify(record, wrong, profileId = 3, nowMs = 10_000L)
            assertEquals(4 - index, (result as PinVerification.Failed).attemptsRemaining)
        }
        val fifth = manager.verify(record, wrong, profileId = 3, nowMs = 10_000L)
        assertTrue(fifth is PinVerification.Locked)
        assertTrue(manager.isLocked(3, nowMs = 10_000L))
        assertTrue(manager.verify(record, pin, profileId = 3, nowMs = 10_000L) is PinVerification.Locked)
        assertEquals(PinVerification.Success, manager.verify(record, pin, profileId = 3, nowMs = 70_001L))
        assertFalse(manager.isLocked(3, nowMs = 70_001L))
    }

    @Test
    fun `each hash uses a unique salt`() {
        val manager = PinManager()
        val first = manager.hash(pin)
        val second = manager.hash(pin)
        assertNotEquals(first, second)
        assertNotEquals(first.split('\$')[2], second.split('\$')[2])
        assertEquals(PinVerification.Success, manager.verify(first, pin, profileId = 4, nowMs = 1_000L))
        assertEquals(PinVerification.Success, manager.verify(second, pin, profileId = 5, nowMs = 1_000L))
    }

    @Test
    fun `failures older than the window are discarded`() {
        val manager = PinManager()
        val record = manager.hash(pin)
        val wrong = "0000".toCharArray()
        repeat(4) { manager.verify(record, wrong, profileId = 8, nowMs = 1_000L) }
        val result = manager.verify(record, wrong, profileId = 8, nowMs = 61_001L)
        assertTrue(result is PinVerification.Failed)
        assertEquals(4, (result as PinVerification.Failed).attemptsRemaining)
    }

    @Test
    fun `reset clears the lockout`() {
        val manager = PinManager()
        val record = manager.hash(pin)
        val wrong = "0000".toCharArray()
        repeat(5) { manager.verify(record, wrong, profileId = 9, nowMs = 1_000L) }
        assertTrue(manager.isLocked(9, nowMs = 1_000L))
        manager.reset(9)
        assertFalse(manager.isLocked(9, nowMs = 1_000L))
        assertEquals(PinVerification.Success, manager.verify(record, pin, profileId = 9, nowMs = 1_000L))
    }

    @Test
    fun `profiles without a pin are unrestricted`() {
        val manager = PinManager()
        assertEquals(PinVerification.NoPin, manager.verify(null, pin, profileId = 6, nowMs = 1_000L))
        assertEquals(PinVerification.NoPin, manager.verify("", pin, profileId = 6, nowMs = 1_000L))
    }

    @Test
    fun `malformed records are rejected`() {
        val manager = PinManager()
        val failure = runCatching {
            manager.verify("not-a-pin-record", pin, profileId = 7, nowMs = 1_000L)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
