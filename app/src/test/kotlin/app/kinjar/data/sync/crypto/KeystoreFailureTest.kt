package app.kinjar.data.sync.crypto

import org.junit.Assert.assertFalse
import org.junit.Test
import app.kinjar.data.sync.identity.AndroidKeystoreIdentityKey
import java.security.UnrecoverableKeyException

class KeystoreFailureTest {
    @Test
    fun `unrecoverable key lookup does not authorize replacing keys`() {
        val failure = UnrecoverableKeyException("Temporary keystore lookup failure")
        assertFalse(AndroidKeystoreWrappingKey("test").isPermanent(failure))
        assertFalse(AndroidKeystoreIdentityKey("test").isPermanent(failure))
    }
}
