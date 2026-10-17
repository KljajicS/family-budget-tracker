package rs.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.db.BudgetDb
import rs.budget.domain.Account
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<ViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private suspend fun seed(count: Int) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD)))
        repeat(count) { i ->
            repo.add(Transaction("t$i", TransactionType.INCOME, Money.of("1", Currency.RSD), "cash", null, null,
                clock.today().minusDays(i.toLong()), "", "", "m", TransactionSource.MANUAL))
        }
    }

    @Test fun `older transactions stay reachable past the first page`() = runTest(dispatcher) {
        seed(45)
        val vm = HomeViewModel(repo, clock).also { models += it }
        backgroundScope.launch { vm.state.collect {} }
        val first = vm.state.filterNotNull().first()
        assertEquals(20, first.recent.size)
        assertTrue(first.hasOlder)
        vm.showOlder()
        vm.showOlder()
        val all = vm.state.filterNotNull().first { it.recent.size > 40 }
        assertEquals(45, all.recent.size)
        assertFalse(all.hasOlder)
        assertEquals("t44", all.recent.last().transaction.id)
    }

    @Test fun `exactly one page does not offer older rows`() = runTest(dispatcher) {
        seed(20)
        val s = HomeViewModel(repo, clock).also { models += it }.state.filterNotNull().first()
        assertEquals(20, s.recent.size)
        assertFalse(s.hasOlder)
    }

    @Test fun `a failed read shows an error and retry recovers`() = runTest(dispatcher) {
        seed(1)
        driver.execute(null, "ALTER TABLE tx RENAME TO tx_hidden", 0)
        val vm = HomeViewModel(repo, clock).also { models += it }
        backgroundScope.launch { vm.state.collect {} }
        vm.failed.first { it }
        driver.execute(null, "ALTER TABLE tx_hidden RENAME TO tx", 0)
        vm.retry()
        val s = vm.state.filterNotNull().first()
        assertEquals(1, s.recent.size)
        assertFalse(vm.failed.value)
    }
}
