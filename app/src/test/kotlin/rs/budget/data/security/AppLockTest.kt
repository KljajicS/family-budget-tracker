package rs.budget.data.security

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var saved = false
    private var writable = true
    private val writes = mutableListOf<Boolean>()

    private fun lock(enabled: Boolean = saved) = AppLock({ enabled }, { value ->
        writes += value
        if (writable) saved = value
        writable
    }).also { it.onForeground() }

    private val AppLock.op get() = state.value.operation!!

    private fun AppLock.unlock() {
        if (!consumeAutoPrompt()) assertTrue(request(LockPurpose.UNLOCK))
        succeed(op.id)
    }

    @Test
    fun `cold start is locked only when enabled and auto prompts once`() {
        assertFalse(lock(enabled = false).state.value.locked)
        val lock = lock(enabled = true)
        assertTrue(lock.state.value.locked)
        assertTrue(lock.consumeAutoPrompt())
        assertEquals(LockPurpose.UNLOCK, lock.op.purpose)
        lock.fail(lock.op.id, LockFailure.CANCELLED)
        assertFalse(lock.consumeAutoPrompt())
    }

    @Test
    fun `failed or cancelled unlock stays locked until an explicit retry succeeds`() {
        val lock = lock(enabled = true)
        assertTrue(lock.request(LockPurpose.UNLOCK))
        lock.fail(lock.op.id, LockFailure.ERROR)
        assertTrue(lock.state.value.locked)
        assertEquals(LockError(LockPurpose.UNLOCK, LockFailure.ERROR), lock.state.value.error)
        lock.unlock()
        assertFalse(lock.state.value.locked)
        assertNull(lock.state.value.error)
    }

    @Test
    fun `stale and repeated results are ignored`() {
        val lock = lock(enabled = true)
        assertTrue(lock.request(LockPurpose.UNLOCK))
        val first = lock.op.id
        assertFalse(lock.request(LockPurpose.UNLOCK))
        lock.fail(first, LockFailure.CANCELLED)
        assertTrue(lock.request(LockPurpose.UNLOCK))
        lock.succeed(first)
        assertTrue(lock.state.value.locked)
        lock.credentialResult(confirmed = true)
        assertTrue(lock.state.value.locked)
    }

    @Test
    fun `enabling needs a successful authentication before anything is saved`() {
        val lock = lock(enabled = false)
        assertTrue(lock.request(LockPurpose.ENABLE))
        lock.fail(lock.op.id, LockFailure.CANCELLED)
        assertFalse(lock.state.value.enabled)
        assertTrue(writes.isEmpty())

        assertTrue(lock.request(LockPurpose.ENABLE))
        lock.succeed(lock.op.id)
        assertTrue(lock.state.value.enabled)
        assertFalse(lock.state.value.locked)
        assertTrue(saved)
        assertTrue(lock().state.value.locked)
    }

    @Test
    fun `disabling needs an unlocked app and a fresh authentication`() {
        saved = true
        val lock = lock()
        assertFalse(lock.request(LockPurpose.DISABLE))
        lock.unlock()
        assertTrue(lock.request(LockPurpose.DISABLE))
        lock.fail(lock.op.id, LockFailure.UNAVAILABLE)
        assertTrue(lock.state.value.enabled)
        assertTrue(writes.isEmpty())

        assertTrue(lock.request(LockPurpose.DISABLE))
        lock.succeed(lock.op.id)
        assertFalse(lock.state.value.enabled)
        assertFalse(saved)
    }

    @Test
    fun `failed saves keep the previous setting without auto disabling`() {
        writable = false
        val off = lock(enabled = false)
        assertTrue(off.request(LockPurpose.ENABLE))
        off.succeed(off.op.id)
        assertFalse(off.state.value.enabled)
        assertEquals(LockError(LockPurpose.ENABLE, LockFailure.SAVE_FAILED), off.state.value.error)

        val on = AppLock({ true }, { throw IOException("disk full") }).also { it.onForeground() }
        on.unlock()
        assertTrue(on.request(LockPurpose.DISABLE))
        on.succeed(on.op.id)
        assertTrue(on.state.value.enabled)
        assertEquals(LockError(LockPurpose.DISABLE, LockFailure.SAVE_FAILED), on.state.value.error)
        on.onBackground()
        assertTrue(on.state.value.locked)
    }

    @Test
    fun `background cancels a biometric prompt so a late success cannot unlock`() {
        val lock = lock(enabled = true)
        assertTrue(lock.consumeAutoPrompt())
        val id = lock.op.id
        assertTrue(lock.advance(id, LockStage.BIOMETRIC))
        lock.onBackground()
        lock.succeed(id)
        assertTrue(lock.state.value.locked)
        assertNull(lock.state.value.operation)
        assertFalse(lock.request(LockPurpose.UNLOCK))

        lock.onForeground()
        assertTrue(lock.consumeAutoPrompt())
    }

    @Test
    fun `background relocks an unlocked app and screen off relocks a foreground one`() {
        val lock = lock(enabled = true)
        lock.unlock()
        lock.onBackground()
        assertTrue(lock.state.value.locked)
        lock.onForeground()
        lock.unlock()
        lock.onScreenOff()
        assertTrue(lock.state.value.locked)
    }

    @Test
    fun `legacy credential screen survives its own stop but Home from it stays locked`() {
        saved = true
        val lock = lock()
        lock.unlock()
        assertTrue(lock.request(LockPurpose.DISABLE))
        assertTrue(lock.advance(lock.op.id, LockStage.CREDENTIAL))
        lock.onBackground()
        assertTrue(lock.state.value.locked)
        assertEquals(LockStage.CREDENTIAL, lock.op.stage)

        // Home pressed on the credential screen, user comes back and cancels it.
        lock.onForeground()
        lock.credentialResult(confirmed = false)
        assertTrue(lock.state.value.locked)
        assertTrue(lock.state.value.enabled)
        assertFalse(lock.consumeAutoPrompt())

        assertTrue(lock.request(LockPurpose.UNLOCK))
        assertTrue(lock.advance(lock.op.id, LockStage.CREDENTIAL))
        lock.onBackground()
        lock.onForeground()
        lock.credentialResult(confirmed = true)
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `legacy credential exception is single use and never unlocks in the background`() {
        val lock = lock(enabled = true)
        assertTrue(lock.request(LockPurpose.UNLOCK))
        assertTrue(lock.advance(lock.op.id, LockStage.CREDENTIAL))
        lock.onBackground()
        lock.credentialResult(confirmed = true)
        assertTrue(lock.state.value.locked)
        assertNull(lock.state.value.operation)

        lock.onForeground()
        assertTrue(lock.request(LockPurpose.UNLOCK))
        assertTrue(lock.advance(lock.op.id, LockStage.CREDENTIAL))
        lock.onBackground()
        lock.onForeground()
        lock.onBackground()
        assertNull(lock.state.value.operation)

        lock.onForeground()
        assertTrue(lock.request(LockPurpose.UNLOCK))
        assertTrue(lock.advance(lock.op.id, LockStage.CREDENTIAL))
        lock.onScreenOff()
        lock.credentialResult(confirmed = true)
        assertTrue(lock.state.value.locked)
    }

    @Test
    fun `a new process starts locked and ignores a redelivered credential result`() {
        val before = lock(enabled = false)
        assertTrue(before.request(LockPurpose.ENABLE))
        before.succeed(before.op.id)
        assertTrue(before.request(LockPurpose.DISABLE))
        assertTrue(before.advance(before.op.id, LockStage.CREDENTIAL))
        before.onBackground()

        val after = lock()
        after.credentialResult(confirmed = true)
        assertTrue(after.state.value.locked)
        assertTrue(after.state.value.enabled)
    }

    @Test
    fun `configuration recreation keeps the singleton state because it reports no background`() {
        val lock = lock(enabled = true)
        lock.unlock()
        // Recreation calls onStart again but never onBackground.
        lock.onForeground()
        assertFalse(lock.state.value.locked)
        assertFalse(lock.consumeAutoPrompt())

        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.request(LockPurpose.UNLOCK))
        assertTrue(lock.advance(lock.op.id, LockStage.BIOMETRIC))
        lock.onForeground()
        lock.succeed(lock.op.id)
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `requests and stage changes are refused while in the background`() {
        val lock = lock(enabled = true)
        lock.onBackground()
        assertFalse(lock.request(LockPurpose.UNLOCK))
        assertFalse(lock.consumeAutoPrompt())
        lock.onForeground()
        assertTrue(lock.request(LockPurpose.UNLOCK))
        val id = lock.op.id
        lock.onBackground()
        assertFalse(lock.advance(id, LockStage.CREDENTIAL))
    }
}
