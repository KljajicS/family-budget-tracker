package rs.budget.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.domain.Currency
import rs.budget.domain.Goal
import rs.budget.domain.GoalProgress
import rs.budget.domain.Money
import rs.budget.domain.newId
import rs.budget.ui.RateStatus
import rs.budget.ui.WriteOperation
import rs.budget.ui.parseMoneyInput
import rs.budget.ui.settings.EditorLoad
import rs.budget.ui.settings.Loadable
import javax.inject.Inject

data class GoalsState(
    val goals: List<GoalProgress>,
    val rateStatus: RateStatus = RateStatus(),
    val reorderFailures: Int = 0,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repo: BudgetRepository, clock: Clock) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
    private val failures = MutableStateFlow(0)

    private val load = Loadable(viewModelScope, combine(repo.financial, failures) { financial, failed ->
        GoalsState(financial.goals, RateStatus.from(financial), failed)
    }.flowOn(Dispatchers.Default))
    val state: StateFlow<GoalsState?> = load.state
    val loadFailed: StateFlow<Boolean> = load.failed

    fun retryLoad() = load.retry()

    /** A drop while the previous order is still saving is undone rather than silently lost. */
    fun reorder(ids: List<String>) {
        if (writes.state.value.busy) {
            failures.update { it + 1 }
            return
        }
        writes.run(finish = false) {
            try {
                repo.reorderGoals(ids)
            } catch (e: Exception) {
                failures.update { it + 1 }
                throw e
            }
        }
    }
}

data class EditGoalState(
    val editing: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val targetText: String = "",
    val currency: Currency = Currency.RSD,
    val legacyMonthly: Money? = null,
    val loaded: Boolean = true,
    val loadFailed: Boolean = false,
    val done: Boolean = false,
) {
    val target: Money? get() = parseMoneyInput(targetText, currency)?.takeIf { !it.isZero && !it.isNegative }
    val canSave: Boolean get() = name.isNotBlank() && target != null && loaded
}

@HiltViewModel
class EditGoalViewModel @Inject constructor(private val repo: BudgetRepository, savedState: SavedStateHandle) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
    private val _state = MutableStateFlow(savedState.get<String>("id").let { id -> EditGoalState(editing = id != null, id = id, loaded = id == null) })
    val state: StateFlow<EditGoalState> = _state

    private val load = EditorLoad(viewModelScope, { _state.update { it.copy(loadFailed = true) } }) {
        val goal = requireNotNull(repo.goal(requireNotNull(_state.value.id)))
        _state.update {
            it.copy(
                name = goal.name,
                targetText = goal.target.amount.stripTrailingZeros().toPlainString(),
                currency = goal.target.currency,
                legacyMonthly = goal.monthly,
                loaded = true,
                loadFailed = false,
            )
        }
    }

    init { retryLoad() }

    fun retryLoad() { if (!_state.value.loaded) load.start() }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setCurrency(currency: Currency) = _state.update { it.copy(currency = currency) }

    fun setTarget(text: String) {
        if (text.length > 64) return
        _state.update { it.copy(targetText = text) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val target = s.target!!
        writes.run {
            repo.setGoal(Goal(s.id ?: newId(), s.name.trim(), target, s.legacyMonthly?.takeIf { it.currency == s.currency } ?: Money.zero(s.currency)))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        if (!_state.value.loaded) return
        val id = _state.value.id ?: return
        writes.run {
            repo.archiveGoal(id)
            _state.update { it.copy(done = true) }
        }
    }
}
