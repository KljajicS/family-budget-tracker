package app.kinjar.data.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import app.kinjar.data.sync.crypto.SyncCryptoException.Reason

class SyncCipherTest {
    private val key = FamilyKey.generate()
    private val cipher = SyncCipher(key)
    private val header = SyncHeader("family-1", "phone-a", SyncPurpose.HISTORY, generation = 7)
    private val history = "full history".toByteArray()

    private fun reason(block: () -> Unit) = assertThrows(SyncCryptoException::class.java, block).reason

    private fun open(blob: ByteArray, publisher: String? = null) = cipher.decrypt(blob, "family-1", SyncPurpose.HISTORY, publisher)

    private fun ByteArray.flipped(index: Int) = copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }

    @Test
    fun `round trips with the authenticated header`() {
        val opened = open(cipher.encrypt(header, history), "phone-a")
        assertEquals(header, opened.header)
        assertArrayEquals(history, opened.plaintext)
        assertArrayEquals(ByteArray(0), open(cipher.encrypt(header, ByteArray(0))).plaintext)
        assertEquals(header, SyncCipher.readHeader(cipher.encrypt(header, history)))
    }

    @Test
    fun `every encryption uses a fresh nonce`() {
        assertFalse(cipher.encrypt(header, history).contentEquals(cipher.encrypt(header, history)))
    }

    @Test
    fun `wrong key fails authentication`() {
        val blob = SyncCipher(FamilyKey.generate()).encrypt(header, history)
        assertEquals(Reason.AUTHENTICATION_FAILED, reason { open(blob) })
    }

    @Test
    fun `any flipped bit in nonce, generation, ciphertext or tag fails authentication`() {
        val blob = cipher.encrypt(header, history)
        val generationByte = 13
        val nonceByte = blob.size - history.size - 16 - 4 - 1
        for (index in listOf(generationByte, nonceByte, blob.size - 17, blob.size - 1)) {
            assertEquals("byte $index", Reason.AUTHENTICATION_FAILED, reason { open(blob.flipped(index)) })
        }
    }

    @Test
    fun `header copied onto another publisher fails authentication`() {
        val blob = cipher.encrypt(header, history)
        val publisherStart = 14 + 1 + "family-1".length + 1
        val forged = blob.copyOf().also { it[publisherStart + "phone-".length] = 'b'.code.toByte() }
        assertEquals(Reason.AUTHENTICATION_FAILED, reason { open(forged, "phone-b") })
        assertEquals(Reason.HEADER_MISMATCH, reason { open(forged, "phone-a") })
    }

    @Test
    fun `unexpected family, purpose or publisher is rejected before decrypting`() {
        val blob = cipher.encrypt(header, history)
        assertEquals(Reason.HEADER_MISMATCH, reason { cipher.decrypt(blob, "family-2", SyncPurpose.HISTORY) })
        assertEquals(Reason.HEADER_MISMATCH, reason { cipher.decrypt(blob, "family-1", SyncPurpose.DAILY_SNAPSHOT) })
        assertEquals(Reason.HEADER_MISMATCH, reason { open(blob, "phone-b") })
    }

    @Test
    fun `every truncation and any extension is malformed`() {
        val blob = cipher.encrypt(header, history)
        for (size in 0 until blob.size) assertEquals("size $size", Reason.MALFORMED, reason { open(blob.copyOf(size)) })
        assertEquals(Reason.MALFORMED, reason { open(blob + 0) })
    }

    @Test
    fun `unknown version, magic or purpose is refused`() {
        val blob = cipher.encrypt(header, history)
        assertEquals(Reason.UNSUPPORTED_VERSION, reason { open(blob.copyOf().also { it[4] = 2 }) })
        assertEquals(Reason.MALFORMED, reason { open(blob.flipped(0)) })
        assertEquals(Reason.MALFORMED, reason { open(blob.copyOf().also { it[5] = 9 }) })
    }

    @Test
    fun `size is bounded on both sides`() {
        val small = SyncCipher(key, maxPlaintextBytes = 8)
        assertEquals(Reason.TOO_LARGE, reason { small.encrypt(header, ByteArray(9)) })
        assertEquals(Reason.TOO_LARGE, reason { small.decrypt(cipher.encrypt(header, ByteArray(9)), "family-1", SyncPurpose.HISTORY) })
        assertArrayEquals(ByteArray(8), small.decrypt(small.encrypt(header, ByteArray(8)), "family-1", SyncPurpose.HISTORY).plaintext)
    }

    @Test
    fun `zero generation cannot be created or read`() {
        assertThrows(IllegalArgumentException::class.java) { header.copy(generation = 0) }
        val blob = cipher.encrypt(header, history).also { it.fill(0, 6, 14) }
        assertEquals(Reason.MALFORMED, reason { open(blob) })
    }

    @Test
    fun `header fields are validated`() {
        assertThrows(IllegalArgumentException::class.java) { SyncHeader("family 1", "phone-a", SyncPurpose.HISTORY, 0) }
        assertThrows(IllegalArgumentException::class.java) { SyncHeader("family-1", "x".repeat(65), SyncPurpose.HISTORY, 0) }
        assertThrows(IllegalArgumentException::class.java) { SyncHeader("family-1", "phone-a", SyncPurpose.HISTORY, -1) }
    }
}
