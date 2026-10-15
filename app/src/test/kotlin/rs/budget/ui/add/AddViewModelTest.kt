package rs.budget.ui.add

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import androidx.lifecycle.SavedStateHandle
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.RecurringRule
import rs.budget.domain.Receipt
import rs.budget.domain.ReceiptItem
import java.math.BigDecimal
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.billTxId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun seeded(): AddViewModel {
        repo.setUp("S", listOf(
            Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING),
            Account("card", "Card", Currency.RSD, AccountKind.SPENDING),
            Account("eur", "EUR", Currency.EUR, AccountKind.SAVINGS),
        ))
        // Repository flows query on Dispatchers.IO, so wait for real data instead of advancing the virtual clock.
        return AddViewModel(repo, clock, SavedStateHandle()).also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
    }

    @Test
    fun `save disabled until amount entered, then saves expense with defaults`() = runTest(dispatcher) {
        val vm = seeded()
        assertFalse(vm.state.value.canSave)
        "1240".forEach(vm::press)
        vm.setCategory("cat-household")
        assertTrue(vm.state.value.canSave)
        vm.save()
        vm.state.first { it.saved }
        val t = repo.transactions.first().single()
        assertEquals(TransactionType.EXPENSE, t.type)
        assertEquals("1240.00", t.money.amount.toPlainString())
        assertEquals("card", t.accountId)
        assertEquals(clock.today(), t.date)
    }

    @Test
    fun `manual entry replaces archived default category before saving`() = runTest(dispatcher) {
        val vm = seeded()
        assertEquals("cat-groceries", vm.state.value.categoryId)
        repo.archiveCategory("cat-groceries")
        val state = vm.state.first { it.categories.none { category -> category.id == "cat-groceries" } }
        assertTrue(state.categories.any { it.id == state.categoryId })
        assertEquals(state.categories.first().id, state.categoryId)
        "100".forEach(vm::press)
        vm.save()
        vm.state.first { it.saved }
        assertEquals(state.categoryId, repo.transactions.first().single().categoryId)
    }

    @Test
    fun `zero amount cannot be saved`() = runTest(dispatcher) {
        val vm = seeded()
        "0.00".forEach(vm::press)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `transfer to same account is rejected with a message`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setType(TransactionType.TRANSFER)
        "500".forEach(vm::press)
        vm.setAccount("cash")
        vm.setToAccount("cash")
        assertFalse(vm.state.value.canSave)
        assertEquals(R.string.add_same_account, vm.state.value.error)
    }

    @Test
    fun `transfer across currencies is rejected`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setType(TransactionType.TRANSFER)
        "500".forEach(vm::press)
        vm.setAccount("cash")
        vm.setToAccount("eur")
        assertEquals(R.string.add_currency_mismatch, vm.state.value.error)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `choosing an account sets the currency`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setAccount("eur")
        assertEquals(Currency.EUR, vm.state.value.currency)
        assertNotNull(vm.state.value.accounts.firstOrNull { it.id == "eur" })
    }

    private suspend fun billVm(): AddViewModel {
        seeded()
        repo.setRule(RecurringRule("rent", "Rent", Money.of("40000", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 20)))
        val vm = AddViewModel(repo, clock, SavedStateHandle(mapOf("rule" to "rent", "due" to "2026-10-20")))
        vm.state.first { it.bill != null && it.accounts.isNotEmpty() }
        return vm
    }

    @Test
    fun `a bill pre-fills the sheet, locks the type and saves with the derived id`() = runTest(dispatcher) {
        val vm = billVm()
        val s = vm.state.value
        assertEquals("40000", s.amount.text)
        assertEquals("cash", s.accountId)
        assertEquals("cat-bills", s.categoryId)
        assertEquals("Rent", s.merchant)
        assertEquals(clock.today(), s.date)
        vm.setDate(clock.today().plusDays(5))
        assertEquals(clock.today(), vm.state.value.date)
        vm.setType(TransactionType.INCOME)
        assertEquals(TransactionType.EXPENSE, vm.state.value.type)
        vm.press('<')
        vm.press('5')
        vm.save()
        vm.state.first { it.saved }
        val t = repo.transactions.first().single()
        assertEquals(billTxId("rent", "2026-10"), t.id)
        assertEquals(TransactionSource.RECURRING, t.source)
        assertEquals("40005.00", t.money.amount.toPlainString())
        assertEquals(0, repo.upcomingBills.first().size)
    }

    @Test
    fun `skipping a bill writes a skip and closes the sheet`() = runTest(dispatcher) {
        val vm = billVm()
        vm.skip()
        vm.state.first { it.saved }
        assertEquals(0, repo.transactions.first().size)
        assertEquals(0, repo.upcomingBills.first().size)
    }

    private fun receipt() = Receipt("https://suf.purs.gov.rs/v/?vl=test", "123456789", "ABCDEFGH-IJKLMNOP-1", "Shop",
        LocalDate.of(2026, 10, 1), Money.of("123", Currency.RSD),
        listOf(ReceiptItem("Food", BigDecimal.ONE, BigDecimal("123"), BigDecimal("123"))))

    @Test fun `receipt prefill locks expense and RSD and preserves edits on repeated delivery`() = runTest(dispatcher) {
        val vm = seeded()
        val receipt = receipt()
        vm.prefillReceipt(receipt, "cat-household")
        vm.setType(TransactionType.INCOME)
        assertEquals(TransactionType.EXPENSE, vm.state.value.type)
        vm.setAccount("eur")
        assertFalse(vm.state.value.canSave)
        vm.setAccount("cash")
        vm.setMerchant("Edited")
        vm.press('<')
        vm.press('5')
        vm.prefillReceipt(receipt, "cat-groceries")
        assertEquals("Edited", vm.state.value.merchant)
        assertEquals("cat-household", vm.state.value.categoryId)
        vm.save()
        vm.save()
        vm.state.first { it.saved }
        val t = repo.transactions.first().single()
        assertEquals(receipt.transactionId, t.id)
        assertEquals(TransactionSource.RECEIPT, t.source)
        assertEquals("123.05", t.money.amount.toPlainString())
        assertEquals("123.00", db.receiptQueries.byId(receipt.id).executeAsOne().total)
    }

    @Test fun `duplicate save shows error and retains receipt review`() = runTest(dispatcher) {
        val vm = seeded()
        vm.prefillReceipt(receipt(), null)
        vm.save()
        vm.state.first { it.saved }
        val second = AddViewModel(repo, clock, SavedStateHandle())
        second.state.first { it.accounts.isNotEmpty() }
        second.prefillReceipt(receipt(), null)
        second.save()
        second.state.first { it.saveError != null }
        assertEquals(R.string.scan_duplicate, second.state.value.saveError)
        assertEquals(receipt(), second.state.value.receipt)
        assertEquals(1, repo.transactions.first().size)
    }

    @Test fun `archived receipt account disables save and cancel clears draft`() = runTest(dispatcher) {
        val vm = seeded()
        vm.prefillReceipt(receipt(), null)
        val selected = vm.state.value.accountId!!
        repo.archiveAccount(selected)
        vm.state.first { it.accounts.none { a -> a.id == selected } }
        assertFalse(vm.state.value.canSave)
        vm.resetReceipt()
        assertEquals(null, vm.state.value.receipt)
        assertFalse(vm.state.value.canSave)
    }
}
