package app.kinjar.data.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import app.kinjar.data.sync.crypto.SyncCryptoException.Reason

class RecoveryKeyTest {
    private val key = FamilyKey.fromBytes(ByteArray(32) { it.toByte() })
    private val text = RecoveryKey.encode(key)

    private fun reason(input: String) = assertThrows(SyncCryptoException::class.java) { RecoveryKey.decode(input) }.reason

    @Test
    fun `round trips as fifteen groups of four Base32 characters`() {
        assertTrue(text, Regex("[A-Z2-7]{4}(-[A-Z2-7]{4}){14}").matches(text))
        assertEquals(key, RecoveryKey.decode(text))
        val random = FamilyKey.generate()
        assertEquals(random, RecoveryKey.decode(RecoveryKey.encode(random)))
    }

    @Test
    fun `pasted copy may differ in case, spaces and hyphens`() {
        assertTrue(RecoveryKey.matches(" " + text.lowercase().replace("-", " ") + "\n", key))
        assertTrue(RecoveryKey.matches(text.replace("-", ""), key))
        assertFalse(RecoveryKey.matches(text, FamilyKey.generate()))
    }

    @Test
    fun `every single character typo is rejected`() {
        val compact = text.replace("-", "")
        for (i in compact.indices) {
            val typo = compact.substring(0, i) + (if (compact[i] == 'A') 'B' else 'A') + compact.substring(i + 1)
            assertThrows("position $i", SyncCryptoException::class.java) { RecoveryKey.decode(typo) }
        }
    }

    @Test
    fun `truncated, extended or foreign text is malformed`() {
        assertEquals(Reason.MALFORMED, reason(text.dropLast(5)))
        assertEquals(Reason.MALFORMED, reason(text.dropLast(1)))
        assertEquals(Reason.MALFORMED, reason(text + "AAAA"))
        assertEquals(Reason.MALFORMED, reason(text.replaceFirst('-', '0')))
        assertEquals(Reason.MALFORMED, reason(""))
        assertEquals(Reason.MALFORMED, reason("A".repeat(500)))
    }

    @Test
    fun `swapped groups fail the checksum`() {
        val groups = text.split("-").toMutableList()
        val swapped = groups.also { it.add(3, it.removeAt(2)) }.joinToString("-")
        assertEquals(Reason.CHECKSUM_MISMATCH, reason(swapped))
    }
}
