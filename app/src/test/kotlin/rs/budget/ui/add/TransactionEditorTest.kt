package rs.budget.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.TransactionAccountStore
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Loan
import rs.budget.domain.Money
import rs.budget.domain.Receipt
import rs.budget.domain.RecurringRule
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.billTxId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")
    private val remembered = mutableMapOf<TransactionType, String>()
    private val accountStore = TransactionAccountStore(remembered::get) { type, id -> remembered[type] = id }
    private fun rsd(v: String) = Money.of(v, Currency.RSD)
    private fun tx(id: String, source: TransactionSource = TransactionSource.MANUAL, account: String = "cash") =
        Transaction(id, TransactionType.EXPENSE, rsd("100"), account, null, "cat-groceries", clock.today().minusDays(2), "kept", "Shop", "other-member", source)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<ViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private suspend fun seed() = repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD), Account("card", "Card", Currency.RSD), Account("eur", "EUR", Currency.EUR)))

    private suspend fun editor(id: String, handle: SavedStateHandle = SavedStateHandle(mapOf("id" to id))): AddViewModel =
        AddViewModel(repo, clock, handle, accountStore).also { models += it }.also { vm -> vm.state.first { (it.editing != null || it.missing) && it.categories.isNotEmpty() } }

    @Test fun `correcting an entry keeps id payer source and note and leaves account preference alone`() = runTest(dispatcher) {
        seed()
        repo.add(tx("t"))
        val vm = editor("t")
        val s = vm.state.value
        assertEquals("100", s.amount.text)
        assertEquals("cash", s.accountId)
        assertEquals(clock.today().minusDays(2), s.date)
        "5".forEach(vm::press)
        vm.setAccount("card")
        vm.setDate(clock.today().plusDays(3))
        vm.setMerchant(" Market ")
        vm.save()
        vm.state.first { it.saved }
        val saved = repo.transactions.first().single()
        assertEquals(tx("t").copy(money = rsd("1005"), accountId = "card", date = clock.today().plusDays(3), merchant = "Market"), saved)
        assertTrue(remembered.isEmpty())
        assertEquals(rsd("0"), repo.home.first().balances["card"])
    }

    @Test fun `ordinary entries can change type and transfers need an active destination`() = runTest(dispatcher) {
        seed()
        repo.add(Transaction("x", TransactionType.TRANSFER, rsd("50"), "cash", "card", null, clock.today(), "", "", "m", TransactionSource.MANUAL))
        repo.archiveAccount("card")
        val vm = editor("x")
        assertEquals("card", vm.state.value.toAccountId)
        assertFalse(vm.state.value.canSave)
        vm.setType(TransactionType.INCOME)
        assertTrue(vm.state.value.canSave)
        vm.save()
        vm.state.first { it.saved }
        val saved = repo.transactions.first().single()
        assertEquals(TransactionType.INCOME, saved.type)
        assertNull(saved.toAccountId)
        assertNull(saved.categoryId)
    }

    @Test fun `archived account and category are never swapped silently`() = runTest(dispatcher) {
        seed()
        repo.add(tx("t"))
        repo.archiveAccount("cash")
        repo.archiveCategory("cat-groceries")
        val vm = editor("t")
        val s = vm.state.value
        assertNull(s.accountId)
        assertEquals("cat-groceries", s.categoryId)
        assertFalse(s.canSave)
        vm.setAccount("card")
        assertFalse(vm.state.value.canSave)
        vm.setCategory(vm.state.value.categories.first().id)
        assertTrue(vm.state.value.canSave)
        vm.save()
        vm.state.first { it.saved }
        assertEquals("card", repo.transactions.first().single().accountId)
    }

    @Test fun `receipt corrections stay RSD expenses`() = runTest(dispatcher) {
        seed()
        val receipt = Receipt("https://suf.purs.gov.rs/v/?vl=x", "1", "INV-1", "Shop", clock.today(), rsd("100"), emptyList())
        repo.addReceipt(tx(receipt.transactionId, TransactionSource.RECEIPT), receipt)
        val vm = editor(receipt.transactionId)
        vm.setType(TransactionType.INCOME)
        assertEquals(TransactionType.EXPENSE, vm.state.value.type)
        vm.setAccount("eur")
        assertFalse(vm.state.value.canSave)
        vm.setAccount("card")
        vm.save()
        vm.state.first { it.saved }
        assertEquals(receipt.transactionId, repo.transactions.first().single().id)
        assertEquals(TransactionSource.RECEIPT, repo.transactions.first().single().source)
    }

    @Test fun `paid bill cannot move into the future and deleting it reopens the bill`() = runTest(dispatcher) {
        seed()
        repo.setRule(RecurringRule("rent", "Rent", rsd("100"), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 1)))
        val id = billTxId("rent", "2026-10")
        repo.add(tx(id, TransactionSource.RECURRING).copy(categoryId = "cat-bills"))
        val vm = editor(id)
        vm.setDate(clock.today().plusDays(1))
        assertTrue(vm.state.value.billInFuture)
        assertFalse(vm.state.value.canSave)
        vm.setDate(clock.today())
        assertTrue(vm.state.value.canSave)
        assertTrue(repo.upcomingBills.first().none { it.txId == id })
        vm.delete()
        vm.state.first { it.saved }
        assertTrue(repo.transactions.first().isEmpty())
        assertTrue(repo.upcomingBills.first().any { it.txId == id })
    }

    @Test fun `loan transactions and missing ids open read only`() = runTest(dispatcher) {
        seed()
        val credit = Transaction("credit", TransactionType.LOAN_RECEIPT, rsd("1000"), "cash", null, null, clock.today(), "", "", "m", TransactionSource.MANUAL)
        repo.loans.create(Loan("l", "Car", rsd("1000"), clock.today()), credit)
        val locked = editor("credit")
        assertTrue(locked.state.value.loanLocked)
        assertFalse(locked.state.value.canSave)
        locked.save()
        locked.delete()
        advanceUntilIdle()
        assertEquals(listOf(credit), repo.transactions.first())

        val missing = editor("gone")
        assertTrue(missing.state.value.missing)
        assertFalse(missing.state.value.canSave)
        "5".forEach(missing::press)
        missing.save()
        advanceUntilIdle()
        assertEquals(1, repo.transactions.first().size)
    }

    @Test fun `ordinary draft survives process recreation within the same rules`() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = AddViewModel(repo, clock, handle, accountStore).also { models += it }.also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
        first.setType(TransactionType.TRANSFER)
        first.setAccount("cash")
        first.setToAccount("card")
        "12.5".forEach(first::press)
        first.setDate(clock.today().plusDays(1))
        first.setMerchant("Rent split")
        advanceUntilIdle()

        val restored = AddViewModel(repo, clock, handle, accountStore).also { models += it }.also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
        val s = restored.state.value
        assertEquals(TransactionType.TRANSFER, s.type)
        assertEquals("12.5", s.amount.text)
        assertEquals("cash", s.accountId)
        assertEquals("card", s.toAccountId)
        assertEquals(clock.today().plusDays(1), s.date)
        assertEquals("Rent split", s.merchant)
        assertTrue(s.canSave)

        val tampered = SavedStateHandle(mapOf("draft.type" to "LOAN_RECEIPT", "draft.amount" to "1x2.345", "draft.date" to "nope",
            "draft.category" to "cat-fun", "draft.account" to "eur", "draft.merchant" to "M"))
        val sanitized = AddViewModel(repo, clock, tampered, accountStore).also { models += it }.also { vm -> vm.state.first { it.accounts.isNotEmpty() } }.state.value
        assertEquals(TransactionType.EXPENSE, sanitized.type)
        assertEquals("12.34", sanitized.amount.text)
        assertEquals(clock.today(), sanitized.date)
        assertEquals(Currency.EUR, sanitized.currency)
    }

    @Test fun `bill and edit drafts restore over their source while receipts are never persisted`() = runTest(dispatcher) {
        seed()
        repo.setRule(RecurringRule("rent", "Rent", rsd("100"), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 20)))
        val billHandle = SavedStateHandle(mapOf("rule" to "rent", "due" to "2026-10-20"))
        val bill = AddViewModel(repo, clock, billHandle, accountStore).also { models += it }.also { vm -> vm.state.first { it.bill != null && it.accounts.isNotEmpty() } }
        bill.press('5')
        bill.setAccount("card")
        advanceUntilIdle()
        billHandle["draft.type"] = TransactionType.INCOME.name
        billHandle["draft.date"] = "2030-01-01"
        val billRestored = AddViewModel(repo, clock, billHandle, accountStore).also { models += it }.also { vm -> vm.state.first { it.bill != null && it.accounts.isNotEmpty() } }.state.value
        assertEquals("1005", billRestored.amount.text)
        assertEquals("card", billRestored.accountId)
        assertEquals(TransactionType.EXPENSE, billRestored.type)
        assertEquals(clock.today(), billRestored.date)

        repo.add(tx("t"))
        val editHandle = SavedStateHandle(mapOf("id" to "t"))
        editor("t", editHandle).setMerchant("Changed")
        advanceUntilIdle()
        assertEquals("Changed", editor("t", editHandle).state.value.merchant)

        val scanHandle = SavedStateHandle()
        val scan = AddViewModel(repo, clock, scanHandle, accountStore).also { models += it }.also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
        scan.prefillReceipt(Receipt("https://suf.purs.gov.rs/v/?vl=secret", "1", "INV-2", "Shop", clock.today(), rsd("10"), emptyList()), null)
        advanceUntilIdle()
        assertTrue(scanHandle.keys().none { it.startsWith("draft.") })
    }
}
