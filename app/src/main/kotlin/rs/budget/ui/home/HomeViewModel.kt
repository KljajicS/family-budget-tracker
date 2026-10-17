package rs.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val hasOlder: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(repo: BudgetRepository, clock: Clock, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val limit = MutableStateFlow(saved.get<Int>("historyLimit")?.coerceAtLeast(PAGE) ?: PAGE)
    private val attempt = MutableStateFlow(0)
    private val failure = MutableStateFlow(false)
    /** A failed read keeps the last good screen and offers [retry] instead of crashing the app. */
    val failed: StateFlow<Boolean> = failure

    // One extra row tells whether older transactions exist without a separate count query.
    private val recent = limit.flatMapLatest { n -> repo.recent(n + 1).map { rows -> rows.take(n) to (rows.size > n) } }

    val state: StateFlow<HomeState?> = attempt.flatMapLatest {
        combine(
            repo.financial,
            recent,
            flow { emit(repo.currentMember()?.id) },
            repo.members,
        ) { financial, (rows, hasOlder), member, members ->
            HomeState(
                financial.home,
                financial.activeAccounts.mapNotNull { a -> financial.home.balances[a.id]?.let { AccountBalance(a, it) } },
                rows,
                financial.accounts.associate { it.id to it.name },
                member,
                financial.day,
                memberName = members.firstOrNull { it.id == member }?.displayName ?: "",
                rateStatus = RateStatus.from(financial),
                hasOlder = hasOlder,
            )
        }
            .onEach { failure.value = false }
            .catch { failure.value = true }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showOlder() {
        limit.value += PAGE
        saved["historyLimit"] = limit.value
    }

    fun retry() = attempt.update { it + 1 }

    private companion object { const val PAGE = 20 }
}
