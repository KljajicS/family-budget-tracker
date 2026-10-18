package rs.budget.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import rs.budget.R
import rs.budget.data.security.AppLock
import rs.budget.data.security.LockFailure
import rs.budget.data.security.LockOperation
import rs.budget.data.security.LockPurpose
import rs.budget.data.security.LockStage
import rs.budget.data.security.LockState
import rs.budget.data.security.StrongBiometricKey
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.TitleStyle

/** Runs system authentication for [AppLock] operations. Create it in onCreate so results survive recreation. */
class LockPrompter(private val activity: AppCompatActivity, private val lock: AppLock) {
    private val executor = ContextCompat.getMainExecutor(activity)
    private var prompt: BiometricPrompt? = lock.state.value.operation?.takeIf { it.stage == LockStage.BIOMETRIC }?.let(::prompt)
    private val credential = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lock.credentialResult(it.resultCode == Activity.RESULT_OK)
    }

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                lock.state.collect { s -> s.operation?.takeIf { it.stage == LockStage.REQUESTED }?.let(::start) }
            }
        }
    }

    fun cancel() {
        prompt?.cancelAuthentication()
    }

    private fun start(op: LockOperation) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(op.purpose.title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
        val biometrics = BiometricManager.from(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val allowed = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            if (biometrics.canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) return lock.fail(op.id, LockFailure.UNAVAILABLE)
            if (lock.advance(op.id, LockStage.BIOMETRIC)) prompt(op).authenticate(info.setAllowedAuthenticators(allowed).build())
            return
        }
        val cipher = if (biometrics.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) StrongBiometricKey.cipher() else null
        if (cipher == null) return confirmCredential(op)
        if (lock.advance(op.id, LockStage.BIOMETRIC)) {
            val strong = info.setAllowedAuthenticators(BIOMETRIC_STRONG).setNegativeButtonText(activity.getString(R.string.lock_use_credential)).build()
            prompt(op).authenticate(strong, BiometricPrompt.CryptoObject(cipher))
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmCredential(op: LockOperation) {
        val intent = activity.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(activity.getString(op.purpose.title), activity.getString(R.string.lock_credential_description))
            ?: return lock.fail(op.id, LockFailure.UNAVAILABLE)
        if (!lock.advance(op.id, LockStage.CREDENTIAL)) return
        try {
            credential.launch(intent)
        } catch (_: ActivityNotFoundException) {
            lock.fail(op.id, LockFailure.UNAVAILABLE)
        }
    }

    private fun prompt(op: LockOperation) = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (lock.state.value.operation != op.copy(stage = LockStage.BIOMETRIC)) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || StrongBiometricKey.proves(result.cryptoObject?.cipher)) lock.succeed(op.id)
            else lock.fail(op.id, LockFailure.ERROR)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (lock.state.value.operation != op.copy(stage = LockStage.BIOMETRIC)) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && errorCode in CREDENTIAL_FALLBACK) return confirmCredential(op)
            lock.fail(op.id, when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_TIMEOUT -> LockFailure.CANCELLED
                BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> LockFailure.UNAVAILABLE
                else -> LockFailure.ERROR
            })
        }
    }).also { prompt = it }

    private companion object {
        val CREDENTIAL_FALLBACK = setOf(BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
    }
}

@get:StringRes
private val LockPurpose.title: Int
    get() = when (this) {
        LockPurpose.UNLOCK -> R.string.lock_prompt_unlock
        LockPurpose.ENABLE -> R.string.lock_prompt_enable
        LockPurpose.DISABLE -> R.string.lock_prompt_disable
    }

@get:StringRes
val LockFailure.message: Int
    get() = when (this) {
        LockFailure.CANCELLED -> R.string.lock_cancelled
        LockFailure.UNAVAILABLE -> R.string.lock_unavailable
        LockFailure.ERROR -> R.string.lock_error
        LockFailure.SAVE_FAILED -> R.string.lock_save_failed
    }

@Composable
fun LockScreen(state: LockState, onUnlock: () -> Unit) {
    val c = LocalColors.current
    val activity = LocalActivity.current
    LaunchedEffect(c.dark) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !c.dark }
    }
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = c.primary, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.lock_title), style = TitleStyle, color = c.text, textAlign = TextAlign.Center)
        Text(stringResource(R.string.lock_hint), style = BodyStyle, color = c.muted, textAlign = TextAlign.Center)
        state.error?.let { Text(stringResource(it.failure.message), color = c.orange, textAlign = TextAlign.Center) }
        PrimaryButton(stringResource(R.string.lock_unlock), enabled = state.operation == null, onClick = onUnlock)
    }
}
