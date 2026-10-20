package app.kinjar.data.sync.crypto

import app.kinjar.data.sync.crypto.SyncCryptoException.Reason
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class SyncPurpose(internal val code: Int) { HISTORY(1), DAILY_SNAPSHOT(2) }

data class SyncHeader(
    val familyId: String,
    val publisherDeviceId: String,
    val purpose: SyncPurpose,
    val generation: Long,
) {
    init {
        require(isSyncId(familyId)) { "Invalid family id" }
        require(isSyncId(publisherDeviceId)) { "Invalid publisher id" }
        require(generation > 0) { "Generation must be positive" }
    }
}

class OpenedSyncFile(val header: SyncHeader, val plaintext: ByteArray)

/**
 * AES-256-GCM over one whole file with a fresh random 96-bit nonce. Every byte before the ciphertext,
 * nonce and length included, is associated data, so a file cannot be passed off under another family,
 * publisher, purpose or generation without failing the tag.
 *
 * Layout: "FBSY" | version | purpose | generation(8) | familyId | publisherId | nonce(12) | length(4) | ciphertext+tag.
 */
class SyncCipher(
    key: FamilyKey,
    private val maxPlaintextBytes: Int = DEFAULT_MAX_PLAINTEXT_BYTES,
    private val random: SecureRandom = SecureRandom(),
) {
    private val secretKey = key.copyBytes().let { bytes -> SecretKeySpec(bytes, "AES").also { bytes.fill(0) } }

    init {
        require(maxPlaintextBytes in 0..MAX_PLAINTEXT_LIMIT)
    }

    fun encrypt(header: SyncHeader, plaintext: ByteArray): ByteArray {
        if (plaintext.size > maxPlaintextBytes) throw SyncCryptoException(Reason.TOO_LARGE)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val aad = headerBytes(header, nonce, plaintext.size + TAG_BYTES)
        val out = aad.copyOf(aad.size + plaintext.size + TAG_BYTES)
        cipher(Cipher.ENCRYPT_MODE, nonce, aad).doFinal(plaintext, 0, plaintext.size, out, aad.size)
        return out
    }

    /** Fails unless the file is intact, under this key, and for the given family, purpose and publisher. */
    fun decrypt(blob: ByteArray, familyId: String, purpose: SyncPurpose, publisherDeviceId: String? = null): OpenedSyncFile {
        val parsed = parse(blob, maxPlaintextBytes)
        val header = parsed.header
        val publisherMatches = publisherDeviceId == null || header.publisherDeviceId == publisherDeviceId
        if (header.familyId != familyId || header.purpose != purpose || !publisherMatches) {
            throw SyncCryptoException(Reason.HEADER_MISMATCH)
        }
        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, parsed.nonce, blob.copyOf(parsed.ciphertextOffset))
                .doFinal(blob, parsed.ciphertextOffset, blob.size - parsed.ciphertextOffset)
        } catch (_: AEADBadTagException) {
            throw SyncCryptoException(Reason.AUTHENTICATION_FAILED)
        }
        return OpenedSyncFile(header, plaintext)
    }

    private fun cipher(mode: Int, nonce: ByteArray, aad: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, secretKey, GCMParameterSpec(TAG_BYTES * 8, nonce))
        updateAAD(aad)
    }

    private class Parsed(val header: SyncHeader, val nonce: ByteArray, val ciphertextOffset: Int)

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
        private const val MAX_PLAINTEXT_LIMIT = 256 * 1024 * 1024
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private val MAGIC = "FBSY".ascii()

        /** Unauthenticated. Only for choosing which family to try, never for trusting the content. */
        fun readHeader(blob: ByteArray): SyncHeader = parse(blob, MAX_PLAINTEXT_LIMIT).header

        private fun headerBytes(header: SyncHeader, nonce: ByteArray, ciphertextBytes: Int) = ByteWriter()
            .raw(MAGIC)
            .byte(FORMAT_VERSION)
            .byte(header.purpose.code)
            .long(header.generation)
            .short(header.familyId.ascii())
            .short(header.publisherDeviceId.ascii())
            .raw(nonce)
            .int(ciphertextBytes)
            .bytes()

        private fun parse(blob: ByteArray, maxPlaintextBytes: Int): Parsed = try {
            val reader = ByteReader(blob)
            if (!reader.raw(MAGIC.size).contentEquals(MAGIC)) throw MalformedBytes()
            if (reader.byte() != FORMAT_VERSION) throw SyncCryptoException(Reason.UNSUPPORTED_VERSION)
            val purposeCode = reader.byte()
            val purpose = SyncPurpose.entries.firstOrNull { it.code == purposeCode } ?: throw MalformedBytes()
            val generation = reader.long()
            if (generation <= 0) throw MalformedBytes()
            val familyId = reader.syncId()
            val publisher = reader.syncId()
            val nonce = reader.raw(NONCE_BYTES)
            val length = reader.int()
            if (length < TAG_BYTES || length != reader.remaining) throw MalformedBytes()
            if (length - TAG_BYTES > maxPlaintextBytes) throw SyncCryptoException(Reason.TOO_LARGE)
            Parsed(SyncHeader(familyId, publisher, purpose, generation), nonce, reader.position)
        } catch (_: MalformedBytes) {
            throw SyncCryptoException(Reason.MALFORMED)
        }
    }
}
