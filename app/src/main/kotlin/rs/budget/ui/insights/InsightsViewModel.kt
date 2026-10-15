package rs.budget.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.domain.Category
import rs.budget.domain.InsightsCalculator
import rs.budget.domain.InsightsSummary
import java.time.YearMonth
import javax.inject.Inject

data class InsightsState(
    val summary: InsightsSummary,
    val categories: Map<String, Category>,
    val hasData: Boolean,
    val rateStatus: rs.budget.ui.RateStatus = rs.budget.ui.RateStatus(),
) {
    val canGoBack: Boolean get() = summary.month > summary.range.start
    val canGoForward: Boolean get() = summary.month < summary.range.endInclusive
}

@HiltViewModel
class InsightsViewModel @Inject constructor(repo: BudgetRepository, private val clock: Clock) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock.today()))

    val state: StateFlow<InsightsState?> = combine(
        month, repo.financial, repo.allCategories,
    ) { m, financial, categories ->
        InsightsState(
            InsightsCalculator.summarize(financial.day, m, financial.transactions, financial.accounts, financial.budgets, financial.exchange.rates),
            categories.associateBy { it.id },
            hasData = financial.transactions.isNotEmpty(),
            rateStatus = rs.budget.ui.RateStatus.from(financial),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = step(-1)
    fun nextMonth() = step(1)

    private fun step(months: Long) {
        val range = state.value?.summary?.range ?: return
        month.update { it.plusMonths(months).coerceIn(range.start, range.endInclusive) }
    }
}
