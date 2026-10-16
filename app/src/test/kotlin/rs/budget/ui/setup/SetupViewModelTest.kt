package rs.budget.ui.setup

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
import rs.budget.domain.Currency
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade")), Projector(db)), FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade")), "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `cannot finish without a name, finishing creates enabled accounts`() = runTest(dispatcher) {
        val vm = SetupViewModel(repo)
        assertFalse(vm.state.value.canFinish)
        vm.setName("Ana")
        assertTrue(vm.state.value.canFinish)
        vm.addAccount("EUR cash", Currency.EUR)
        vm.finish()
        vm.state.first { it.done }
        assertEquals(listOf("Cash", "Card", "EUR cash"), repo.accounts.first().map { it.name })
        assertEquals("Ana", repo.currentMember()!!.displayName)
    }
}
