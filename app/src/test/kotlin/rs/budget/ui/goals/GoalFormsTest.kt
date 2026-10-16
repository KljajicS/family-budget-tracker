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
import rs.budget.domain.Currency
import rs.budget.domain.Goal
import rs.budget.domain.Money
import java.time.Instant
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
    fun `create needs a name and positive target, edit can change currency, delete archives`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD)))

        val create = EditGoalViewModel(repo, SavedStateHandle())
        assertFalse(create.state.value.canSave)
        create.setName("Trip")
        create.setTarget("0")
        assertFalse(create.state.value.canSave)
        create.setTarget("3000")
        create.setCurrency(Currency.EUR)
        assertTrue(create.state.value.canSave)
        create.save()
        create.state.first { it.done }
        val goal = repo.goals.first().single()
        assertEquals(Money.of("3000", Currency.EUR), goal.target)
        assertEquals(Money.zero(Currency.EUR), goal.monthly)

        val edit = EditGoalViewModel(repo, SavedStateHandle(mapOf("id" to goal.id)))
        edit.state.first { it.name == "Trip" }
        edit.setCurrency(Currency.RSD)
        edit.setTarget("350000")
        edit.save()
        edit.state.first { it.done }
        assertEquals(Money.of("350000", Currency.RSD), repo.goal(goal.id)!!.target)

        val delete = EditGoalViewModel(repo, SavedStateHandle(mapOf("id" to goal.id)))
        delete.state.first { it.name == "Trip" }
        delete.delete()
        delete.state.first { it.done }
        assertEquals(0, repo.goals.first().size)
    }

    @Test
    fun `editing a legacy goal keeps its old contribution in the same currency`() = runTest(dispatcher) {
        repo.setGoal(Goal("g", "Old", Money.of("1000", Currency.EUR), Money.of("50", Currency.EUR)))
        val edit = EditGoalViewModel(repo, SavedStateHandle(mapOf("id" to "g")))
        edit.state.first { it.name == "Old" }
        edit.setName("Renamed")
        edit.save()
        edit.state.first { it.done }
        assertEquals(Money.of("50", Currency.EUR), repo.goal("g")!!.monthly)
    }
}
