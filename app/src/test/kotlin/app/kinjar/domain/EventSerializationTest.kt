package app.kinjar.domain

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
    fun `legacy account kind and goal contribution still decode, new goals default to zero contribution`() {
        val account = """{"type":"AccountCreated","account":{"id":"a","name":"Savings","currency":"EUR","kind":"SAVINGS","archived":false}}"""
        assertEquals(AccountKind.SAVINGS, (EventJson.decodeFromString(Event.serializer(), account) as AccountCreated).account.kind)
        val goal = """{"type":"GoalSet","goal":{"id":"g","name":"Trip","target":{"amount":"3000.00","currency":"EUR"},"monthly":{"amount":"150.00","currency":"EUR"}}}"""
        assertEquals(Money.of("150", Currency.EUR), (EventJson.decodeFromString(Event.serializer(), goal) as GoalSet).goal.monthly)
        assertEquals(Money.zero(Currency.EUR), Goal("g", "Trip", Money.of("3000", Currency.EUR)).monthly)
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
    fun `goal events round trip`() {
        val goal = Goal("g1", "Trip", Money.of("3000", Currency.EUR), Money.of("150", Currency.EUR))
        val allocation = GoalAllocation("a1", "g1", Money.of("200", Currency.EUR), LocalDate.of(2026, 10, 14))
        listOf<Event>(
            GoalSet(goal), GoalArchived("g1"), GoalAllocated(allocation), GoalAllocationDeleted("a1"),
            GoalsReordered(listOf("g2", "g1")), AccountsReordered(listOf("a2", "a1")),
        ).forEach { event ->
            val json = EventJson.encodeToString(Event.serializer(), event)
            assertEquals(event, EventJson.decodeFromString(Event.serializer(), json))
        }
    }

    @Test
    fun `money serializes as plain string amount`() {
        val json = EventJson.encodeToString(Money.serializer(), Money.of("12.5", Currency.EUR))
        assertEquals("""{"amount":"12.50","currency":"EUR"}""", json)
    }
}
