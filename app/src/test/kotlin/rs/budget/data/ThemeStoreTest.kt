package rs.budget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStoreTest {
    @Test
    fun `defaults to system, persists a choice and ignores garbage`() {
        var saved: String? = null
        val store = ThemeStore({ saved }, { saved = it })
        assertEquals(ThemeMode.SYSTEM, store.mode.value)
        store.set(ThemeMode.DARK)
        assertEquals("DARK", saved)
        assertEquals(ThemeMode.DARK, ThemeStore({ saved }, {}).mode.value)
        assertEquals(ThemeMode.SYSTEM, ThemeStore({ "purple" }, {}).mode.value)
    }
}
