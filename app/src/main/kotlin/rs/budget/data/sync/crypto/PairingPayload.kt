package rs.budget.data.sync.crypto

import rs.budget.data.sync.crypto.SyncCryptoException.Reason
import java.util.Base64

/**
 * Family invitation shown as a QR code in person. It carries the family key itself: hiding the QR later
 * cannot revoke a copy someone already scanned or photographed.
 */
class PairingPayload(val familyId: String, val folderId: String, val key: FamilyKey) {
    init {
        require(isSyncId(familyId)) { "Invalid family id" }
        require(FOLDER_ID.matches(folderId)) { "Invalid folder id" }
    }

    fun encode(): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteWriter()
            .byte(VERSION)
            .short(familyId.ascii())
            .short(folderId.ascii())
            .raw(key.copyBytes())
            .bytes(),
    )

    override fun toString() = "PairingPayload(familyId=$familyId, folderId=$folderId, key=redacted)"

    companion object {
        private const val PREFIX = "fbsync:"
        private const val VERSION = 1
        private const val MAX_TEXT_LENGTH = 512
        private val FOLDER_ID = Regex("[A-Za-z0-9_-]{1,128}")

        /** [Reason.NOT_A_PAIRING_CODE] lets the scanner ignore unrelated QR codes such as fiscal receipts. */
        fun decode(text: String): PairingPayload {
            if (!text.startsWith(PREFIX)) throw SyncCryptoException(Reason.NOT_A_PAIRING_CODE)
            if (text.length > MAX_TEXT_LENGTH) throw SyncCryptoException(Reason.MALFORMED)
            val bytes = try {
                Base64.getUrlDecoder().decode(text.substring(PREFIX.length))
            } catch (_: IllegalArgumentException) {
                throw SyncCryptoException(Reason.MALFORMED)
            }
            return try {
                val reader = ByteReader(bytes)
                if (reader.byte() != VERSION) throw SyncCryptoException(Reason.UNSUPPORTED_VERSION)
                val familyId = reader.syncId()
                val folderId = String(reader.short(), Charsets.US_ASCII).takeIf(FOLDER_ID::matches) ?: throw MalformedBytes()
                val key = FamilyKey.fromBytes(reader.raw(FamilyKey.SIZE_BYTES))
                reader.end()
                PairingPayload(familyId, folderId, key)
            } catch (_: MalformedBytes) {
                throw SyncCryptoException(Reason.MALFORMED)
            }
        }
    }
}
