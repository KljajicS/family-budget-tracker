package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventSerializationTest {
    @Test
    fun `transaction added round trips through json`() {
        val event: Event = TransactionAdded(
            transaction = Transaction(
                id = "t1",
                type = TransactionType.EXPENSE,
                money = Money.of("4860", Currency.RSD),
                accountId = "a1",
                toAccountId = null,
                categoryId = "c1",
                date = LocalDate.of(2026, 10, 14),
                note = "",
                merchant = "Maxi",
                paidBy = "m1",
                source = TransactionSource.MANUAL,
            )
        )
        val json = EventJson.encodeToString(Event.serializer(), event)
        assertEquals(event, EventJson.decodeFromString(Event.serializer(), json))
    }

    @Test
    fun `money serializes as plain string amount`() {
        val json = EventJson.encodeToString(Money.serializer(), Money.of("12.5", Currency.EUR))
        assertEquals("""{"amount":"12.50","currency":"EUR"}""", json)
    }
}
