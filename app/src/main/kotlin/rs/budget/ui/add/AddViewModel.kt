package rs.budget.ui.add

import androidx.annotation.StringRes
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
) {
    val canSave: Boolean get() = amount.value != null && accountId != null && error == null &&
        (type != TransactionType.TRANSFER || toAccountId != null)
}

@HiltViewModel
class AddViewModel @Inject constructor(
    private val repo: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(AddState(date = clock.today()))
    val state: StateFlow<AddState> = _state

    init {
        viewModelScope.launch {
            combine(repo.accounts, repo.categories) { a, c -> a to c }.collect { (accounts, categories) ->
                _state.update { s ->
                    val account = s.accountId ?: accounts.firstOrNull()?.id
                    s.copy(accounts = accounts, categories = categories.sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }, accountId = account,
                        currency = accounts.firstOrNull { it.id == account }?.currency ?: s.currency)
                }
            }
        }
    }

    fun setType(type: TransactionType) = _state.update { validate(it.copy(type = type, toAccountId = null)) }
    fun press(key: Char) = _state.update { it.copy(amount = it.amount.press(key)) }
    fun setCurrency(currency: Currency) = _state.update { it.copy(currency = currency) }
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
                    id = newId(),
                    type = s.type,
                    money = Money.of(amount, s.currency),
                    accountId = s.accountId!!,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                    categoryId = if (s.type == TransactionType.EXPENSE) s.categoryId else null,
                    date = s.date,
                    note = "",
                    merchant = s.merchant.trim(),
                    paidBy = member.id,
                    source = TransactionSource.MANUAL,
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
