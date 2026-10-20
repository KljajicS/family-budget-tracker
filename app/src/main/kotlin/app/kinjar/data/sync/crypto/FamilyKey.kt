package app.kinjar.data.sync.crypto

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 256-bit family secret. Long-lived: every phone, QR photo or recovery copy that ever held it keeps working,
 * and nothing here can revoke one.
 */
class FamilyKey private constructor(private val bytes: ByteArray) {
    internal fun copyBytes(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?) = other is FamilyKey && MessageDigest.isEqual(bytes, other.bytes)

    override fun hashCode() = 0

    override fun toString() = "FamilyKey(redacted)"

    companion object {
        const val SIZE_BYTES = 32

        fun generate(random: SecureRandom = SecureRandom()) = FamilyKey(ByteArray(SIZE_BYTES).also(random::nextBytes))

        internal fun fromBytes(bytes: ByteArray): FamilyKey {
            require(bytes.size == SIZE_BYTES) { "Family key must be $SIZE_BYTES bytes" }
            return FamilyKey(bytes.copyOf())
        }
    }
}

/** Messages carry only [reason], never key material or input bytes. */
class SyncCryptoException(val reason: Reason) : GeneralSecurityException(reason.name) {
    enum class Reason {
        MALFORMED,
        UNSUPPORTED_VERSION,
        TOO_LARGE,
        HEADER_MISMATCH,
        AUTHENTICATION_FAILED,
        CHECKSUM_MISMATCH,
        NOT_A_PAIRING_CODE,
    }
}
