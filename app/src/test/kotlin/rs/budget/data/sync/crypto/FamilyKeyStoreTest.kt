package rs.budget.data.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class FamilyKeyStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private class SoftwareWrappingKey : WrappingKey {
        var key: SecretKey? = null
        var failure: Exception? = null

        override fun create(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun load(): SecretKey? = failure?.let { throw it } ?: key
        override fun delete() {
            key = null
        }
        override fun isPermanent(e: Exception) = e is AEADBadTagException
    }

    private val wrapping = SoftwareWrappingKey()
    private val dir get() = File(folder.root, "sync")
    private val file get() = File(dir, "family_key.bin")
    private val key = FamilyKey.generate()

    private fun store() = FamilyKeyStore(dir, wrapping)

    private fun invalidated(reason: StoredFamilyKey.Invalidated.Reason) = StoredFamilyKey.Invalidated(reason)

    @Test
    fun `enrolled key survives a restart and is never stored in the clear`() {
        assertEquals(StoredFamilyKey.Absent, store().load())
        store().enroll("family-1", key)
        assertEquals(StoredFamilyKey.Ready("family-1", key), store().load())
        val bytes = file.readBytes()
        assertFalse(bytes.toList().windowed(32).any { it.toByteArray().contentEquals(key.copyBytes()) })
        assertEquals(listOf("family_key.bin"), dir.list()!!.toList())
    }

    @Test
    fun `an enrolled key is never replaced, even when unreadable`() {
        store().enroll("family-1", key)
        val before = file.readBytes()
        assertThrows(IllegalStateException::class.java) { store().enroll("family-2", FamilyKey.generate()) }
        wrapping.key = null
        assertThrows(IllegalStateException::class.java) { store().enroll("family-1", FamilyKey.generate()) }
        assertEquals(before.toList(), file.readBytes().toList())
    }

    @Test
    fun `missing wrapping key is invalidated, not absent`() {
        store().enroll("family-1", key)
        wrapping.key = null
        assertEquals(invalidated(StoredFamilyKey.Invalidated.Reason.WRAPPING_KEY_MISSING), store().load())
    }

    @Test
    fun `different wrapping key or tampered family id fails to unwrap`() {
        store().enroll("family-1", key)
        val original = file.readBytes()
        file.writeBytes(original.copyOf().also { it[6 + "family-".length] = '2'.code.toByte() })
        assertEquals(invalidated(StoredFamilyKey.Invalidated.Reason.UNWRAP_FAILED), store().load())
        file.writeBytes(original)
        wrapping.create()
        assertEquals(invalidated(StoredFamilyKey.Invalidated.Reason.UNWRAP_FAILED), store().load())
    }

    @Test
    fun `truncated, extended or oversized file is corrupt`() {
        store().enroll("family-1", key)
        val original = file.readBytes()
        for (bytes in listOf(original.copyOf(original.size - 1), original + 0, ByteArray(0), ByteArray(4096))) {
            file.writeBytes(bytes)
            assertEquals(invalidated(StoredFamilyKey.Invalidated.Reason.CORRUPT_FILE), store().load())
        }
    }

    @Test
    fun `passing keystore failure is unavailable and blocks enrolment`() {
        store().enroll("family-1", key)
        wrapping.failure = ProviderException("keystore busy")
        assertEquals(StoredFamilyKey.Unavailable, store().load())
        assertThrows(IllegalStateException::class.java) { store().enroll("family-1", FamilyKey.generate()) }
        wrapping.failure = null
        assertEquals(StoredFamilyKey.Ready("family-1", key), store().load())
    }

    @Test
    fun `only an explicit forget allows a new enrolment`() {
        store().enroll("family-1", key)
        wrapping.key = null
        store().forget()
        assertEquals(StoredFamilyKey.Absent, store().load())
        val next = FamilyKey.generate()
        store().enroll("family-2", next)
        assertEquals(StoredFamilyKey.Ready("family-2", next), store().load())
    }

    @Test
    fun `ready state does not print the key`() {
        assertFalse(StoredFamilyKey.Ready("family-1", key).toString().contains(RecoveryKey.encode(key)))
        assertEquals("Ready(familyId=family-1, key=FamilyKey(redacted))", StoredFamilyKey.Ready("family-1", key).toString())
    }
}
