package rs.budget.ui.add

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.DuplicateReceipt
import rs.budget.data.InvalidReceiptAccount
import rs.budget.data.InvalidReceiptCategory
import rs.budget.data.StarterCategories
import rs.budget.domain.Account
import rs.budget.domain.Bill
import rs.budget.domain.BillCalculator
import rs.budget.domain.billTxId
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.Receipt
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.newId
import java.time.LocalDate
import javax.inject.Inject

data class AddState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: AmountInput = AmountInput(),
    val currency: Currency = Currency.RSD,
    val categoryId: String? = "cat-groceries",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val date: LocalDate,
    val merchant: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
    val saving: Boolean = false,
    @StringRes val saveError: Int? = null,
    val receipt: Receipt? = null,
    val paidByName: String = "",
    val bill: Bill? = null,
) {
    val canSave: Boolean get() = !saving && !saved && amount.value != null && accountId != null && error == null &&
        accounts.any { it.id == accountId && it.currency == currency } &&
        (receipt == null || currency == Currency.RSD) &&
        (type != TransactionType.EXPENSE || categoryId == null || categories.any { it.id == categoryId }) &&
        (type != TransactionType.TRANSFER || toAccountId != null)
}

@HiltViewModel
class AddViewModel @Inject constructor(
    private val repo: BudgetRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(AddState(date = clock.today()))
    val state: StateFlow<AddState> = _state

    init {
        viewModelScope.launch {
            val ruleId = savedState.get<String>("rule")
            val due = savedState.get<String>("due")?.let(LocalDate::parse)
            val rule = ruleId?.let { repo.rule(it) }
            if (rule != null && due != null) {
                val period = BillCalculator.period(rule.cadence, due)
                _state.update {
                    it.copy(
                        type = TransactionType.EXPENSE,
                        amount = AmountInput(rule.money.amount.stripTrailingZeros().toPlainString()),
                        categoryId = rule.categoryId,
                        accountId = rule.accountId,
                        date = clock.today(),
                        merchant = rule.name,
                        bill = Bill(rule, due, period, billTxId(rule.id, period), overdue = due < clock.today()),
                    )
                }
            }
            combine(repo.accounts, repo.categories) { a, c -> a to c }.collect { (accounts, categories) ->
                val memberName = repo.currentMember()?.displayName.orEmpty()
                val sortedCategories = categories.sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }
                _state.update { s ->
                    val account = s.accountId ?: accounts.firstOrNull { s.receipt == null || it.currency == Currency.RSD }?.id
                    s.copy(categoryId = if (s.receipt == null && s.bill == null && s.categoryId != null && categories.none { it.id == s.categoryId }) sortedCategories.firstOrNull()?.id else s.categoryId,
                        paidByName = memberName, accounts = accounts, categories = sortedCategories, accountId = account,
                        currency = accounts.firstOrNull { it.id == account }?.currency ?: s.currency)
                }
            }
        }
    }

    fun setType(type: TransactionType) = _state.update { if (it.bill != null || it.receipt != null) it else validate(it.copy(type = type, toAccountId = null)) }

    fun prefillReceipt(receipt: Receipt, categoryId: String?) = _state.update { s ->
        if (s.receipt?.id == receipt.id) s else s.copy(
            type = TransactionType.EXPENSE, amount = AmountInput(receipt.total.amount.toPlainString()),
            currency = Currency.RSD, categoryId = categoryId,
            accountId = s.accounts.firstOrNull { it.currency == Currency.RSD }?.id, toAccountId = null,
            date = receipt.date, merchant = receipt.merchant, receipt = receipt, bill = null,
            saved = false, saving = false, error = null, saveError = null,
        )
    }

    fun resetReceipt() = _state.update { s ->
        if (s.saving) s else AddState(date = clock.today(), accounts = s.accounts, categories = s.categories, paidByName = s.paidByName)
    }

    fun skip() {
        if (_state.value.saving || _state.value.saved) return
        val bill = _state.value.bill ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                repo.skipBill(bill.rule.id, bill.period)
                _state.update { it.copy(saved = true, saving = false) }
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveError = R.string.add_save_failed) }
            }
        }
    }
    fun press(key: Char) = _state.update { it.copy(amount = it.amount.press(key)) }
    fun setCategory(id: String?) = _state.update { it.copy(categoryId = id) }
    fun setDate(date: LocalDate) = _state.update { if (it.bill != null) it else it.copy(date = date) }
    fun setMerchant(text: String) = _state.update { it.copy(merchant = text) }

    fun setAccount(id: String) = _state.update { s ->
        validate(s.copy(accountId = id, currency = s.accounts.firstOrNull { it.id == id }?.currency ?: s.currency))
    }

    fun setToAccount(id: String) = _state.update { validate(it.copy(toAccountId = id)) }

    fun save() {
        val s = _state.value
        val amount = s.amount.value ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
            val member = requireNotNull(repo.currentMember())
            val transaction = Transaction(
                    id = s.receipt?.transactionId ?: s.bill?.txId ?: newId(),
                    type = s.type,
                    money = Money.of(amount, s.currency),
                    accountId = s.accountId!!,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                    categoryId = if (s.type == TransactionType.EXPENSE) s.categoryId else null,
                    date = if (s.bill != null) clock.today() else s.date,
                    note = "",
                    merchant = s.merchant.trim(),
                    paidBy = member.id,
                    source = when {
                        s.receipt != null -> TransactionSource.RECEIPT
                        s.bill != null -> TransactionSource.RECURRING
                        else -> TransactionSource.MANUAL
                    },
                )
            if (s.receipt != null) repo.addReceipt(transaction, s.receipt) else repo.add(transaction)
            _state.update { it.copy(saved = true, saving = false) }
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is DuplicateReceipt -> R.string.scan_duplicate
                    is InvalidReceiptAccount -> R.string.scan_rsd_account
                    is InvalidReceiptCategory -> R.string.scan_category_changed
                    else -> R.string.add_save_failed
                }
                _state.update { it.copy(saving = false, saveError = message) }
            }
        }
    }

    private fun validate(s: AddState): AddState {
        if (s.type != TransactionType.TRANSFER || s.toAccountId == null) return s.copy(error = null)
        val from = s.accounts.firstOrNull { it.id == s.accountId }
        val to = s.accounts.firstOrNull { it.id == s.toAccountId }
        return s.copy(
            error = when {
                s.accountId == s.toAccountId -> R.string.add_same_account
                from != null && to != null && from.currency != to.currency -> R.string.add_currency_mismatch
                else -> null
            }
        )
    }
}
