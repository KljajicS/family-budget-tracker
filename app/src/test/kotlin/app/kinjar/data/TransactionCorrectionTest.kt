package app.kinjar.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.kinjar.domain.Account
import app.kinjar.domain.Cadence
import app.kinjar.domain.Currency
import app.kinjar.domain.Goal
import app.kinjar.domain.Loan
import app.kinjar.domain.Money
import app.kinjar.domain.Receipt
import app.kinjar.domain.RecurringRule
import app.kinjar.domain.Transaction
import app.kinjar.domain.TransactionSource
import app.kinjar.domain.TransactionType
import app.kinjar.domain.billTxId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionCorrectionTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val repo = BudgetRepository(db, EventStore(db, "d", clock, Projector(db)), clock, "d")
    private fun rsd(v: String) = Money.of(v, Currency.RSD)
    private fun tx(id: String, account: String = "cash", source: TransactionSource = TransactionSource.MANUAL, date: LocalDate = clock.today()) =
        Transaction(id, TransactionType.EXPENSE, rsd("100"), account, null, "cat-groceries", date, "note", "Shop", "m", source)

    private suspend fun setup() = repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD), Account("card", "Card", Currency.RSD), Account("eur", "EUR", Currency.EUR)))

    @Test fun `edits keep identity and may move an ordinary entry into the future`() = runTest {
        setup()
        repo.add(tx("t"))
        val future = tx("t").copy(money = rsd("150"), date = clock.today().plusDays(10), accountId = "card")
        repo.edit(future)
        assertEquals(future, repo.transaction("t"))
        assertEquals(rsd("0"), repo.home.first().balances["card"])
        clock.instant = clock.instant.plusSeconds(10L * 86_400)
        assertEquals(rsd("-150"), repo.home.first().balances["card"])
    }

    @Test fun `edits cannot change source or payer, invent a row, or break receipt and bill rules`() = runTest {
        setup()
        repo.add(tx("t"))
        assertTrue(runCatching { repo.edit(tx("t", source = TransactionSource.RECURRING)) }.isFailure)
        assertTrue(runCatching { repo.edit(tx("t").copy(paidBy = "other")) }.isFailure)
        assertTrue(runCatching { repo.edit(tx("missing")) }.isFailure)
        assertNull(repo.transaction("missing"))

        val receipt = Receipt("https://suf.purs.gov.rs/v/?vl=x", "1", "INV-1", "Shop", clock.today(), rsd("100"), emptyList())
        repo.addReceipt(tx(receipt.transactionId, source = TransactionSource.RECEIPT), receipt)
        val stored = repo.transaction(receipt.transactionId)!!
        assertTrue(runCatching { repo.edit(stored.copy(type = TransactionType.INCOME, categoryId = null)) }.isFailure)
        assertTrue(runCatching { repo.edit(stored.copy(money = Money.of("1", Currency.EUR), accountId = "eur")) }.isFailure)
        repo.edit(stored.copy(money = rsd("90"), accountId = "card"))
        assertEquals(rsd("90"), repo.transaction(receipt.transactionId)!!.money)
        assertTrue(repo.hasReceipt(receipt.id))

        repo.setRule(RecurringRule("rent", "Rent", rsd("100"), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 1)))
        val bill = tx(billTxId("rent", "2026-10"), source = TransactionSource.RECURRING)
        repo.add(bill)
        assertTrue(runCatching { repo.edit(bill.copy(date = clock.today().plusDays(1))) }.isFailure)
        repo.edit(bill.copy(date = clock.today().minusDays(3), money = rsd("120")))
        assertTrue(repo.upcomingBills.first().none { it.txId == bill.id })
        repo.delete(bill.id)
        assertTrue(repo.upcomingBills.first().any { it.txId == bill.id })
    }

    @Test fun `archived account entries can be deleted or moved to an active account but not edited in place`() = runTest {
        setup()
        repo.add(tx("a"))
        repo.add(tx("b"))
        repo.archiveAccount("cash")
        assertTrue(runCatching { repo.edit(tx("a").copy(money = rsd("1"))) }.isFailure)
        repo.edit(tx("a").copy(accountId = "card"))
        repo.delete("b")
        assertEquals(listOf("a"), repo.transactions.first().map { it.id })
        assertEquals("card", repo.transaction("a")!!.accountId)
    }

    @Test fun `loan transactions report as linked and stay protected`() = runTest {
        setup()
        repo.add(tx("plain"))
        val credit = Transaction("credit", TransactionType.LOAN_RECEIPT, rsd("1000"), "cash", null, null, clock.today(), "", "", "m", TransactionSource.MANUAL)
        repo.loans.create(Loan("l", "Car", rsd("1000"), clock.today()), credit)
        assertTrue(repo.isLoanLinked("credit"))
        assertFalse(repo.isLoanLinked("plain"))
        assertTrue(runCatching { repo.edit(credit.copy(money = rsd("1"))) }.isFailure)
        assertTrue(runCatching { repo.delete("credit") }.isFailure)
    }

    @Test fun `goals use the unrounded pool and empty foreign accounts need no rate`() = runTest {
        repo.setUp("S", listOf(Account("eur", "EUR", Currency.EUR), Account("usd", "USD", Currency.USD)))
        repo.exchangeRates.setOverride(Currency.EUR, "117.2")
        repo.add(Transaction("t", TransactionType.INCOME, Money.of("100.01", Currency.EUR), "eur", null, null, clock.today(), "", "", "m", TransactionSource.MANUAL))
        repo.setGoal(Goal("g", "Trip", Money.of("100.01", Currency.EUR)))
        val financial = repo.financial.first { it.exchange.rates.rate(Currency.EUR) != null }
        assertTrue(financial.goals.single().reached)
        assertEquals(rsd("11721.17"), financial.home.savingsRsd)
        assertFalse(Currency.USD in financial.missingCurrencies)
    }
}
