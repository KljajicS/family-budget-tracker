package rs.budget.data.security

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class LockPurpose { UNLOCK, ENABLE, DISABLE }
enum class LockStage { REQUESTED, BIOMETRIC, CREDENTIAL }
enum class LockFailure { CANCELLED, UNAVAILABLE, ERROR, SAVE_FAILED }

data class LockOperation(val id: Long, val purpose: LockPurpose, val stage: LockStage)
data class LockError(val purpose: LockPurpose, val failure: LockFailure)

data class LockState(
    val enabled: Boolean,
    val unlocked: Boolean = false,
    val operation: LockOperation? = null,
    val error: LockError? = null,
) {
    val locked: Boolean get() = enabled && !unlocked
}

/**
 * Device-local access gate. The unlocked flag lives only in this process, so every cold start is locked
 * while the lock is enabled. Each prompt carries an operation id; a result for any other id is stale and
 * ignored. Main thread only.
 */
class AppLock(read: () -> Boolean, private val write: (Boolean) -> Boolean) {
    private val _state = MutableStateFlow(LockState(enabled = read()))
    val state: StateFlow<LockState> = _state
    private var lastId = 0L
    private var foreground = false
    private var autoPrompt = true
    private var credentialMayStop = false

    fun onForeground() {
        foreground = true
    }

    /** A stop that is not a configuration change. */
    fun onBackground() {
        foreground = false
        if (credentialMayStop && _state.value.operation?.stage == LockStage.CREDENTIAL) {
            // The legacy credential screen is an activity on top of ours, so launching it stops us exactly
            // once. Keep that operation for its result, but lock now: Home from there must not leave us open.
            credentialMayStop = false
            _state.update { it.copy(unlocked = false) }
        } else relock()
    }

    fun onScreenOff() = relock()

    private fun relock() {
        credentialMayStop = false
        autoPrompt = true
        _state.update { it.copy(unlocked = false, operation = null, error = null) }
    }

    /** Starts at most one unlock prompt per lock, so a cancelled prompt never reopens on its own. */
    fun consumeAutoPrompt(): Boolean {
        val s = _state.value
        if (!autoPrompt || !foreground || !s.locked || s.operation != null) return false
        autoPrompt = false
        return request(LockPurpose.UNLOCK)
    }

    fun request(purpose: LockPurpose): Boolean {
        val s = _state.value
        val allowed = when (purpose) {
            LockPurpose.UNLOCK -> s.locked
            LockPurpose.ENABLE -> !s.enabled
            LockPurpose.DISABLE -> s.enabled && s.unlocked
        }
        if (!foreground || s.operation != null || !allowed) return false
        _state.value = s.copy(operation = LockOperation(++lastId, purpose, LockStage.REQUESTED), error = null)
        return true
    }

    fun advance(id: Long, stage: LockStage): Boolean {
        val op = current(id) ?: return false
        if (!foreground) return false
        credentialMayStop = stage == LockStage.CREDENTIAL
        _state.update { it.copy(operation = op.copy(stage = stage)) }
        return true
    }

    fun succeed(id: Long) {
        val op = current(id) ?: return
        if (!foreground) return fail(id, LockFailure.CANCELLED)
        credentialMayStop = false
        val s = _state.value.copy(operation = null)
        _state.value = when (op.purpose) {
            LockPurpose.UNLOCK -> s.copy(unlocked = true)
            LockPurpose.ENABLE ->
                if (save(true)) s.copy(enabled = true, unlocked = true) else s.copy(error = LockError(op.purpose, LockFailure.SAVE_FAILED))
            LockPurpose.DISABLE ->
                if (save(false)) s.copy(enabled = false, unlocked = true)
                else s.copy(unlocked = true, error = LockError(op.purpose, LockFailure.SAVE_FAILED))
        }
    }

    fun fail(id: Long, failure: LockFailure) {
        val op = current(id) ?: return
        credentialMayStop = false
        _state.update { it.copy(operation = null, error = LockError(op.purpose, failure)) }
    }

    /** The credential activity result carries no id; only a live credential operation of this process may use it. */
    fun credentialResult(confirmed: Boolean) {
        val op = _state.value.operation?.takeIf { it.stage == LockStage.CREDENTIAL } ?: return
        if (confirmed) succeed(op.id) else fail(op.id, LockFailure.CANCELLED)
    }

    private fun current(id: Long) = _state.value.operation?.takeIf { it.id == id }

    private fun save(enabled: Boolean) = runCatching { write(enabled) }.getOrDefault(false)

    companion object {
        @SuppressLint("UseKtx")
        fun fromPreferences(context: Context): AppLock {
            val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
            return AppLock({ prefs.getBoolean("enabled", false) }, { prefs.edit().putBoolean("enabled", it).commit() })
        }
    }
}
