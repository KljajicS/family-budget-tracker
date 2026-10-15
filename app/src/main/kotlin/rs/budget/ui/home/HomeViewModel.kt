package rs.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.RecentTx
import rs.budget.domain.BudgetCalculator
import rs.budget.domain.BudgetStatus
import rs.budget.domain.GoalStatus
import rs.budget.domain.HomeSummary
import rs.budget.domain.Money
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val summary: HomeSummary,
    val recent: List<RecentTx>,
    val currentMemberId: String?,
    val today: LocalDate,
    val memberName: String = "",
    val budgets: List<BudgetStatus> = emptyList(),
    val budgetSpent: Money? = null,
    val budgetTotal: Money? = null,
    val pace: Float = 0f,
    val goals: List<GoalStatus> = emptyList(),
    val rateStatus: rs.budget.ui.RateStatus = rs.budget.ui.RateStatus(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState?> = combine(
        repo.financial,
        repo.recent(20),
        flow { emit(repo.currentMember()?.id) },
        repo.members,
    ) { financial, recent, member, members ->
        val statuses = financial.budgets
        val (spent, total) = BudgetCalculator.total(statuses, financial.exchange.rates)
        HomeState(
            financial.home, recent, member, financial.day,
            memberName = members.firstOrNull { it.id == member }?.displayName ?: "",
            budgets = statuses.take(3),
            budgetSpent = spent.takeIf { statuses.isNotEmpty() },
            budgetTotal = total.takeIf { statuses.isNotEmpty() },
            pace = statuses.firstOrNull()?.pace?.toFloat() ?: 0f,
            goals = financial.goals.take(3),
            rateStatus = rs.budget.ui.RateStatus.from(financial),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
