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
import rs.budget.domain.Account
import rs.budget.domain.HomeSummary
import rs.budget.domain.Money
import rs.budget.ui.RateStatus
import java.time.LocalDate
import javax.inject.Inject

data class AccountBalance(val account: Account, val balance: Money)

data class HomeState(
    val summary: HomeSummary,
    val accounts: List<AccountBalance>,
    val recent: List<RecentTx>,
    val accountNames: Map<String, String>,
    val currentMemberId: String?,
    val today: LocalDate,
    val memberName: String = "",
    val rateStatus: RateStatus = RateStatus(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState?> = combine(
        repo.financial,
        repo.recent(20),
        flow { emit(repo.currentMember()?.id) },
        repo.members,
    ) { financial, recent, member, members ->
        HomeState(
            financial.home,
            financial.activeAccounts.mapNotNull { a -> financial.home.balances[a.id]?.let { AccountBalance(a, it) } },
            recent,
            financial.accounts.associate { it.id to it.name },
            member,
            financial.day,
            memberName = members.firstOrNull { it.id == member }?.displayName ?: "",
            rateStatus = RateStatus.from(financial),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
