package app.kinjar.data.sync.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class InstallIdentityTest {
    @get:Rule val folder = TemporaryFolder()

    private class SoftwareIdentityKey : IdentityKey {
        var pair: KeyPair? = null
        var failure: Exception? = null

        override fun create(): ByteArray = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }.also { pair = it }.public.encoded

        override fun sign(data: ByteArray): ByteArray? {
            failure?.let { throw it }
            val privateKey = pair?.private ?: return null
            return Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(data)
                sign()
            }
        }

        override fun delete() {
            pair = null
        }

        override fun isPermanent(e: Exception) = false
    }

    private val key = SoftwareIdentityKey()
    private val dir get() = File(folder.root, "sync")

    private fun identity(key: IdentityKey = this.key) = InstallIdentity(dir, key)

    private val copied = InstallState.Untrusted(InstallState.Untrusted.Reason.COPIED)

    @Test
    fun `created identity survives a restart and cannot be recreated`() {
        assertEquals(InstallState.Absent, identity().load())
        val ready = identity().create()
        assertEquals(ready, identity().load())
        assertThrows(IllegalStateException::class.java) { identity().create() }
    }

    @Test
    fun `files copied to an install without the keystore key are untrusted`() {
        identity().create()
        assertEquals(copied, identity(SoftwareIdentityKey()).load())
        assertEquals(copied, identity(SoftwareIdentityKey().also { it.create() }).load())
    }

    @Test
    fun `untrusted identity is replaced only by an explicit create with a new id`() {
        val original = identity().create()
        val otherPhone = SoftwareIdentityKey()
        assertEquals(copied, identity(otherPhone).load())
        val fresh = identity(otherPhone).create()
        assertNotEquals(original.installId, fresh.installId)
        assertEquals(fresh, identity(otherPhone).load())
        assertEquals(copied, identity().load())
    }

    @Test
    fun `damaged file is corrupt`() {
        identity().create()
        val file = File(dir, "install_identity.bin")
        val original = file.readBytes()
        for (bytes in listOf(original.copyOf(original.size - 1), original + 0, ByteArray(0), ByteArray(2048))) {
            file.writeBytes(bytes)
            assertEquals(InstallState.Untrusted(InstallState.Untrusted.Reason.CORRUPT_FILE), identity().load())
        }
    }

    @Test
    fun `passing keystore failure is unavailable and blocks replacement`() {
        val ready = identity().create()
        key.failure = ProviderException("keystore busy")
        assertEquals(InstallState.Unavailable, identity().load())
        assertThrows(IllegalStateException::class.java) { identity().create() }
        key.failure = null
        assertEquals(ready, identity().load())
    }

    @Test
    fun `selected member is local and separate from the identity`() {
        val members = SelectedMemberStore(dir)
        assertNull(members.get())
        members.set("member-1")
        assertEquals("member-1", SelectedMemberStore(dir).get())
        identity().create()
        assertEquals("member-1", SelectedMemberStore(dir).get())
        assertThrows(IllegalArgumentException::class.java) { members.set("not a member") }
        members.clear()
        assertNull(SelectedMemberStore(dir).get())
    }
}
