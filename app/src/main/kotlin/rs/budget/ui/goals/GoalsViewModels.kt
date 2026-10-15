package rs.budget.ui.goals

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.domain.Currency
import rs.budget.domain.Goal
import rs.budget.domain.GoalAllocation
import rs.budget.domain.GoalCalculator
import rs.budget.domain.GoalStatus
import rs.budget.domain.InsightsCalculator
import rs.budget.domain.Money
import rs.budget.domain.newId
import java.time.LocalDate
import java.time.YearMonth
import rs.budget.ui.WriteOperation
import rs.budget.ui.parseMoneyInput
import javax.inject.Inject

data class GoalsState(
    val statuses: List<GoalStatus>,
    val savingsTotal: Money,
    val savingsByCurrency: Map<Currency, Money>,
    val unassigned: Money,
    val needs: Money,
    val averageSaved: Money,
    val partial: Boolean,
    val rateStatus: rs.budget.ui.RateStatus = rs.budget.ui.RateStatus(),
)

@HiltViewModel
class GoalsViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<GoalsState?> = repo.financial.map { financial ->
        val today = financial.day
        val statuses = financial.goals
        val home = financial.home
        val rates = financial.exchange.rates
        val (unassigned, p1) = GoalCalculator.unassigned(home.savingsTotal, statuses, rates)
        val (needs, p2) = GoalCalculator.needs(statuses, rates)
        val insights = InsightsCalculator.summarize(today, YearMonth.from(today), financial.transactions, financial.accounts, financial.budgets, rates)
        GoalsState(statuses, home.savingsTotal, home.savingsByCurrency, unassigned, needs, insights.averageSaved, home.partial || p1 || p2 || insights.partial, rs.budget.ui.RateStatus.from(financial))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class EditGoalState(
    val editing: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val targetText: String = "",
    val monthlyText: String = "",
    val currency: Currency = Currency.RSD,
    val currencyLocked: Boolean = false,
    val done: Boolean = false,
) {
    val target: Money? get() = parseMoneyInput(targetText, currency)?.takeIf { !it.isZero && !it.isNegative }
    val monthly: Money? get() = parseMoneyInput(if (monthlyText.isBlank()) "0" else monthlyText, currency)?.takeIf { !it.isNegative }
    val canSave: Boolean get() = name.isNotBlank() && target != null && monthly != null
}

@HiltViewModel
class EditGoalViewModel @Inject constructor(private val repo: BudgetRepository, savedState: SavedStateHandle) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
    private val _state = MutableStateFlow(EditGoalState(editing = savedState.get<String>("id") != null, id = savedState.get<String>("id")))
    val state: StateFlow<EditGoalState> = _state

    init {
        val id = _state.value.id
        if (id != null) viewModelScope.launch {
            val goal = repo.goal(id) ?: return@launch
            val funded = repo.allocations.first().any { it.goalId == id }
            _state.update {
                it.copy(
                    name = goal.name,
                    targetText = goal.target.amount.stripTrailingZeros().toPlainString(),
                    monthlyText = goal.monthly.amount.stripTrailingZeros().toPlainString(),
                    currency = goal.target.currency,
                    currencyLocked = funded,
                )
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setCurrency(currency: Currency) = _state.update { if (it.currencyLocked) it else it.copy(currency = currency) }
    fun setTarget(text: String) = setNumber(text) { s, t -> s.copy(targetText = t) }
    fun setMonthly(text: String) = setNumber(text) { s, t -> s.copy(monthlyText = t) }

    private fun setNumber(text: String, apply: (EditGoalState, String) -> EditGoalState) {
        if (text.length > 64) return
        _state.update { apply(it, text) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        writes.run {
            repo.setGoal(Goal(s.id ?: newId(), s.name.trim(), s.target!!, s.monthly!!))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        writes.run {
            repo.archiveGoal(id)
            _state.update { it.copy(done = true) }
        }
    }
}

data class GoalDetailState(
    val status: GoalStatus? = null,
    val allocations: List<GoalAllocation> = emptyList(),
    val amountText: String = "",
    val date: LocalDate,
    val gone: Boolean = false,
) {
    val amount: Money? get() = status?.let { s -> parseMoneyInput(amountText, s.goal.target.currency) }?.takeIf { !it.isZero && !it.isNegative }
    val canAdd: Boolean get() = amount != null
}

@HiltViewModel
class GoalDetailViewModel @Inject constructor(private val repo: BudgetRepository, clock: Clock, savedState: SavedStateHandle) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
    private val goalId: String = savedState.get<String>("id")!!
    private val form = MutableStateFlow(GoalDetailState(date = clock.today()))

    val state: StateFlow<GoalDetailState> = combine(form, repo.goalStatuses, repo.allocations) { f, statuses, allocations ->
        val status = statuses.firstOrNull { it.goal.id == goalId }
        f.copy(status = status, allocations = allocations.filter { it.goalId == goalId }, gone = status == null)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    fun setAmount(text: String) {
        if (text.length > 64) return
        form.update { it.copy(amountText = text) }
    }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun add() {
        val f = form.value
        writes.run(finish = false) {
            val goal = requireNotNull(repo.goal(goalId))
            val amount = requireNotNull(parseMoneyInput(f.amountText, goal.target.currency))
            require(!amount.isZero)
            repo.allocate(GoalAllocation(newId(), goalId, amount, f.date))
            form.update { if (it.amountText == f.amountText) it.copy(amountText = "") else it }
        }
    }

    fun deleteAllocation(id: String) { writes.run(finish = false) { repo.deleteAllocation(id) } }

    fun deleteGoal() { writes.run { repo.archiveGoal(goalId) } }
}
