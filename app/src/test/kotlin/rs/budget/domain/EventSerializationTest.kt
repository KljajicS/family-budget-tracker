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
    fun `budget set round trips`() {
        val event: Event = BudgetSet(Budget("cat-groceries", java.time.YearMonth.of(2026, 10), Money.of("60000", Currency.RSD)))
        val json = EventJson.encodeToString(Event.serializer(), event)
        assertEquals(event, EventJson.decodeFromString(Event.serializer(), json))
    }

    @Test
    fun `recurring events round trip with a type discriminator`() {
        val rule = RecurringRule("r1", "Rent", Money.of("40000", Currency.RSD), "a1", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 5))
        listOf<Event>(RecurringRuleSet(rule), RecurringRuleArchived("r1"), BillSkipped("r1", "2026-10")).forEach { event ->
            val json = EventJson.encodeToString(Event.serializer(), event)
            assertEquals(event, EventJson.decodeFromString(Event.serializer(), json))
        }
        assertEquals("BillSkipped", EventJson.parseToJsonElement(EventJson.encodeToString(Event.serializer(), BillSkipped("r1", "2026")))
            .let { it as kotlinx.serialization.json.JsonObject }["type"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `money serializes as plain string amount`() {
        val json = EventJson.encodeToString(Money.serializer(), Money.of("12.5", Currency.EUR))
        assertEquals("""{"amount":"12.50","currency":"EUR"}""", json)
    }
}
