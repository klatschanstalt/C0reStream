package pw.c0re.stream.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pw.c0re.stream.ui.player.PlaybackRetryCoordinator

/** Pure JVM coverage for the delayed player work guarding (no Media3 or Android context). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRetryCoordinatorTest {

    @Test
    fun `retry for medium A never prepares medium B after a channel switch`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val playerA = Any()
        val playerB = Any()
        var aPreparations = 0
        var bPreparations = 0

        coordinator.onMediaChanged("A|key", playerA)
        assertTrue(coordinator.scheduleRetry(playerA) { aPreparations++ })

        // The user zaps before the delay expires.
        coordinator.onMediaChanged("B|key", playerB)
        advanceUntilIdle()

        assertEquals(0, aPreparations)
        assertEquals(0, bPreparations)

        // B still gets its own, bounded retry.
        assertTrue(coordinator.scheduleRetry(playerB) { bPreparations++ })
        advanceUntilIdle()
        assertEquals(0, aPreparations)
        assertEquals(1, bPreparations)
    }

    @Test
    fun `newer playback request invalidates a pending retry for the same medium`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val player = Any()
        var executed = 0

        coordinator.onMediaChanged("A|key", player)
        assertTrue(coordinator.scheduleRetry(player) { executed++ })
        coordinator.onPlaybackRequest()
        advanceUntilIdle()

        assertEquals(0, executed)
    }

    @Test
    fun `new error generation and recovery cancel the stale retry`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val player = Any()
        var executed = 0

        coordinator.onMediaChanged("A|key", player)
        assertTrue(coordinator.scheduleRetry(player) { executed++ })
        coordinator.onReady() // playback recovered before the delay expired
        advanceUntilIdle()
        assertEquals(0, executed)

        assertTrue(coordinator.scheduleRetry(player) { executed++ })
        coordinator.invalidatePending() // e.g. a new error or user action supersedes it
        advanceUntilIdle()
        assertEquals(0, executed)
    }

    @Test
    fun `stop or release cancels pending work and forbids a stale manual retry`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val player = Any()
        var executed = 0

        coordinator.onMediaChanged("A|key", player)
        val generation = checkNotNull(coordinator.generationFor(player))
        assertTrue(coordinator.scheduleRetry(player) { executed++ })
        coordinator.onStoppedOrReleased()
        advanceUntilIdle()

        assertEquals(0, executed)
        assertFalse(coordinator.isMediumCurrent(generation))
        assertFalse(coordinator.retryNow(player) { executed++ })
        assertEquals(0, executed)
    }

    @Test
    fun `retries are bounded and a manual retry resets the budget only for the active player`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val player = Any()
        val stalePlayer = Any()
        var executed = 0

        coordinator.onMediaChanged("A|key", player)
        repeat(3) {
            assertTrue(coordinator.scheduleRetry(player) { executed++ })
            advanceUntilIdle()
        }
        assertEquals(3, executed)
        assertFalse(coordinator.scheduleRetry(player) { executed++ })

        assertFalse(coordinator.retryNow(stalePlayer) { executed++ })
        assertTrue(coordinator.retryNow(player) { executed++ })
        assertEquals(4, executed)
        assertTrue(coordinator.scheduleRetry(player) { executed++ })
        advanceUntilIdle()
        assertEquals(5, executed)
    }

    @Test
    fun `progress and sleep generations follow medium and player identity`() = runTest {
        val coordinator = PlaybackRetryCoordinator(this, delayForAttempt = { 1_000L })
        val player = Any()
        coordinator.onMediaChanged("A|key", player)
        val generation = checkNotNull(coordinator.generationFor(player))

        assertTrue(coordinator.isMediumCurrent(generation))
        coordinator.onReady() // an error recovery must not stop progress writes
        assertTrue(coordinator.isMediumCurrent(generation))

        coordinator.onPlayerChanged(null)
        assertFalse(coordinator.isMediumCurrent(generation))
        assertFalse(coordinator.isRetryCurrent(generation))
    }
}
