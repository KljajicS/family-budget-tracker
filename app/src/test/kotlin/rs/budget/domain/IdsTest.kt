package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun `ids are uuid v7 and sort by time`() {
        val first = newId()
        Thread.sleep(2)
        val second = newId()
        assertEquals(36, first.length)
        assertEquals('7', first[14])
        assertTrue(first < second)
    }

    @Test
    fun `bill transaction id is stable per rule and period`() {
        assertEquals(billTxId("r1", "2026-10"), billTxId("r1", "2026-10"))
        assertTrue(billTxId("r1", "2026-10") != billTxId("r1", "2026-11"))
        assertEquals(36, billTxId("r1", "2026").length)
    }
}
