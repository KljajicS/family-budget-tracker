package app.kinjar.data.sync.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import app.kinjar.data.sync.crypto.ANDROID_KEYSTORE
import app.kinjar.data.sync.crypto.ByteReader
import app.kinjar.data.sync.crypto.ByteWriter
import app.kinjar.data.sync.crypto.MalformedBytes
import app.kinjar.data.sync.crypto.androidKeyStore
import app.kinjar.data.sync.crypto.ascii
import app.kinjar.data.sync.crypto.syncDir
import app.kinjar.data.sync.crypto.syncId
import app.kinjar.data.sync.crypto.writeAtomically
import app.kinjar.domain.newId
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

sealed interface InstallState {
    data class Ready(val installId: String) : InstallState

    data object Absent : InstallState

    /** Identity files this keystore cannot prove: copied app data, or a damaged file. Never publish under it. */
    data class Untrusted(val reason: Reason) : InstallState {
        enum class Reason { COPIED, CORRUPT_FILE }
    }

    /** Keystore or storage failed in a way that may pass. Retry later; never replace the identity. */
    data object Unavailable : InstallState
}

/** A signing key pair whose private half never leaves its keystore. */
interface IdentityKey {
    /** Replaces any previous key pair and returns the X.509 encoded public key. */
    fun create(): ByteArray

    /** Null when the key is gone. */
    fun sign(data: ByteArray): ByteArray?

    fun delete()

    /** True when [e] means the key can never sign again, as opposed to a passing keystore failure. */
    fun isPermanent(e: Exception): Boolean
}

/**
 * Sync publisher id of this install, bound to a non-exportable keystore key. The binding file is excluded from
 * backup and device transfer, and a copy of it elsewhere fails the signature check, so two phones never
 * publish under one id.
 *
 * File: "FBID" | version | installId | publicKey.
 */
class InstallIdentity(dir: File, private val key: IdentityKey) {
    private val file = File(dir, "install_identity.bin")

    @Synchronized
    fun load(): InstallState {
        val bytes = try {
            if (!file.exists()) return InstallState.Absent
            if (file.length() > MAX_FILE_BYTES) return InstallState.Untrusted(InstallState.Untrusted.Reason.CORRUPT_FILE)
            file.readBytes()
        } catch (_: IOException) {
            return InstallState.Unavailable
        }
        val (installId, publicKey) = parse(bytes) ?: return InstallState.Untrusted(InstallState.Untrusted.Reason.CORRUPT_FILE)
        val challenge = CHALLENGE + installId.ascii()
        val signature = try {
            key.sign(challenge) ?: return InstallState.Untrusted(InstallState.Untrusted.Reason.COPIED)
        } catch (e: Exception) {
            return if (key.isPermanent(e)) InstallState.Untrusted(InstallState.Untrusted.Reason.COPIED) else InstallState.Unavailable
        }
        val valid = try {
            Signature.getInstance(SIGNATURE).run {
                initVerify(publicKey)
                update(challenge)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }
        return if (valid) InstallState.Ready(installId) else InstallState.Untrusted(InstallState.Untrusted.Reason.COPIED)
    }

    /** A new id and key pair. Refuses while the current identity is trusted or cannot be checked. */
    @Synchronized
    fun create(): InstallState.Ready {
        when (load()) {
            is InstallState.Ready -> error("Install identity already exists")
            InstallState.Unavailable -> error("Install identity cannot be checked now")
            else -> Unit
        }
        val installId = newId()
        val publicKey = key.create()
        writeAtomically(file, ByteWriter().raw(MAGIC).byte(VERSION).short(installId.ascii()).short(publicKey).bytes())
        return InstallState.Ready(installId)
    }

    private fun parse(bytes: ByteArray): Pair<String, PublicKey>? = try {
        val reader = ByteReader(bytes)
        if (!reader.raw(MAGIC.size).contentEquals(MAGIC) || reader.byte() != VERSION) throw MalformedBytes()
        val installId = reader.syncId()
        val encoded = reader.short()
        reader.end()
        installId to KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    } catch (_: MalformedBytes) {
        null
    } catch (_: GeneralSecurityException) {
        null
    }

    companion object {
        private const val VERSION = 1
        private const val MAX_FILE_BYTES = 1024L
        private const val SIGNATURE = "SHA256withECDSA"
        private val MAGIC = "FBID".ascii()
        private val CHALLENGE = "family-budget-install-identity".ascii()

        fun forApp(context: Context) = InstallIdentity(syncDir(context), AndroidKeystoreIdentityKey("sync_install_identity"))
    }
}

class AndroidKeystoreIdentityKey(private val alias: String) : IdentityKey {
    override fun create(): ByteArray {
        delete()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKeyPair().public.encoded
        }
    }

    override fun sign(data: ByteArray): ByteArray? {
        val privateKey = androidKeyStore().getKey(alias, null) as PrivateKey? ?: return null
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    override fun delete() {
        androidKeyStore().run { if (containsAlias(alias)) deleteEntry(alias) }
    }

    override fun isPermanent(e: Exception) = e is KeyPermanentlyInvalidatedException
}
