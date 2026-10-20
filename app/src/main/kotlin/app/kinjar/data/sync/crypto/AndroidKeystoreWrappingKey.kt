package app.kinjar.data.sync.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** No user authentication, so background sync can unwrap while the phone is locked after first unlock. */
class AndroidKeystoreWrappingKey(private val alias: String) : WrappingKey {
    override fun create(): SecretKey {
        delete()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    override fun load(): SecretKey? = androidKeyStore().getKey(alias, null) as SecretKey?

    override fun delete() {
        androidKeyStore().run { if (containsAlias(alias)) deleteEntry(alias) }
    }

    override fun isPermanent(e: Exception) =
        e is KeyPermanentlyInvalidatedException || e is AEADBadTagException
}

internal const val ANDROID_KEYSTORE = "AndroidKeyStore"

internal fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
