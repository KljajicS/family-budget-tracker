package rs.budget.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Before API 30 a plain BiometricPrompt cannot be restricted to Class 3 sensors. A Keystore key that needs
 * biometric auth for every use only accepts Class 3 matches, so a working cipher after the prompt is the proof.
 * It protects no data.
 */
object StrongBiometricKey {
    private const val ALIAS = "app_lock_strong_biometric"

    fun cipher(): Cipher? = try {
        init()
    } catch (_: KeyPermanentlyInvalidatedException) {
        runCatching { keyStore().deleteEntry(ALIAS); init() }.getOrNull()
    } catch (_: Exception) {
        null
    }

    fun proves(cipher: Cipher?): Boolean = runCatching { cipher?.doFinal(ByteArray(16)) != null }.getOrDefault(false)

    private fun init(): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey = keyStore().getKey(ALIAS, null) as SecretKey?
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            generateKey()
        }
}
