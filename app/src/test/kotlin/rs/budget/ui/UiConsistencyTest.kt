package rs.budget.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.RecurringRule
import rs.budget.ui.goals.EditGoalState
import rs.budget.ui.settings.EditAccountViewModel
import rs.budget.ui.settings.EditRecurringState
import rs.budget.ui.settings.Loadable
import rs.budget.ui.setup.SetupViewModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class UiConsistencyTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<ViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun `money inputs accept a decimal comma but still reject ambiguous grouping`() {
        assertEquals(Money.of("12.5", Currency.RSD), parseMoneyInput("12,5", Currency.RSD))
        assertEquals(Money.of("1500.25", Currency.EUR), parseMoneyInput(" 1500,25 ", Currency.EUR))
        assertNull(parseMoneyInput("1.234,56", Currency.RSD))
        assertNull(parseMoneyInput("1,234.5", Currency.RSD))
        assertNull(parseMoneyInput("12,345", Currency.RSD))

        assertEquals(Money.of("3000.5", Currency.EUR), EditGoalState(name = "Trip", targetText = "3000,5", currency = Currency.EUR).target)
        val eur = Account("eur", "Euro", Currency.EUR)
        val bill = EditRecurringState(name = "Netflix", amountText = "12,99", accountId = "eur", categoryId = "cat-bills", firstDue = LocalDate.of(2026, 10, 28), accounts = listOf(eur))
        assertEquals(Money.of("12.99", Currency.EUR), bill.amount)
        assertTrue(bill.canSave)
    }

    @Test
    fun `dates use Latin month names for Serbian`() {
        val date = LocalDate.of(2026, 10, 28)
        assertEquals("28 okt 2026", formatDate(date, Locale.forLanguageTag("sr")))
        assertEquals("28 okt 2026", formatDate(date, Locale.forLanguageTag("sr-RS")))
        assertEquals("28 Oct 2026", formatDate(date, Locale.US))
    }

    @Test
    fun `setup saves starter accounts in the app language and keeps typed names`() = runTest(dispatcher) {
        val serbian = mapOf(
            R.string.setup_account_cash to "Keš",
            R.string.setup_account_card to "Kartica",
            R.string.audit_ui_account_savings to "Štednja",
        )
        val vm = SetupViewModel(repo).also { models += it }
        vm.setName("Ana")
        vm.toggleAccount(2)
        vm.addAccount("Devizni", Currency.EUR)
        vm.finish { serbian[it] }
        vm.state.first { it.done }
        assertEquals(listOf("Keš", "Kartica", "Štednja", "Devizni"), repo.accounts.first().map { it.name })
    }

    @Test
    fun `account removal lists only active bills paid from that account`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD), Account("eur", "Euro", Currency.EUR)))
        repo.setRule(RecurringRule("r1", "Netflix", Money.of("12.99", Currency.EUR), "eur", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 28)))
        repo.setRule(RecurringRule("r2", "Rent", Money.of("500", Currency.RSD), "cash", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 1)))

        val vm = EditAccountViewModel(repo, SavedStateHandle(mapOf("id" to "eur"))).also { models += it }
        vm.state.first { it.bills == listOf("Netflix") }
        repo.archiveRule("r1")
        vm.state.first { it.bills.isEmpty() }
    }

    @Test
    fun `a failed screen load shows an error and recovers on retry`() = runTest(dispatcher) {
        var calls = 0
        val load = Loadable(backgroundScope, flow {
            if (calls++ == 0) throw IOException("disk")
            emit(7)
        })
        backgroundScope.launch { load.state.collect {} }
        load.failed.first { it }
        assertNull(load.state.value)

        load.retry()
        assertEquals(7, load.state.filterNotNull().first())
        assertFalse(load.failed.value)
    }
}
