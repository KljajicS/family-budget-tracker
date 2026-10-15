package rs.budget.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import rs.budget.data.*
import rs.budget.data.db.BudgetDb
import rs.budget.data.rates.*
import rs.budget.domain.*
import rs.budget.ui.home.HomeViewModel
import rs.budget.ui.budgets.BudgetsViewModel
import rs.budget.ui.goals.GoalsViewModel
import rs.budget.ui.insights.InsightsViewModel
import rs.budget.ui.settings.ExchangeRatesViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = BudgetDb(driver).also { BudgetDb.Schema.create(driver) }
    private val clock = FixedClock(Instant.parse("2026-10-15T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val repo = BudgetRepository(db, EventStore(db, "device", clock, Projector(db)), clock)
    private val rates = repo.exchangeRates

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun `stale warning starts at seven days and manual overrides exempt only their currency`() = runTest(dispatcher) {
        rates.cache(mapOf(Currency.EUR to BigDecimal("117"), Currency.USD to BigDecimal("105")), clock.today())
        val initial = repo.financial.first()
        assertFalse(RateStatus.from(initial).estimated)
        val foreignAccount = Account("eur", "Euro", Currency.EUR, AccountKind.SPENDING)
        assertTrue(RateStatus.from(initial.copy(accounts = listOf(foreignAccount))).estimated)
        assertFalse(RateStatus.from(initial.copy(accounts = listOf(foreignAccount.copy(currency = Currency.RSD)))).estimated)
        assertFalse(RateStatus.from(initial.copy(day = initial.day.plusDays(6))).stale)
        val weekLater = initial.copy(day = initial.day.plusDays(7))
        assertTrue(RateStatus.from(weekLater).stale)
        val oneOverride = initial.exchange.copy(overrides = mapOf(Currency.EUR to BigDecimal("118")))
        assertTrue(RateStatus.from(weekLater.copy(exchange = oneOverride)).stale)
        val bothOverrides = oneOverride.copy(overrides = oneOverride.overrides + (Currency.USD to BigDecimal("106")))
        assertFalse(RateStatus.from(weekLater.copy(exchange = bothOverrides)).stale)
    }

    @Test fun `all four tabs react and every emission uses one rate including historical months`() = runTest(dispatcher) {
        repo.setUp("Family", listOf(Account("spend", "Spending", Currency.EUR, AccountKind.SPENDING), Account("save", "Savings", Currency.EUR, AccountKind.SAVINGS)))
        suspend fun add(id: String, type: TransactionType, amount: String, account: String, previous: Boolean = false) {
            repo.add(Transaction(id, type, Money.of(amount, Currency.EUR), account, null, "cat-groceries",
                if (previous) clock.today().minusMonths(1) else clock.today(), "", "", "member", TransactionSource.MANUAL))
        }
        add("income", TransactionType.INCOME, "1000", "spend")
        add("savings", TransactionType.INCOME, "100", "save")
        add("expense", TransactionType.EXPENSE, "10", "spend")
        add("old", TransactionType.EXPENSE, "3", "spend", true)
        repo.setBudget(Budget("cat-groceries", YearMonth.from(clock.today()), Money.of("100", Currency.EUR)))
        repo.setRule(RecurringRule("bill", "Bill", Money.of("20", Currency.EUR), "spend", "cat-bills", Cadence.MONTHLY, clock.today().plusDays(5)))
        repo.setGoal(Goal("goal", "Goal", Money.of("100", Currency.EUR), Money.of("5", Currency.EUR)))
        val original = db.txQueries.all().executeAsList()
        val home = HomeViewModel(repo, clock)
        val budgets = BudgetsViewModel(repo, clock)
        val goals = GoalsViewModel(repo, clock)
        val insights = InsightsViewModel(repo, clock)
        fun <T : Any> observe(state: StateFlow<T?>, verify: (T) -> Unit): Channel<T> {
            val channel = Channel<T>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                state.filterNotNull().collect { verify(it); channel.send(it) }
            }
            return channel
        }
        fun expected(amount: String, status: RateStatus) = Money.of(BigDecimal(amount).multiply(status.snapshot.rates.rate(Currency.EUR) ?: BigDecimal.ZERO), Currency.RSD)
        val homes = observe(home.state) { s ->
            assertEquals(expected("872", s.rateStatus), s.summary.safeToSpend)
            assertEquals(expected("10", s.rateStatus), s.budgetSpent)
            assertEquals(expected("100", s.rateStatus), s.budgetTotal)
        }
        val budgetStates = observe(budgets.state) { s ->
            assertEquals(expected("10", s.rateStatus), s.spent)
            assertEquals(expected("100", s.rateStatus), s.total)
            assertEquals(expected("20", s.rateStatus), s.upcomingTotal)
        }
        val goalStates = observe(goals.state) { s ->
            assertEquals(expected("100", s.rateStatus), s.savingsTotal)
            assertEquals(expected("5", s.rateStatus), s.needs)
        }
        val insightStates = observe(insights.state) { s ->
            assertEquals(expected(if (s.summary.month == YearMonth.from(clock.today())) "10" else "3", s.rateStatus), s.summary.spent)
        }
        assertTrue(homes.receive().summary.partial)
        assertEquals(setOf(Currency.EUR), budgetStates.receive().rateStatus.missing)
        goalStates.receive()
        insightStates.receive()
        suspend fun <T> awaitRate(channel: Channel<T>, rate: BigDecimal, status: (T) -> RateStatus) {
            do { val value = channel.receive() } while (status(value).snapshot.rates.rate(Currency.EUR)?.compareTo(rate) != 0)
        }
        rates.cache(mapOf(Currency.EUR to BigDecimal("117"), Currency.USD to BigDecimal("105")), clock.today())
        for (rate in listOf("117", "120", "119", "117")) {
            when (rate) { "120", "119" -> rates.setOverride(Currency.EUR, rate); "117" -> rates.setOverride(Currency.EUR, null) }
            val decimal = rate.toBigDecimal()
            awaitRate(homes, decimal) { it.rateStatus }
            awaitRate(budgetStates, decimal) { it.rateStatus }
            awaitRate(goalStates, decimal) { it.rateStatus }
            awaitRate(insightStates, decimal) { it.rateStatus }
        }
        insights.previousMonth()
        insights.state.first { it?.summary?.month == YearMonth.from(clock.today()).minusMonths(1) }
        rates.setOverride(Currency.EUR, "125")
        val historical = insights.state.first { it?.rateStatus?.snapshot?.rates?.rate(Currency.EUR) == BigDecimal("125") }!!
        assertEquals(Money.of("375", Currency.RSD), historical.summary.spent)
        assertEquals(original, db.txQueries.all().executeAsList())
    }

    @Test fun `failed manual write retains draft and double tap writes once on retry`() = runTest(dispatcher) {
        val attempts = object : RateAttempts { override var lastAttempt: Instant? = null }
        val refresh = RateRefresh(rates, RateLookup { error("offline") }, attempts, clock)
        val vm = ExchangeRatesViewModel(rates, refresh)
        vm.setInput(Currency.EUR, "117,12345678")
        driver.execute(null, "CREATE TRIGGER fail_rate BEFORE INSERT ON exchange_rate_override BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        vm.save(Currency.EUR)
        vm.writes.state.first { it.failed }
        assertEquals("117,12345678", vm.inputs.value[Currency.EUR])
        assertEquals(0L, db.eventQueries.count().executeAsOne())
        driver.execute(null, "DROP TRIGGER fail_rate", 0)
        vm.save(Currency.EUR)
        vm.save(Currency.EUR)
        vm.writes.state.first { !it.busy && !it.failed }
        assertNull(vm.inputs.value[Currency.EUR])
        assertEquals(1L, db.eventQueries.count().executeAsOne())
        assertEquals(BigDecimal("117.12345678"), rates.snapshots.first().rates.rate(Currency.EUR))
        vm.reset(Currency.EUR)
        vm.writes.state.first { !it.busy && !it.failed }
        assertNull(rates.snapshots.first().rates.rate(Currency.EUR))
    }
}
