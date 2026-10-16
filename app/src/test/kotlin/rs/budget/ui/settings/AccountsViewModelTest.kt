package rs.budget.ui.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.db.BudgetDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.ui.home.HomeViewModel
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `reorder is shared with home and a failed write keeps the stored order`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD), Account("card", "Card", Currency.RSD), Account("eur", "Euro", Currency.EUR)))
        val vm = AccountsViewModel(repo)
        vm.state.filterNotNull().first { it.accounts.size == 3 }
        vm.reorder(listOf("eur", "cash", "card"))
        vm.state.filterNotNull().first { s -> s.accounts.map { it.account.id } == listOf("eur", "cash", "card") }
        HomeViewModel(repo, clock).state.filterNotNull().first { s -> s.accounts.map { it.account.id } == listOf("eur", "cash", "card") }

        driver.execute(null, "CREATE TRIGGER fail_order BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        vm.reorder(listOf("card", "cash", "eur"))
        vm.writes.state.first { it.failed }
        val s = vm.state.filterNotNull().first { it.reorderFailures == 1 }
        assertEquals(listOf("eur", "cash", "card"), s.accounts.map { it.account.id })
    }

    @Test
    fun `editing keeps the legacy kind and archived accounts leave the list`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("sav", "Savings", Currency.RSD, AccountKind.SAVINGS), Account("cash", "Cash", Currency.RSD)))
        val edit = EditAccountViewModel(repo, SavedStateHandle(mapOf("id" to "sav")))
        edit.state.first { it.name == "Savings" && it.balance != null }
        assertEquals(Money.zero(Currency.RSD), edit.state.value.balance)
        edit.setName("Rainy day")
        edit.save()
        edit.state.first { it.done }
        assertEquals(AccountKind.SAVINGS, repo.accounts.first().first { it.id == "sav" }.kind)

        repo.archiveAccount("sav")
        val s = AccountsViewModel(repo).state.filterNotNull().first { it.accounts.size == 1 }
        assertEquals("cash", s.accounts.single().account.id)
    }
}
