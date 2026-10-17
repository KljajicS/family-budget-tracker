package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import rs.budget.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LoanRepositoryTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val store = EventStore(db, "d", clock, Projector(db))
    private val repo = BudgetRepository(db, store, clock)
    private val loans = repo.loans
    private fun eur(v: String) = Money.of(v, Currency.EUR)
    private val loan = Loan("l", "Car", eur("6800"), LocalDate.of(2026, 10, 17))
    private fun tx(id: String, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(id, type, eur("250"), "a", null, null, clock.today(), "", "", "m", TransactionSource.MANUAL)
    private fun payment(principal: String? = "200") = LoanPayment("p", "l", "t", eur("250"), principal?.let(::eur))
    private suspend fun setup() { repo.addAccount(Account("a", "Bank", Currency.EUR)); loans.create(loan) }

    @Test fun `existing loans do not create money and payment retries debit once`() = runTest {
        setup()
        assertTrue(repo.transactions.first().isEmpty())
        loans.record(payment(), tx("t"))
        loans.record(payment(), tx("t"))
        assertEquals(1, repo.transactions.first().size)
        assertEquals(eur("-250"), repo.home.first().balances["a"])
        assertEquals(eur("6600"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
        store.rebuild()
        assertEquals(eur("6600"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
    }

    @Test fun `new borrowing increases cash but is excluded from income`() = runTest {
        repo.addAccount(Account("a", "Bank", Currency.EUR))
        val credit = tx("credit", TransactionType.LOAN_RECEIPT)
        loans.create(loan, credit)
        loans.create(loan, credit)
        assertEquals(eur("250"), repo.home.first().balances["a"])
        assertTrue(repo.home.first().income.isZero)
        assertTrue(InsightsCalculator.summarize(clock.today(), java.time.YearMonth.from(clock.today()), repo.transactions.first(), Rates.EMPTY).months.last().income.isZero)
        assertTrue(runCatching { repo.delete(credit.id) }.isFailure)
    }

    @Test fun `link unlink and delete preserve or reverse exactly one expense`() = runTest {
        setup()
        repo.add(tx("t"))
        loans.record(payment(), tx("t"), linkExisting = true)
        assertTrue(runCatching { loans.record(payment().copy(id = "duplicate"), tx("t"), true) }.isFailure)
        assertTrue(runCatching { repo.edit(tx("t")) }.isFailure)
        loans.removePayment("p", false)
        assertEquals(1, repo.transactions.first().size)
        assertEquals(eur("6800"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
        loans.record(payment().copy(id = "p2"), tx("t"), true)
        loans.removePayment("p2", true)
        assertTrue(repo.transactions.first().isEmpty())
    }

    @Test fun `unknown splits block adjustments and can be fixed after account or loan removal`() = runTest {
        setup()
        loans.record(payment(null), tx("t"))
        val correction = LoanAdjustment("j", "l", eur("-10"), clock.today(), "Correction")
        assertTrue(runCatching { loans.adjust(correction) }.isFailure)
        repo.archiveAccount("a")
        loans.archive("l")
        assertEquals(1, repo.transactions.first().size)
        loans.split("p", eur("200"))
        val book = loans.book.first()
        assertTrue(book.loans.single().archived)
        assertTrue(book.balance(book.loans.single(), clock.today()).complete)
        assertTrue(runCatching { loans.record(payment().copy(id = "p2", transactionId = "t2"), tx("t2")) }.isFailure)
    }

    @Test fun `invalid save rolls back whole batch`() = runTest {
        setup()
        val tooMuch = tx("t").copy(money = eur("7000"))
        assertTrue(runCatching { loans.record(LoanPayment("p", "l", "t", eur("7000"), eur("7000")), tooMuch) }.isFailure)
        assertTrue(repo.transactions.first().isEmpty())
        assertTrue(loans.book.first().payments.isEmpty())
        assertTrue(runCatching { loans.record(payment(), tx("t").copy(date = clock.today().plusDays(1))) }.isFailure)
    }
}
