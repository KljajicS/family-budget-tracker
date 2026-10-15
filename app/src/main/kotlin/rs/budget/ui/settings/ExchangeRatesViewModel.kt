package rs.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.budget.data.rates.RateRepository
import rs.budget.data.rates.RateRefresh
import rs.budget.domain.Currency
import rs.budget.domain.parseRate
import rs.budget.ui.WriteOperation
import javax.inject.Inject

@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(private val repo: RateRepository, private val refresher: RateRefresh) : ViewModel() {
    val snapshot = repo.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val refreshState = refresher.state
    val writes = WriteOperation(viewModelScope)
    private val drafts = MutableStateFlow<Map<Currency, String>>(emptyMap())
    val inputs = drafts.asStateFlow()

    fun setInput(currency: Currency, text: String) {
        if (text.length <= 16) drafts.update { it + (currency to text) }
    }

    fun save(currency: Currency) {
        val draft = drafts.value[currency] ?: return
        val rate = parseRate(draft.trim().replace(',', '.')) ?: return
        writes.run(finish = false) {
            repo.setOverride(currency, rate.toPlainString())
            drafts.update { if (it[currency] == draft) it - currency else it }
        }
    }

    fun reset(currency: Currency) {
        writes.run(finish = false) { repo.setOverride(currency, null) }
    }

    fun refresh() { viewModelScope.launch { refresher.refresh(manual = true) } }
}
