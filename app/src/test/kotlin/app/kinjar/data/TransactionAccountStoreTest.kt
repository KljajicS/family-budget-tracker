package app.kinjar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import app.kinjar.domain.TransactionType

class TransactionAccountStoreTest {
    @Test fun `defaults are empty and successful choices persist independently across restarts`() {
        val preferences = mutableMapOf<TransactionType, String>()
        val store = TransactionAccountStore(preferences::get) { type, id -> preferences[type] = id }
        assertEquals(null, store.lastUsed(TransactionType.EXPENSE))
        assertEquals(null, store.lastUsed(TransactionType.INCOME))
        store.remember(TransactionType.EXPENSE, "cash")
        store.remember(TransactionType.INCOME, "card")
        val restarted = TransactionAccountStore(preferences::get) { _, _ -> }
        assertEquals("cash", restarted.lastUsed(TransactionType.EXPENSE))
        assertEquals("card", restarted.lastUsed(TransactionType.INCOME))
    }

    @Test fun `transfer neither reads nor writes account preferences`() {
        val store = TransactionAccountStore({ error("Unexpected read") }, { _, _ -> error("Unexpected write") })
        assertEquals(null, store.lastUsed(TransactionType.TRANSFER))
        store.remember(TransactionType.TRANSFER, "cash")
    }

    @Test fun `failed preference write retains previous successful account`() {
        val saved = "cash"
        val store = TransactionAccountStore({ saved }, { _, _ -> error("Disk full") })
        val failure = runCatching { store.remember(TransactionType.EXPENSE, "card") }
        assertEquals(true, failure.isFailure)
        assertEquals("cash", store.lastUsed(TransactionType.EXPENSE))
    }
}
