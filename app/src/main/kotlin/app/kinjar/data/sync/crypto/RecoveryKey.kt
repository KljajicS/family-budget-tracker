package app.kinjar.data.sync.crypto

import app.kinjar.data.sync.crypto.SyncCryptoException.Reason
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Family key as text for a password manager: RFC 4648 Base32 of version, key and a 4-byte SHA-256
 * checksum, in hyphenated groups of four. Case, spaces and hyphens are ignored on input; the checksum
 * rejects a truncated or mistyped copy before it is trusted.
 */
object RecoveryKey {
    private const val VERSION = 1
    private const val CHECKSUM_BYTES = 4
    private const val MAX_TEXT_LENGTH = 200
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DOMAIN = "family-budget-recovery-key".ascii()

    fun encode(key: FamilyKey): String {
        val body = byteArrayOf(VERSION.toByte()) + key.copyBytes()
        return base32(body + checksum(body)).chunked(4).joinToString("-")
    }

    fun decode(text: String): FamilyKey {
        if (text.length > MAX_TEXT_LENGTH) throw SyncCryptoException(Reason.MALFORMED)
        val compact = text.filterNot { it == '-' || it.isWhitespace() }.uppercase()
        val bytes = base32Decode(compact)
        if (bytes == null || bytes.isEmpty()) throw SyncCryptoException(Reason.MALFORMED)
        if (bytes[0].toInt() != VERSION) throw SyncCryptoException(Reason.UNSUPPORTED_VERSION)
        if (bytes.size != 1 + FamilyKey.SIZE_BYTES + CHECKSUM_BYTES) throw SyncCryptoException(Reason.MALFORMED)
        val body = bytes.copyOf(1 + FamilyKey.SIZE_BYTES)
        if (!MessageDigest.isEqual(checksum(body), bytes.copyOfRange(body.size, bytes.size))) {
            throw SyncCryptoException(Reason.CHECKSUM_MISMATCH)
        }
        return FamilyKey.fromBytes(body.copyOfRange(1, body.size))
    }

    /** For the "paste it back" check after the user saved the key. */
    fun matches(text: String, key: FamilyKey): Boolean = try {
        decode(text) == key
    } catch (_: SyncCryptoException) {
        false
    }

    private fun checksum(body: ByteArray) =
        MessageDigest.getInstance("SHA-256").apply { update(DOMAIN); update(body) }.digest().copyOf(CHECKSUM_BYTES)

    private fun base32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = ((buffer shl 8) or (b.toInt() and 0xFF)) and 0xFFFF
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    private fun base32Decode(text: String): ByteArray? {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = ((buffer shl 5) or value) and 0xFFFF
            bits += 5
            if (bits >= 8) {
                out.write(buffer ushr (bits - 8))
                bits -= 8
            }
        }
        // Leftover bits are the zero padding encode adds; anything else means a character was added or lost.
        if (bits >= 5 || (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }
}
