package app.kinjar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import app.kinjar.data.*
import app.kinjar.domain.*
import app.kinjar.ui.loans.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LoanFormsTest {
    private val day = LocalDate.of(2026, 10, 17)
    private val loan = Loan("l", "Car", Money.of("6800", Currency.EUR), day)
    private val euro = Account("a", "Euro", Currency.EUR)
    private val rsd = Account("r", "Dinars", Currency.RSD)
    private fun eur(value: String) = Money.of(value, Currency.EUR)
    private fun expense(id: String, amount: Money = eur("250")) =
        Transaction(id, TransactionType.EXPENSE, amount, "a", null, null, day, "", "", "m", TransactionSource.MANUAL)
    private val empty = LoanBook(listOf(loan), emptyList(), emptyList(), emptyList())

    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "d", clock, Projector(db)), clock, "d")
    private val accountStore = TransactionAccountStore({ null }, { _, _ -> })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    private val models = mutableListOf<ViewModel>()
    @After fun tearDown() {
        runTest(dispatcher) { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test fun `manual principal distinguishes empty zero invalid and greater than total`() {
        val form = PaymentForm("p", "t", day, amount = "250")
        assertNull(form.payment(loan, euro)!!.principal)
        assertEquals(Money.zero(Currency.EUR), form.copy(principal = "0").payment(loan, euro)!!.principal)
        assertNull(form.copy(principal = "oops").payment(loan, euro))
        assertNull(form.copy(principal = "251").payment(loan, euro))
        assertEquals(Money.of("50", Currency.EUR), form.copy(principal = "200").payment(loan, euro)!!.interest)
        assertNull(form.copy(amount = "0").payment(loan, euro))
    }

    @Test fun `foreign payment needs explicit loan total and same currency ignores redundant total`() {
        val form = PaymentForm("p", "t", day, amount = "29350", principal = "200")
        assertNull(form.payment(loan, rsd))
        assertEquals(Money.of("250", Currency.EUR), form.copy(total = "250").payment(loan, rsd)!!.total)
        assertEquals(Money.of("250", Currency.EUR), form.copy(amount = "250", total = "999").payment(loan, euro)!!.total)
    }

    @Test fun `loan form validates optional installment without inventing one`() {
        assertNull(LoanForm(start = day).value("l"))
        val form = LoanForm(name = "Car", opening = "6800", start = day)
        assertNull(form.value("l")!!.usualPayment)
        assertNull(form.copy(usual = "0").value("l"))
        assertNull(form.copy(usual = "invalid").value("l"))
        assertEquals(Money.of("250", Currency.EUR), form.copy(usual = "250").value("l")!!.usualPayment)
    }

    @Test fun `payment validation names the actionable problem`() {
        val form = PaymentForm("p", "t", day, accountId = "a", amount = "250")
        val accounts = listOf(euro, rsd.copy(archived = true))
        fun issue(f: PaymentForm, book: LoanBook = empty, mode: LoanAction = LoanAction.PAYMENT, target: Loan = loan) =
            validatePayment(book, target, f, mode, accounts, day).issue
        assertEquals(LoanPayment("p", "l", "t", eur("250"), eur("200")), validatePayment(empty, loan, form.copy(principal = "200"), LoanAction.PAYMENT, accounts, day).value)
        assertNull(issue(form.copy(amount = "")))
        assertEquals(LoanIssue.PRINCIPAL_OVER_TOTAL, issue(form.copy(principal = "251")))
        assertEquals(LoanIssue.EXCEEDS_BALANCE, issue(form.copy(principal = "200"), target = loan.copy(opening = eur("100"))))
        assertEquals(LoanIssue.DATE, issue(form.copy(date = day.plusDays(1))))
        assertEquals(LoanIssue.DATE, issue(form.copy(date = day.minusDays(1))))
        assertEquals(LoanIssue.ACCOUNT_UNAVAILABLE, issue(form.copy(accountId = "r")))
        assertEquals(LoanIssue.ARCHIVED, issue(form, target = loan.copy(archived = true)))
        val conflicted = empty.copy(payments = listOf(LoanPayment("x", "l", "gone", eur("250"), eur("200"))))
        assertEquals(LoanIssue.HISTORY_CONFLICT, issue(form.copy(principal = "200"), conflicted))
    }

    @Test fun `retrying a saved draft is not counted twice and archived splits stay editable`() {
        val saved = LoanPayment("p", "l", "t", eur("250"), eur("100"))
        val book = empty.copy(loans = listOf(loan.copy(opening = eur("150"))), payments = listOf(saved), transactions = listOf(expense("t")))
        val target = book.loans.single()
        val retry = PaymentForm("p", "t", day, accountId = "a", amount = "250", principal = "100")
        assertEquals(saved, validatePayment(book, target, retry, LoanAction.PAYMENT, listOf(euro), day).value)
        val split = retry.copy(total = "250", principal = "150", linked = expense("t"))
        assertEquals(eur("150"), validatePayment(book, target.copy(archived = true), split, LoanAction.SPLIT, emptyList(), day).value!!.principal)
        assertEquals(LoanIssue.EXCEEDS_BALANCE, validatePayment(book, target, split.copy(principal = "151"), LoanAction.SPLIT, emptyList(), day).issue)
        assertNull(validatePayment(book, target, split.copy(id = "removed"), LoanAction.SPLIT, emptyList(), day).value)
    }

    @Test fun `adjustment validation explains blocked corrections`() {
        val form = AdjustmentForm("j", "100", "Statement", increase = false)
        val unknown = empty.copy(payments = listOf(LoanPayment("p", "l", "t", eur("250"), null)), transactions = listOf(expense("t")))
        assertEquals(LoanIssue.INCOMPLETE_SPLITS, validateAdjustment(unknown, loan, form, day).issue)
        assertEquals(LoanIssue.EXCEEDS_BALANCE, validateAdjustment(empty, loan.copy(opening = eur("50")), form, day).issue)
        assertEquals(LoanIssue.ARCHIVED, validateAdjustment(empty, loan.copy(archived = true), form, day).issue)
        assertNull(validateAdjustment(empty, loan, form.copy(reason = " "), day).value)
        val saved = LoanAdjustment("j", "l", eur("-100"), day, "Statement")
        assertEquals(saved, validateAdjustment(empty.copy(adjustments = listOf(saved)), loan.copy(opening = eur("100")), form, day).value)
    }

    @Test fun `receipt validation requires a real change into an active account`() {
        val receipt = expense("credit").copy(type = TransactionType.LOAN_RECEIPT)
        val form = PaymentForm("p", "t", day, accountId = "a", amount = "250")
        assertNull(validateReceipt(receipt, loan, form, listOf(euro)).value)
        assertEquals(eur("240"), validateReceipt(receipt, loan, form.copy(amount = "240"), listOf(euro)).value!!.money)
        assertEquals(LoanIssue.ACCOUNT_UNAVAILABLE, validateReceipt(receipt, loan, form.copy(accountId = "r"), listOf(rsd.copy(archived = true))).issue)
        assertEquals(LoanIssue.ARCHIVED, validateReceipt(receipt, loan.copy(archived = true), form, listOf(euro)).issue)
    }

    @Test fun `editor drafts survive process recreation without being overwritten by the stored loan`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        LoanEditorViewModel(repo, clock, saved).also { models += it }.edit(LoanForm(name = "Car", opening = "6800", start = day))
        assertEquals("Car", LoanEditorViewModel(repo, clock, saved).also { models += it }.form.value.name)

        repo.loans.create(loan)
        val editing = SavedStateHandle(mapOf("id" to "l"))
        val first = LoanEditorViewModel(repo, clock, editing).also { models += it }
        first.data.first { it?.loan != null }
        assertEquals("Car", first.form.value.name)
        first.edit(first.form.value.copy(name = "Draft"))
        val restored = LoanEditorViewModel(repo, clock, editing).also { models += it }
        restored.data.first { it?.loan != null }
        assertEquals("Draft", restored.form.value.name)

        val missing = LoanEditorViewModel(repo, clock, SavedStateHandle(mapOf("id" to "nope"))).also { models += it }
        assertNull(missing.data.first { it != null }!!.loan)
        assertFalse(missing.loadFailed.value)
        assertFalse(missing.canSave())
    }

    @Test fun `detail action and drafts survive process recreation`() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("id" to "l"))
        val first = LoanDetailViewModel(repo, clock, saved, accountStore).also { models += it }
        first.data.first { it != null }
        first.begin(LoanAction.ADJUST)
        first.editAdjustment(first.adjustment.value.copy(amount = "10", reason = "Statement"))
        first.editForm(first.form.value.copy(principal = "200"))
        val restored = LoanDetailViewModel(repo, clock, saved, accountStore).also { models += it }
        assertEquals(LoanAction.ADJUST, restored.action.value)
        assertEquals(first.adjustment.value, restored.adjustment.value)
        assertEquals(first.form.value, restored.form.value)
        restored.close()
        assertEquals(LoanAction.NONE, LoanDetailViewModel(repo, clock, saved, accountStore).also { models += it }.action.value)
    }
}
