package app.kinjar.data.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import app.kinjar.data.sync.crypto.SyncCryptoException.Reason
import java.util.Base64

class PairingPayloadTest {
    private val key = FamilyKey.generate()
    private val payload = PairingPayload("family-1", "1AbC_folder-Id", key)

    private fun reason(text: String) = assertThrows(SyncCryptoException::class.java) { PairingPayload.decode(text) }.reason

    private fun encoded(bytes: ByteArray) = "fbsync:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun raw(version: Int = 1, keyBytes: ByteArray = key.copyBytes()) =
        ByteWriter().byte(version).short("family-1".ascii()).short("folder".ascii()).raw(keyBytes).bytes()

    @Test
    fun `round trips family, folder and key`() {
        val decoded = PairingPayload.decode(payload.encode())
        assertEquals("family-1", decoded.familyId)
        assertEquals("1AbC_folder-Id", decoded.folderId)
        assertEquals(key, decoded.key)
    }

    @Test
    fun `secret never appears in toString or errors`() {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(key.copyBytes()).take(20)
        assertFalse(payload.toString().contains(secret))
        assertFalse(payload.toString().contains(payload.encode().removePrefix("fbsync:")))
        assertEquals("FamilyKey(redacted)", key.toString())
        val error = assertThrows(SyncCryptoException::class.java) { PairingPayload.decode(payload.encode() + "A") }
        assertEquals("MALFORMED", error.message)
    }

    @Test
    fun `unrelated QR codes are recognised as such`() {
        assertEquals(Reason.NOT_A_PAIRING_CODE, reason("https://suf.purs.gov.rs/v/?vl=abc"))
        assertEquals(Reason.NOT_A_PAIRING_CODE, reason(""))
    }

    @Test
    fun `wrong key length, trailing bytes, bad Base64 and future versions are refused`() {
        assertEquals(PairingPayload.decode(encoded(raw())).key, key)
        assertEquals(Reason.MALFORMED, reason(encoded(raw(keyBytes = key.copyBytes().copyOf(31)))))
        assertEquals(Reason.MALFORMED, reason(encoded(raw() + 0)))
        assertEquals(Reason.MALFORMED, reason("fbsync:not*base64"))
        assertEquals(Reason.MALFORMED, reason("fbsync:"))
        assertEquals(Reason.MALFORMED, reason("fbsync:" + "A".repeat(600)))
        assertEquals(Reason.UNSUPPORTED_VERSION, reason(encoded(raw(version = 2))))
        val badFolder = ByteWriter().byte(1).short("family-1".ascii()).short("fol der".ascii()).raw(key.copyBytes()).bytes()
        assertEquals(Reason.MALFORMED, reason(encoded(badFolder)))
    }

    @Test
    fun `ids are validated when building a payload`() {
        assertThrows(IllegalArgumentException::class.java) { PairingPayload("", "folder", key) }
        assertThrows(IllegalArgumentException::class.java) { PairingPayload("family-1", "f".repeat(129), key) }
        assertThrows(IllegalArgumentException::class.java) { FamilyKey.fromBytes(ByteArray(16)) }
    }
}
