package app.kinjar.ui.settings

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
import app.kinjar.data.BudgetRepository
import app.kinjar.data.EventStore
import app.kinjar.data.FixedClock
import app.kinjar.data.Projector
import app.kinjar.data.testDb
import app.kinjar.domain.Account
import app.kinjar.domain.AccountKind
import app.kinjar.domain.Cadence
import app.kinjar.domain.Currency
import app.kinjar.domain.Money
import app.kinjar.domain.RecurringRule
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
        edit.setFirstDue(LocalDate.of(2026, 1, 1))
        edit.save()
        edit.state.first { it.done }
        assertEquals(Cadence.MONTHLY, repo.rules.first().single().cadence)
        assertEquals(rule.firstDue, repo.rules.first().single().firstDue)

        val account = EditAccountViewModel(repo, SavedStateHandle(mapOf("id" to "eur")))
        account.state.first { it.name == "Euro" }
        assertTrue(account.state.value.currencyLocked)

        val delete = EditRecurringViewModel(repo, clock, SavedStateHandle(mapOf("id" to rule.id)))
        delete.state.first { it.name == "Netflix" }
        delete.delete()
        delete.state.first { it.done }
        assertEquals(0, repo.rules.first().size)
    }

    @Test
    fun `amounts that round to zero and rules on an archived account cannot be saved`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING), Account("old", "Old", Currency.RSD, AccountKind.SPENDING)))
        val create = EditRecurringViewModel(repo, clock, SavedStateHandle())
        create.state.first { it.accounts.isNotEmpty() }
        create.setName("Rent")
        create.setAmount("0.001")
        assertFalse(create.state.value.canSave)
        create.setAmount("0.01")
        assertTrue(create.state.value.canSave)

        repo.setRule(RecurringRule("r", "Rent", Money.of("100", Currency.RSD), "old", "cat-bills", Cadence.MONTHLY, LocalDate.of(2026, 10, 20)))
        repo.archiveAccount("old")
        val edit = EditRecurringViewModel(repo, clock, SavedStateHandle(mapOf("id" to "r")))
        edit.state.first { it.name == "Rent" && it.accounts.isNotEmpty() }
        assertFalse(edit.state.value.canSave)
        edit.setAccount("cash")
        assertTrue(edit.state.value.canSave)
    }
}
