package rs.budget.ui.goals

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class GoalFormsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create validates, detail funds and removes, currency locks, delete archives`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))

        val create = EditGoalViewModel(repo, SavedStateHandle())
        assertFalse(create.state.value.canSave)
        create.setName("Trip")
        create.setTarget("0")
        assertFalse(create.state.value.canSave)
        create.setTarget("3000")
        create.setMonthly("-5")
        assertFalse(create.state.value.canSave)
        create.setMonthly("150")
        create.setCurrency(Currency.EUR)
        assertTrue(create.state.value.canSave)
        create.save()
        create.state.first { it.done }
        val goal = repo.goals.first().single()
        assertEquals(Money.of("3000", Currency.EUR), goal.target)
        assertEquals(Money.of("150", Currency.EUR), goal.monthly)

        val detail = GoalDetailViewModel(repo, clock, SavedStateHandle(mapOf("id" to goal.id)))
        detail.state.first { it.status != null }
        assertEquals(LocalDate.of(2026, 10, 14), detail.state.value.date)
        assertFalse(detail.state.value.canAdd)
        detail.setAmount("200")
        detail.add()
        detail.state.first { it.allocations.size == 1 }
        assertEquals(Money.of("200", Currency.EUR), detail.state.value.allocations.single().money)
        assertEquals("", detail.state.value.amountText)
        detail.setAmount("100")
        detail.add()
        val two = detail.state.first { it.allocations.size == 2 && it.status?.allocated == Money.of("300", Currency.EUR) }
        detail.deleteAllocation(two.allocations.first().id)
        detail.state.first { it.allocations.size == 1 }

        val edit = EditGoalViewModel(repo, SavedStateHandle(mapOf("id" to goal.id)))
        edit.state.first { it.name == "Trip" }
        assertTrue(edit.state.value.currencyLocked)
        edit.setCurrency(Currency.RSD)
        assertEquals(Currency.EUR, edit.state.value.currency)
        edit.delete()
        edit.state.first { it.done }
        assertEquals(0, repo.goals.first().size)
        detail.state.first { it.gone }
    }
}
