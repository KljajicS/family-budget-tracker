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
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.TransactionType
import java.time.Instant
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
        return AddViewModel(repo, clock).also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
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
        // Account.sq `active` orders by name, so "Card" is the default account.
        assertEquals("card", t.accountId)
        assertEquals(clock.today(), t.date)
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
}
