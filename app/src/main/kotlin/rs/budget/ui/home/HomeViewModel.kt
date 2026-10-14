package rs.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.RecentTx
import rs.budget.domain.HomeSummary
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val summary: HomeSummary,
    val recent: List<RecentTx>,
    val currentMemberId: String?,
    val today: LocalDate,
)

@HiltViewModel
class HomeViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState?> = combine(
        repo.home,
        repo.recent(20),
        flow { emit(repo.currentMember()?.id) },
    ) { summary, recent, member ->
        HomeState(summary, recent, member, clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
