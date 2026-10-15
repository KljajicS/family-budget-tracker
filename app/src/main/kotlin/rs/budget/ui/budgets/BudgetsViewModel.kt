package rs.budget.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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
import rs.budget.ui.WriteOperation
import rs.budget.ui.parseMoneyInput
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
    val rateStatus: rs.budget.ui.RateStatus = rs.budget.ui.RateStatus(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<BudgetsState?> = combine(repo.financial, repo.categories) { financial, categories ->
        val today = financial.day
        val statuses = financial.budgets
        val bills = financial.bills
        val rates = financial.exchange.rates
        val month = YearMonth.from(today)
        val (spent, total) = BudgetCalculator.total(statuses, rates)
        val upcomingTotal = bills.fold(Money.zero(Currency.RSD)) { acc, b -> acc + (rates.convert(b.rule.money) ?: Money.zero(Currency.RSD)) }
        BudgetsState(month, month.lengthOfMonth() - today.dayOfMonth + 1, statuses, spent, total, categories, bills, upcomingTotal, financial.accounts.associate { it.id to it.name }, rs.budget.ui.RateStatus.from(financial))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class EditState(
    val editing: Boolean = false,
    val categories: List<Category> = emptyList(),
    val taken: Set<String> = emptySet(),
    val categoryId: String? = null,
    val amountText: String = "",
    val currency: Currency = Currency.RSD,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = categoryId != null && parseMoneyInput(amountText, currency)?.let { !it.isZero } == true
}

@HiltViewModel
class EditBudgetViewModel @Inject constructor(
    private val repo: BudgetRepository,
    clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
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
        if (text.length > 64) return
        _state.update { it.copy(amountText = text) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        writes.run {
            repo.setBudget(Budget(s.categoryId!!, month, parseMoneyInput(s.amountText, s.currency)!!))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.categoryId ?: return
        writes.run {
            repo.removeBudget(id, month)
            _state.update { it.copy(done = true) }
        }
    }
}
