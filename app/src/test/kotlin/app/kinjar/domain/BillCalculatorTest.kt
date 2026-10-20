package app.kinjar.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillCalculatorTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val accounts = setOf("cash")

    private fun rule(id: String, amount: String, firstDue: LocalDate, cadence: Cadence = Cadence.MONTHLY, category: String = "cat-bills", account: String = "cash", currency: Currency = Currency.RSD) =
        RecurringRule(id, id, Money.of(amount, currency), account, category, cadence, firstDue)

    private fun paid(bill: Bill) = Transaction(
        bill.txId, TransactionType.EXPENSE, bill.rule.money, bill.rule.accountId, null, bill.rule.categoryId, bill.dueDate, "", bill.rule.name, "m1", TransactionSource.RECURRING,
    )

    @Test
    fun `monthly occurrences keep the 31st where the month has it`() {
        val r = rule("r", "100", LocalDate.of(2026, 1, 31))
        val due = BillCalculator.occurrences(r, LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), due)
    }

    @Test
    fun `yearly rule fires once a year, nothing before its first due date`() {
        val r = rule("r", "100", LocalDate.of(2026, 10, 20), Cadence.YEARLY)
        assertEquals(emptyList<LocalDate>(), BillCalculator.occurrences(r, LocalDate.of(2026, 10, 19)))
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 20)), BillCalculator.occurrences(r, LocalDate.of(2027, 12, 31)))
        assertEquals("2026", BillCalculator.period(Cadence.YEARLY, LocalDate.of(2026, 10, 20)))
        assertEquals("2026-10", BillCalculator.period(Cadence.MONTHLY, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `upcoming lists this month and overdue earlier months, not next month`() {
        val r = rule("r", "100", LocalDate.of(2026, 8, 20))
        val bills = BillCalculator.upcoming(today, listOf(r), accounts, emptyList(), emptySet())
        assertEquals(listOf(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 20)), bills.map { it.dueDate })
        assertEquals(listOf(true, true, false), bills.map { it.overdue })
    }

    @Test
    fun `paid and skipped occurrences drop out, one period at a time`() {
        val r = rule("r", "100", LocalDate.of(2026, 9, 20))
        val all = BillCalculator.upcoming(today, listOf(r), accounts, emptyList(), emptySet())
        val afterPay = BillCalculator.upcoming(today, listOf(r), accounts, listOf(paid(all[0])), emptySet())
        assertEquals(listOf("2026-10"), afterPay.map { it.period })
        val afterSkip = BillCalculator.upcoming(today, listOf(r), accounts, emptyList(), setOf("r" to "2026-10"))
        assertEquals(listOf("2026-09"), afterSkip.map { it.period })
    }

    @Test
    fun `moving the due day keeps a paid month paid`() {
        val r = rule("r", "100", LocalDate.of(2026, 10, 5))
        val bill = BillCalculator.upcoming(today, listOf(r), accounts, emptyList(), emptySet()).single()
        val moved = r.copy(firstDue = LocalDate.of(2026, 10, 25))
        assertEquals(emptyList<Bill>(), BillCalculator.upcoming(today, listOf(moved), accounts, listOf(paid(bill)), emptySet()))
    }

    @Test
    fun `archived rules and archived accounts hide their bills`() {
        val archivedRule = rule("a", "100", LocalDate.of(2026, 10, 5)).copy(archived = true)
        val goneAccount = rule("b", "100", LocalDate.of(2026, 10, 5), account = "old")
        assertEquals(emptyList<Bill>(), BillCalculator.upcoming(today, listOf(archivedRule, goneAccount), accounts, emptyList(), emptySet()))
    }
}
