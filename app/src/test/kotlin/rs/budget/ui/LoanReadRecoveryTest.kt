package rs.budget.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import rs.budget.data.*
import rs.budget.data.db.BudgetDb
import rs.budget.domain.*
import rs.budget.ui.loans.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LoanReadRecoveryTest {
    private val day = LocalDate.of(2026, 10, 17)
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val repo = BudgetRepository(db, EventStore(db, "d", clock, Projector(db)), clock, "d")
    private val accountStore = TransactionAccountStore({ null }, { _, _ -> })
    private val models = mutableListOf<ViewModel>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private fun hide(table: String) = driver.execute(null, "ALTER TABLE $table RENAME TO ${table}_hidden", 0)
    private fun restore(table: String) = driver.execute(null, "ALTER TABLE ${table}_hidden RENAME TO $table", 0)

    private suspend fun seed() {
        repo.setUp("S", listOf(Account("a", "Euro", Currency.EUR)))
        repo.loans.create(Loan("l", "Car", Money.of("6800", Currency.EUR), day))
    }

    private fun detail(id: String = "l") = LoanDetailViewModel(repo, clock, SavedStateHandle(mapOf("id" to id)), accountStore).also { models += it }

    @Test fun `detail read failure blocks writes and retry keeps the open action and draft`() = runTest(dispatcher) {
        seed()
        val vm = detail()
        backgroundScope.launch { vm.data.collect {} }
        vm.data.first { it != null }
        vm.begin(LoanAction.PAYMENT)
        vm.editForm(vm.form.value.copy(amount = "250", principal = "200"))
        assertNotNull(vm.paymentCheck().value)

        hide("tx")
        driver.notifyListeners("loan")
        vm.loadFailed.first { it }
        vm.data.first { it == null }
        assertNull(vm.paymentCheck().value)
        vm.savePayment()
        vm.archive()
        assertFalse(vm.writes.state.value.busy)

        restore("tx")
        vm.retry()
        vm.data.first { it != null }
        assertFalse(vm.loadFailed.value)
        assertEquals(LoanAction.PAYMENT, vm.action.value)
        assertEquals("250", vm.form.value.amount)
        vm.savePayment()
        vm.writes.state.first { !it.busy }
        assertFalse(vm.writes.state.value.failed)
        assertEquals(Money.of("200", Currency.EUR), repo.loans.book.first().payments.single().principal)
    }

    @Test fun `failed category read blocks actions until retry reloads every detail input`() = runTest(dispatcher) {
        seed()
        hide("category")
        val vm = detail()
        backgroundScope.launch { vm.data.collect {} }
        vm.loadFailed.first { it }
        vm.begin(LoanAction.PAYMENT)
        assertEquals(LoanAction.NONE, vm.action.value)

        restore("category")
        vm.retry()
        val loaded = vm.data.first { it != null }!!
        assertEquals(listOf("a"), loaded.accounts.map { it.id })
        assertTrue(loaded.categories.isNotEmpty())
        assertEquals("l", vm.loan()?.id)
    }

    @Test fun `editor read failure keeps the draft and blocks saving until retry`() = runTest(dispatcher) {
        seed()
        val vm = LoanEditorViewModel(repo, clock, SavedStateHandle(mapOf("id" to "l"))).also { models += it }
        backgroundScope.launch { vm.data.collect {} }
        vm.data.first { it?.loan != null }
        vm.edit(vm.form.value.copy(name = "Draft"))

        hide("account")
        driver.notifyListeners("account")
        vm.loadFailed.first { it }
        vm.data.first { it == null }
        assertFalse(vm.canSave())
        vm.save()
        assertFalse(vm.writes.state.value.busy)

        restore("account")
        vm.retry()
        vm.data.first { it?.loan != null }
        assertEquals("Draft", vm.form.value.name)
        assertTrue(vm.canSave())
        vm.save()
        vm.writes.state.first { it.finished }
        assertEquals("Draft", repo.loans.book.first().loans.single().name)
    }

    @Test fun `a missing loan is not reported as a read failure`() = runTest(dispatcher) {
        seed()
        val vm = detail("nope")
        val loaded = vm.data.first { it != null }!!
        assertFalse(vm.loadFailed.value)
        assertTrue(loaded.book.loans.isNotEmpty())
        assertNull(vm.loan())
        assertNull(vm.adjustmentCheck().value)
    }

    @Test fun `loan list surfaces a failed read and recovers on retry`() = runTest(dispatcher) {
        seed()
        hide("loan")
        val vm = LoanListViewModel(repo, clock).also { models += it }
        backgroundScope.launch { vm.book.collect {} }
        vm.loadFailed.first { it }
        assertNull(vm.book.value)
        restore("loan")
        vm.retry()
        assertEquals("Car", vm.book.first { it != null }!!.loans.single().name)
        assertFalse(vm.loadFailed.value)
    }
}
