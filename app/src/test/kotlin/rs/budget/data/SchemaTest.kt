package rs.budget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaTest {
    @Test
    fun `fresh database has no events and seq starts at zero`() {
        val db = testDb()
        assertEquals(0L, db.eventQueries.count().executeAsOne())
        assertEquals(0L, db.eventQueries.lastSeq("dev-1").executeAsOne().max_seq ?: 0L)
    }
}
