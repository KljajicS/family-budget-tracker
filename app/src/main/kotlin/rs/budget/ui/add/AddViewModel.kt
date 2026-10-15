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
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.StarterCategories
import rs.budget.domain.Account
import rs.budget.domain.Bill
import rs.budget.domain.BillCalculator
import rs.budget.domain.billTxId
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
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
    /** Set when the sheet pays one occurrence of a recurring rule: the type is locked and the id is derived. */
    val bill: Bill? = null,
) {
    val canSave: Boolean get() = amount.value != null && accountId != null && error == null &&
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
                        date = due,
                        merchant = rule.name,
                        bill = Bill(rule, due, period, billTxId(rule.id, period), overdue = due < clock.today()),
                    )
                }
            }
            combine(repo.accounts, repo.categories) { a, c -> a to c }.collect { (accounts, categories) ->
                _state.update { s ->
                    val account = s.accountId ?: accounts.firstOrNull()?.id
                    s.copy(accounts = accounts, categories = categories.sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }, accountId = account,
                        currency = accounts.firstOrNull { it.id == account }?.currency ?: s.currency)
                }
            }
        }
    }

    fun setType(type: TransactionType) = _state.update { if (it.bill != null) it else validate(it.copy(type = type, toAccountId = null)) }

    fun skip() {
        val bill = _state.value.bill ?: return
        viewModelScope.launch {
            repo.skipBill(bill.rule.id, bill.period)
            _state.update { it.copy(saved = true) }
        }
    }
    fun press(key: Char) = _state.update { it.copy(amount = it.amount.press(key)) }
    fun setCategory(id: String?) = _state.update { it.copy(categoryId = id) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setMerchant(text: String) = _state.update { it.copy(merchant = text) }

    fun setAccount(id: String) = _state.update { s ->
        validate(s.copy(accountId = id, currency = s.accounts.firstOrNull { it.id == id }?.currency ?: s.currency))
    }

    fun setToAccount(id: String) = _state.update { validate(it.copy(toAccountId = id)) }

    fun save() {
        val s = _state.value
        val amount = s.amount.value ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val member = repo.currentMember() ?: return@launch
            repo.add(
                Transaction(
                    id = s.bill?.txId ?: newId(),
                    type = s.type,
                    money = Money.of(amount, s.currency),
                    accountId = s.accountId!!,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                    categoryId = if (s.type == TransactionType.EXPENSE) s.categoryId else null,
                    date = s.date,
                    note = "",
                    merchant = s.merchant.trim(),
                    paidBy = member.id,
                    source = if (s.bill != null) TransactionSource.RECURRING else TransactionSource.MANUAL,
                )
            )
            _state.update { it.copy(saved = true) }
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
