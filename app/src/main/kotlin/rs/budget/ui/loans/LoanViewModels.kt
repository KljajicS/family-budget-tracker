package rs.budget.ui.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.LoanBook
import rs.budget.data.TransactionAccountStore
import rs.budget.domain.*
import rs.budget.ui.WriteOperation
import rs.budget.ui.parseMoneyInput
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LoanListViewModel @Inject constructor(repo: BudgetRepository, val clock: Clock) : ViewModel() {
    val book = repo.loans.book.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class LoanForm(
    val name: String = "",
    val opening: String = "",
    val currency: Currency = Currency.EUR,
    val start: LocalDate,
    val usual: String = "",
    val due: LocalDate? = null,
    val receiving: Boolean = false,
    val accountId: String? = null,
    val deposit: String = "",
) {
    fun value(id: String): Loan? {
        val openingMoney = parseMoneyInput(opening, currency) ?: return null
        if (name.isBlank()) return null
        val usualMoney = if (usual.isBlank()) null else parseMoneyInput(usual, currency)?.takeUnless { it.isZero } ?: return null
        return Loan(id, name.trim(), openingMoney, start, usualMoney, due)
    }
}

@HiltViewModel
class LoanEditorViewModel @Inject constructor(
    private val repo: BudgetRepository, val clock: Clock, saved: SavedStateHandle,
) : ViewModel() {
    val editingId: String? = saved["id"]
    private val id = editingId ?: saved.get<String>("draftId") ?: newId().also { saved["draftId"] = it }
    private val receiptId = saved.get<String>("receiptId") ?: newId().also { saved["receiptId"] = it }
    val writes = WriteOperation(viewModelScope)
    val form = MutableStateFlow(LoanForm(start = clock.today()))
    val loaded = MutableStateFlow(editingId == null)
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var original: Loan? = null

    init {
        if (editingId != null) viewModelScope.launch {
            val loan = repo.loans.book.first().loans.find { it.id == editingId } ?: return@launch
            original = loan
            form.value = LoanForm(loan.name, loan.opening.amount.toPlainString(), loan.opening.currency, loan.start,
                loan.usualPayment?.amount?.toPlainString().orEmpty(), loan.nextDue)
            loaded.value = true
        }
    }

    fun canSave(): Boolean {
        val s = form.value
        val loan = s.value(id) ?: return false
        if (!loaded.value || s.start > clock.today() || original?.archived == true) return false
        if (!s.receiving) return true
        val account = accounts.value.find { it.id == s.accountId } ?: return false
        return !loan.opening.isZero && parseMoneyInput(s.deposit, account.currency)?.let { !it.isZero } == true
    }

    fun save() {
        if (!canSave()) return
        val s = form.value
        val value = s.value(id) ?: return
        writes.run {
            if (editingId != null) repo.loans.update(value) else {
                val receipt = if (!s.receiving) null else {
                    val account = accounts.value.first { it.id == s.accountId }
                    Transaction(receiptId, TransactionType.LOAN_RECEIPT, requireNotNull(parseMoneyInput(s.deposit, account.currency)),
                        account.id, null, null, s.start, "", s.name.trim(), requireNotNull(repo.currentMember()).id, TransactionSource.MANUAL)
                }
                repo.loans.create(value, receipt)
            }
        }
    }
}

enum class LoanAction { NONE, PAYMENT, LINK, SPLIT, ADJUST }

data class PaymentForm(
    val id: String,
    val txId: String,
    val date: LocalDate,
    val accountId: String? = null,
    val amount: String = "",
    val total: String = "",
    val principal: String = "",
    val categoryId: String? = null,
    val linked: Transaction? = null,
) {
    fun payment(loan: Loan, account: Account?): LoanPayment? {
        val accountMoney = linked?.money ?: account?.let { parseMoneyInput(amount, it.currency) } ?: return null
        if (accountMoney.isZero) return null
        val totalMoney = if (accountMoney.currency == loan.opening.currency) accountMoney
            else parseMoneyInput(total, loan.opening.currency)?.takeUnless { it.isZero } ?: return null
        val principalMoney = if (principal.isBlank()) null else parseMoneyInput(principal, loan.opening.currency) ?: return null
        if (principalMoney != null && principalMoney.amount > totalMoney.amount) return null
        return LoanPayment(id, loan.id, linked?.id ?: txId, totalMoney, principalMoney)
    }
}

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val repo: BudgetRepository, val clock: Clock, saved: SavedStateHandle,
    private val accountStore: TransactionAccountStore,
) : ViewModel() {
    val id: String = requireNotNull(saved["id"])
    val writes = WriteOperation(viewModelScope)
    val book = repo.loans.book.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts = repo.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val action = MutableStateFlow(LoanAction.NONE)
    val form = MutableStateFlow(newPayment())
    val adjustmentAmount = MutableStateFlow("")
    val adjustmentReason = MutableStateFlow("")
    val adjustmentIncrease = MutableStateFlow(false)
    private var adjustmentId = newId()

    private fun newPayment() = PaymentForm(newId(), newId(), clock.today())
    fun loan(): Loan? = book.value?.loans?.find { it.id == id }

    fun begin(mode: LoanAction) {
        if (writes.state.value.busy) return
        val active = accounts.value.filterNot { it.archived }
        val account = active.find { it.id == accountStore.lastUsed(TransactionType.EXPENSE) } ?: active.firstOrNull()
        val lastCategory = book.value?.let { b ->
            val txIds = b.payments.filter { it.loanId == id }.map { it.transactionId }.toSet()
            b.transactions.filter { it.id in txIds }.maxByOrNull { it.date }?.categoryId
        }?.takeIf { cat -> categories.value.any { it.id == cat } }
        form.value = newPayment().copy(accountId = account?.id, categoryId = lastCategory)
        adjustmentAmount.value = ""
        adjustmentReason.value = ""
        adjustmentIncrease.value = false
        adjustmentId = newId()
        action.value = mode
    }

    fun link(tx: Transaction) {
        form.value = form.value.copy(linked = tx, date = tx.date, accountId = tx.accountId, amount = tx.money.amount.toPlainString())
    }

    fun editSplit(payment: LoanPayment) {
        val tx = book.value?.transactions?.find { it.id == payment.transactionId } ?: return
        form.value = PaymentForm(payment.id, tx.id, tx.date, tx.accountId, tx.money.amount.toPlainString(),
            payment.total.amount.toPlainString(), payment.principal?.amount?.toPlainString().orEmpty(), tx.categoryId, tx)
        action.value = LoanAction.SPLIT
    }

    fun payment(): LoanPayment? {
        val loan = loan() ?: return null
        val s = form.value
        if (s.date < loan.start || s.date > clock.today()) return null
        if (action.value == LoanAction.LINK && s.linked == null) return null
        val account = accounts.value.find { it.id == s.accountId }
        if (s.linked == null && (account == null || account.archived)) return null
        return s.payment(loan, account)
    }

    fun savePayment() {
        val payment = payment() ?: return
        val s = form.value
        val mode = action.value
        writes.run(finish = false) {
            if (mode == LoanAction.SPLIT) repo.loans.split(payment.id, payment.principal) else {
                val account = accounts.value.first { it.id == s.accountId }
                val tx = s.linked ?: Transaction(s.txId, TransactionType.EXPENSE, requireNotNull(parseMoneyInput(s.amount, account.currency)),
                    account.id, null, s.categoryId, s.date, "", requireNotNull(loan()).name,
                    requireNotNull(repo.currentMember()).id, TransactionSource.MANUAL)
                repo.loans.record(payment, tx, s.linked != null)
                if (s.linked == null) accountStore.remember(TransactionType.EXPENSE, account.id)
            }
            action.value = LoanAction.NONE
        }
    }

    fun adjustment(): LoanAdjustment? {
        val loan = loan() ?: return null
        val amount = parseMoneyInput(adjustmentAmount.value, loan.opening.currency)?.takeUnless { it.isZero } ?: return null
        if (adjustmentReason.value.isBlank()) return null
        return LoanAdjustment(adjustmentId, id, if (adjustmentIncrease.value) amount else -amount, clock.today(), adjustmentReason.value.trim())
    }

    fun adjust() {
        val value = adjustment() ?: return
        writes.run(finish = false) { repo.loans.adjust(value); action.value = LoanAction.NONE }
    }

    fun remove(payment: LoanPayment, deleteExpense: Boolean) {
        writes.run(finish = false) { repo.loans.removePayment(payment.id, deleteExpense) }
    }

    fun archive() { writes.run { repo.loans.archive(id) } }
}
