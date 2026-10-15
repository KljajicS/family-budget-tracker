package rs.budget.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.StarterCategories
import rs.budget.domain.Bill
import rs.budget.domain.Budget
import rs.budget.domain.BudgetCalculator
import rs.budget.domain.BudgetStatus
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsState(
    val month: YearMonth,
    val daysLeft: Int,
    val statuses: List<BudgetStatus>,
    val spent: Money,
    val total: Money,
    val categories: List<Category>,
    val upcoming: List<Bill> = emptyList(),
    val upcomingTotal: Money = Money.zero(Currency.RSD),
    val accountNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<BudgetsState?> = combine(repo.budgetStatuses, repo.categories, repo.upcomingBills, repo.accounts) { statuses, categories, bills, accounts ->
        val today = clock.today()
        val month = YearMonth.from(today)
        val (spent, total) = BudgetCalculator.total(statuses, repo.rates)
        val upcomingTotal = bills.fold(Money.zero(Currency.RSD)) { acc, b -> acc + (repo.rates.convert(b.rule.money) ?: Money.zero(Currency.RSD)) }
        BudgetsState(month, month.lengthOfMonth() - today.dayOfMonth + 1, statuses, spent, total, categories, bills, upcomingTotal, accounts.associate { it.id to it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** One budget: pick a category (locked when editing), amount, currency. */
data class EditState(
    val editing: Boolean = false,
    val categories: List<Category> = emptyList(),
    val taken: Set<String> = emptySet(),
    val categoryId: String? = null,
    val amountText: String = "",
    val currency: Currency = Currency.RSD,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = categoryId != null && (amountText.toBigDecimalOrNull()?.signum() ?: 0) > 0
}

@HiltViewModel
class EditBudgetViewModel @Inject constructor(
    private val repo: BudgetRepository,
    clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val month = YearMonth.from(clock.today())
    private val _state = MutableStateFlow(EditState(editing = savedState.get<String>("category") != null, categoryId = savedState.get<String>("category")))
    val state: StateFlow<EditState> = _state

    init {
        viewModelScope.launch {
            val categories = repo.categories.first().sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }
            val current = repo.effectiveBudgets(month).first()
            val existing = current.firstOrNull { it.categoryId == _state.value.categoryId }
            _state.update { s ->
                s.copy(
                    categories = categories,
                    taken = current.map { it.categoryId }.toSet(),
                    amountText = existing?.money?.amount?.stripTrailingZeros()?.toPlainString() ?: s.amountText,
                    currency = existing?.money?.currency ?: s.currency,
                )
            }
        }
    }

    fun setCategory(id: String) = _state.update { if (it.editing) it else it.copy(categoryId = id) }
    fun setCurrency(currency: Currency) = _state.update { it.copy(currency = currency) }

    fun setAmount(text: String) {
        if (text.isNotEmpty() && text.toBigDecimalOrNull() == null) return
        _state.update { it.copy(amountText = text) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.setBudget(Budget(s.categoryId!!, month, Money.of(s.amountText.toBigDecimal(), s.currency)))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.categoryId ?: return
        viewModelScope.launch {
            repo.removeBudget(id, month)
            _state.update { it.copy(done = true) }
        }
    }
}
