package rs.budget.ui.budgets

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
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<BudgetsState?> = combine(repo.budgetStatuses, repo.categories) { statuses, categories ->
        val today = clock.today()
        val month = YearMonth.from(today)
        val (spent, total) = BudgetCalculator.total(statuses)
        BudgetsState(month, month.lengthOfMonth() - today.dayOfMonth + 1, statuses, spent, total, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class EditRow(val category: Category, val amountText: String)
data class EditState(val rows: List<EditRow> = emptyList(), val saved: Boolean = false)

@HiltViewModel
class EditBudgetsViewModel @Inject constructor(private val repo: BudgetRepository, clock: Clock) : ViewModel() {
    private val month = YearMonth.from(clock.today())
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    init {
        viewModelScope.launch {
            val categories = repo.categories.first().sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }
            val current = repo.effectiveBudgets(month).first().associateBy { it.categoryId }
            _state.update { s ->
                s.copy(rows = categories.map { c -> EditRow(c, current[c.id]?.money?.amount?.stripTrailingZeros()?.toPlainString() ?: "") })
            }
        }
    }

    fun setAmount(categoryId: String, text: String) {
        if (text.isNotEmpty() && text.toBigDecimalOrNull() == null) return
        _state.update { s -> s.copy(rows = s.rows.map { if (it.category.id == categoryId) it.copy(amountText = text) else it }) }
    }

    /** Empty fields are left alone so carried-forward amounts survive; an explicit 0 clears a budget. */
    fun save() {
        val amounts = _state.value.rows
            .mapNotNull { r -> r.amountText.toBigDecimalOrNull()?.let { r.category.id to Money.of(it, Currency.RSD) } }
            .toMap()
        viewModelScope.launch {
            repo.setBudgets(month, amounts)
            _state.update { it.copy(saved = true) }
        }
    }
}
