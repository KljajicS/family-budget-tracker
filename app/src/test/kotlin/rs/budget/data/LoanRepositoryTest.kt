package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    @Test fun `concurrent identical loan commands append each event batch once`() = runTest {
        repo.addAccount(Account("a", "Bank", Currency.EUR))
        List(8) { async { loans.create(loan) } }.awaitAll()
        assertEquals(2L, db.eventQueries.count().executeAsOne())
        List(8) { async { loans.record(payment(), tx("t")) } }.awaitAll()
        assertEquals(4L, db.eventQueries.count().executeAsOne())
        val adjustment = LoanAdjustment("correction", "l", eur("-100"), clock.today(), "Statement")
        List(8) { async { loans.adjust(adjustment) } }.awaitAll()
        assertEquals(5L, db.eventQueries.count().executeAsOne())
        assertEquals(eur("6500"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
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

    @Test fun `linked recurring payment deletion makes bill due again`() = runTest {
        setup()
        val rule = RecurringRule("rule", "Car", eur("250"), "a", "category", Cadence.MONTHLY, clock.today())
        repo.setRule(rule)
        val bill = repo.upcomingBills.first().single()
        val transaction = tx(bill.txId).copy(source = TransactionSource.RECURRING)
        repo.add(transaction)
        loans.record(payment().copy(transactionId = transaction.id), transaction, true)
        assertTrue(repo.upcomingBills.first().isEmpty())
        loans.removePayment("p", true)
        assertEquals(bill.txId, repo.upcomingBills.first().single().txId)
    }

    @Test fun `repayment flows to savings goals and monthly spending once`() = runTest {
        repo.addAccount(Account("a", "Cash", Currency.RSD))
        fun rsd(value: String) = Money.of(value, Currency.RSD)
        val target = loan.copy(opening = rsd("6800"))
        loans.create(target)
        repo.add(tx("income", TransactionType.INCOME).copy(money = rsd("1000")))
        repo.setGoal(Goal("g", "Reserve", rsd("1000")))
        val payment = LoanPayment("p", "l", "t", rsd("250"), rsd("200"))
        loans.record(payment, tx("t").copy(money = rsd("250")))
        val financial = repo.financial.first()
        assertEquals(rsd("750"), financial.home.savingsRsd)
        assertEquals(rsd("250"), financial.home.spent)
        assertEquals(rsd("750"), financial.goals.single().funded)
        val insights = InsightsCalculator.summarize(clock.today(), java.time.YearMonth.from(clock.today()), financial.transactions, Rates.EMPTY)
        assertEquals(rsd("250"), insights.spent)
        loans.archive("l")
        assertEquals(rsd("750"), repo.home.first().savingsRsd)
        assertEquals(rsd("6600"), loans.book.first().balance(target, clock.today()).beforeUnknownPayments)
    }

    @Test fun `RSD debit against EUR loan stores actual amounts through replay`() = runTest {
        repo.addAccount(Account("a", "Dinars", Currency.RSD))
        loans.create(loan)
        val debit = tx("t").copy(money = Money.of("29350", Currency.RSD))
        loans.record(payment(), debit)
        assertEquals(Money.of("-29350", Currency.RSD), repo.home.first().balances["a"])
        assertEquals(Money.of("29350", Currency.RSD), repo.home.first().spent)
        assertEquals(eur("6600"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
        store.rebuild()
        assertEquals(debit, repo.transactions.first().single())
        assertEquals(payment(), loans.book.first().payments.single())
        loans.split("p", eur("210"))
        assertEquals(Money.of("-29350", Currency.RSD), repo.home.first().balances["a"])
        assertEquals(eur("6590"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
    }

    @Test fun `metadata cannot rewrite opening and corrections enforce dates and idempotency`() = runTest {
        setup()
        assertTrue(runCatching { loans.update(loan.copy(opening = eur("1"))) }.isFailure)
        assertTrue(runCatching { loans.update(loan.copy(start = clock.today().minusDays(1))) }.isFailure)
        loans.update(loan.copy(name = "Renamed"))
        val correction = LoanAdjustment("j", "l", eur("-10"), clock.today(), "Opening correction")
        assertTrue(runCatching { loans.adjust(correction.copy(date = clock.today().minusDays(1))) }.isFailure)
        assertTrue(runCatching { loans.adjust(correction.copy(date = clock.today().plusDays(1))) }.isFailure)
        loans.adjust(correction)
        loans.adjust(correction)
        assertEquals(eur("6790"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
        loans.archive("l")
        assertTrue(runCatching { loans.update(loan.copy(name = "Again")) }.isFailure)
        assertTrue(runCatching { loans.adjust(correction.copy(id = "j2")) }.isFailure)
    }

    private fun events() = db.eventQueries.count().executeAsOne()

    @Test fun `retries with stable IDs append no duplicate events`() = runTest {
        repo.addAccount(Account("a", "Bank", Currency.EUR))
        val credit = tx("credit", TransactionType.LOAN_RECEIPT)
        val start = events()
        loans.create(loan, credit)
        val afterCreate = events()
        loans.create(loan, credit)
        loans.record(payment(null), tx("t"))
        val afterRecord = events()
        loans.record(payment(null), tx("t"))
        loans.split("p", eur("200"))
        val afterSplit = events()
        loans.split("p", eur("200"))
        val correction = LoanAdjustment("j", "l", eur("-10"), clock.today(), "Correction")
        loans.adjust(correction)
        val afterAdjust = events()
        loans.adjust(correction)
        loans.update(loan.copy(name = "Renamed"))
        val afterUpdate = events()
        loans.update(loan.copy(name = "Renamed"))
        loans.correctReceipt("l", "a", eur("240"))
        val afterReceipt = events()
        loans.correctReceipt("l", "a", eur("240"))
        loans.removePayment("p", false)
        val afterRemove = events()
        loans.removePayment("p", false)
        loans.archive("l")
        val afterArchive = events()
        loans.archive("l")
        assertEquals(listOf(3L, 5L, 6L, 7L, 8L, 9L, 10L, 11L),
            listOf(afterCreate, afterRecord, afterSplit, afterAdjust, afterUpdate, afterReceipt, afterRemove, afterArchive).map { it - start })
        assertEquals(afterArchive, events())
        assertEquals(eur("6790"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
    }

    @Test fun `archived loan keeps payment links and only allows split corrections`() = runTest {
        setup()
        loans.record(payment(null), tx("t"))
        loans.archive("l")
        assertTrue(runCatching { loans.removePayment("p", false) }.isFailure)
        assertTrue(runCatching { loans.removePayment("p", true) }.isFailure)
        loans.split("p", eur("200"))
        assertEquals(1, repo.transactions.first().size)
        assertEquals(eur("200"), loans.book.first().payments.single().principal)
    }

    @Test fun `link to an expense deleted elsewhere is flagged and can still be removed`() = runTest {
        setup()
        loans.record(payment(), tx("t"))
        loans.record(LoanPayment("p2", "l", "t2", eur("250"), eur("100")), tx("t2"))
        store.append(TransactionDeleted("t"))
        store.append(TransactionDeleted("t2"))
        assertTrue(loans.book.first().balance(loan, clock.today()).invalidHistory)
        assertTrue(runCatching { loans.split("p", eur("150")) }.isFailure)
        loans.removePayment("p", false)
        assertTrue(loans.book.first().balance(loan, clock.today()).invalidHistory)
        loans.removePayment("p2", false)
        val balance = loans.book.first().balance(loan, clock.today())
        assertTrue(balance.complete)
        assertEquals(eur("6800"), balance.beforeUnknownPayments)
    }

    @Test fun `receipt correction moves the credit atomically and never touches opening principal`() = runTest {
        repo.addAccount(Account("a", "Bank", Currency.EUR))
        repo.addAccount(Account("b", "Other bank", Currency.EUR))
        repo.addAccount(Account("r", "Dinars", Currency.RSD))
        loans.create(loan, tx("credit", TransactionType.LOAN_RECEIPT))
        loans.correctReceipt("l", "b", eur("6500"))
        assertTrue(repo.home.first().balances["a"]?.isZero != false)
        assertEquals(eur("6500"), repo.home.first().balances["b"])
        assertTrue(repo.home.first().income.isZero)
        assertEquals(eur("6800"), loans.book.first().loans.single().opening)
        assertEquals(eur("6800"), loans.book.first().balance(loan, clock.today()).beforeUnknownPayments)
        assertTrue(runCatching { loans.correctReceipt("l", "r", eur("6500")) }.isFailure)
        repo.archiveAccount("a")
        assertTrue(runCatching { loans.correctReceipt("l", "a", eur("6500")) }.isFailure)
        store.rebuild()
        assertEquals(eur("6500"), repo.home.first().balances["b"])
        assertEquals("b", loans.book.first().receipt("l")!!.accountId)
        loans.archive("l")
        assertTrue(runCatching { loans.correctReceipt("l", "b", eur("6000")) }.isFailure)
        assertTrue(runCatching { repo.edit(loans.book.first().receipt("l")!!) }.isFailure)
        loans.create(loan.copy(id = "existing"))
        assertTrue(runCatching { loans.correctReceipt("existing", "b", eur("1")) }.isFailure)
    }

    @Test fun `book history lists vanished links first and link picker excludes linked expenses`() = runTest {
        setup()
        repo.add(tx("old").copy(date = clock.today()))
        loans.record(payment(), tx("t"))
        loans.record(LoanPayment("p2", "l", "gone", eur("250"), eur("10")), tx("gone"))
        store.append(TransactionDeleted("gone"))
        val book = loans.book.first()
        assertEquals(listOf("p2" to null, "p" to "t"), book.history("l").map { it.first.id to it.second?.id })
        assertEquals(listOf("old"), book.linkCandidates(loan, clock.today()).map { it.id })
    }

    @Test fun `receipt validation rejects stale accounts invalid dates and mismatched retries`() = runTest {
        repo.addAccount(Account("a", "Bank", Currency.EUR))
        val receipt = tx("credit", TransactionType.LOAN_RECEIPT)
        assertTrue(runCatching { loans.create(loan, receipt.copy(date = clock.today().minusDays(1))) }.isFailure)
        assertTrue(runCatching { loans.create(loan.copy(opening = eur("0")), receipt) }.isFailure)
        loans.create(loan, receipt)
        assertTrue(runCatching { loans.create(loan, receipt.copy(money = eur("251"))) }.isFailure)
        repo.archiveAccount("a")
        assertTrue(runCatching { loans.create(loan.copy(id = "l2"), receipt.copy(id = "other")) }.isFailure)
        assertEquals(1, repo.transactions.first().size)
    }
}
