package rs.budget.ui.settings

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
import rs.budget.domain.Cadence
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create a bill, currency follows the account, edit prefills and delete archives`() = runTest(dispatcher) {
        repo.setUp("S", listOf(
            Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING),
            Account("eur", "Euro", Currency.EUR, AccountKind.SPENDING),
        ))

        val create = EditRecurringViewModel(repo, clock, SavedStateHandle())
        create.state.first { it.accounts.isNotEmpty() }
        assertFalse(create.state.value.canSave)
        assertEquals(LocalDate.of(2026, 10, 14), create.state.value.firstDue)
        assertEquals("cat-bills", create.state.value.categoryId)
        create.setName("Netflix")
        create.setAmount("12.99")
        create.setAccount("eur")
        create.setFirstDue(LocalDate.of(2026, 10, 28))
        assertEquals(Currency.EUR, create.state.value.currency)
        assertTrue(create.state.value.canSave)
        create.save()
        create.state.first { it.done }

        val rule = repo.rules.first().single()
        assertEquals(Money.of("12.99", Currency.EUR), rule.money)
        assertEquals(Cadence.MONTHLY, rule.cadence)

        val edit = EditRecurringViewModel(repo, clock, SavedStateHandle(mapOf("id" to rule.id)))
        edit.state.first { it.name == "Netflix" }
        assertEquals("12.99", edit.state.value.amountText)
        assertEquals("eur", edit.state.value.accountId)
        edit.setCadence(Cadence.YEARLY)
        edit.save()
        edit.state.first { it.done }
        assertEquals(Cadence.YEARLY, repo.rules.first().single().cadence)

        val account = EditAccountViewModel(repo, SavedStateHandle(mapOf("id" to "eur")))
        account.state.first { it.name == "Euro" }
        assertTrue(account.state.value.currencyLocked)

        val delete = EditRecurringViewModel(repo, clock, SavedStateHandle(mapOf("id" to rule.id)))
        delete.state.first { it.name == "Netflix" }
        delete.delete()
        delete.state.first { it.done }
        assertEquals(0, repo.rules.first().size)
    }
}
