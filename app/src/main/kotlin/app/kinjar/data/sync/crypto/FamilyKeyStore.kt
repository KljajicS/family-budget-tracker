package app.kinjar.data.sync.crypto

import android.content.Context
import java.io.File
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface StoredFamilyKey {
    data object Absent : StoredFamilyKey

    data class Ready(val familyId: String, val key: FamilyKey) : StoredFamilyKey

    /** Enrolled, but this install can never read it again. Only an explicit [FamilyKeyStore.forget] clears it. */
    data class Invalidated(val reason: Reason) : StoredFamilyKey {
        enum class Reason { CORRUPT_FILE, WRAPPING_KEY_MISSING, UNWRAP_FAILED }
    }

    /** Keystore or storage failed in a way that may pass. Stop syncing and retry later; never re-enroll. */
    data object Unavailable : StoredFamilyKey
}

/** A key that never leaves its keystore. */
interface WrappingKey {
    /** Replaces any previous key. */
    fun create(): SecretKey

    /** Null when the key is gone. */
    fun load(): SecretKey?

    fun delete()

    /** True when [e] means this key can never decrypt again, as opposed to a passing keystore failure. */
    fun isPermanent(e: Exception): Boolean
}

/**
 * Family key wrapped by a non-exportable keystore key, in a file excluded from backup. There is no plaintext
 * fallback: whenever the wrapping key cannot be used, the family key is unavailable.
 *
 * File: "FBKW" | version | familyId | iv(12) | length(4) | ciphertext+tag, everything before the ciphertext authenticated.
 */
class FamilyKeyStore(dir: File, private val wrappingKey: WrappingKey) {
    private val file = File(dir, "family_key.bin")

    @Synchronized
    fun load(): StoredFamilyKey {
        val bytes = try {
            if (!file.exists()) return StoredFamilyKey.Absent
            if (file.length() > MAX_FILE_BYTES) return StoredFamilyKey.Invalidated(StoredFamilyKey.Invalidated.Reason.CORRUPT_FILE)
            file.readBytes()
        } catch (_: IOException) {
            return StoredFamilyKey.Unavailable
        }
        val parsed = parse(bytes) ?: return StoredFamilyKey.Invalidated(StoredFamilyKey.Invalidated.Reason.CORRUPT_FILE)
        return try {
            val secret = wrappingKey.load()
                ?: return StoredFamilyKey.Invalidated(StoredFamilyKey.Invalidated.Reason.WRAPPING_KEY_MISSING)
            val plain = Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BYTES * 8, parsed.iv))
                updateAAD(parsed.aad)
                doFinal(parsed.ciphertext)
            }
            StoredFamilyKey.Ready(parsed.familyId, FamilyKey.fromBytes(plain).also { plain.fill(0) })
        } catch (e: Exception) {
            if (wrappingKey.isPermanent(e)) StoredFamilyKey.Invalidated(StoredFamilyKey.Invalidated.Reason.UNWRAP_FAILED)
            else StoredFamilyKey.Unavailable
        }
    }

    /** Refuses while any key file exists, readable or not, so an enrolled family key is never silently replaced. */
    @Synchronized
    fun enroll(familyId: String, key: FamilyKey) {
        require(isSyncId(familyId)) { "Invalid family id" }
        check(!file.exists()) { "A family key is already enrolled" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey.create()) }
        val iv = cipher.iv
        check(iv?.size == IV_BYTES) { "Unexpected IV" }
        val head = ByteWriter().raw(MAGIC).byte(VERSION).short(familyId.ascii()).raw(iv).int(CIPHERTEXT_BYTES).bytes()
        cipher.updateAAD(head)
        val plain = key.copyBytes()
        val ciphertext = try {
            cipher.doFinal(plain)
        } finally {
            plain.fill(0)
        }
        writeAtomically(file, head + ciphertext)
    }

    /** Leaving the family or recovering from [StoredFamilyKey.Invalidated]. Needs the user's explicit confirmation. */
    @Synchronized
    fun forget() {
        if (file.exists() && !file.delete()) throw IOException("Could not delete the family key")
        wrappingKey.delete()
    }

    private class Parsed(val familyId: String, val iv: ByteArray, val aad: ByteArray, val ciphertext: ByteArray)

    private fun parse(bytes: ByteArray): Parsed? = try {
        val reader = ByteReader(bytes)
        if (!reader.raw(MAGIC.size).contentEquals(MAGIC) || reader.byte() != VERSION) throw MalformedBytes()
        val familyId = reader.syncId()
        val iv = reader.raw(IV_BYTES)
        if (reader.int() != CIPHERTEXT_BYTES) throw MalformedBytes()
        val aadEnd = reader.position
        val ciphertext = reader.raw(CIPHERTEXT_BYTES)
        reader.end()
        Parsed(familyId, iv, bytes.copyOf(aadEnd), ciphertext)
    } catch (_: MalformedBytes) {
        null
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val CIPHERTEXT_BYTES = FamilyKey.SIZE_BYTES + TAG_BYTES
        private const val MAX_FILE_BYTES = 512L
        private val MAGIC = "FBKW".ascii()

        fun forApp(context: Context) = FamilyKeyStore(syncDir(context), AndroidKeystoreWrappingKey("family_sync_wrapping_key"))
    }
}
