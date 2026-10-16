package rs.budget.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import rs.budget.data.BudgetRepository
import rs.budget.domain.Account
import rs.budget.domain.Currency
import rs.budget.domain.newId
import rs.budget.ui.WriteOperation
import javax.inject.Inject

data class SetupAccount(val name: String, val currency: Currency, val enabled: Boolean)

data class SetupState(
    val name: String = "",
    val accounts: List<SetupAccount> = listOf(
        SetupAccount("Cash", Currency.RSD, enabled = true),
        SetupAccount("Card", Currency.RSD, enabled = true),
        SetupAccount("Savings", Currency.RSD, enabled = false),
    ),
    val done: Boolean = false,
) {
    val canFinish: Boolean get() = name.isNotBlank() && accounts.any { it.enabled }
}

@HiltViewModel
class SetupViewModel @Inject constructor(private val repo: BudgetRepository) : ViewModel() {
    val writes = WriteOperation(viewModelScope)
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun toggleAccount(index: Int) = _state.update { s ->
        s.copy(accounts = s.accounts.mapIndexed { i, a -> if (i == index) a.copy(enabled = !a.enabled) else a })
    }

    fun addAccount(name: String, currency: Currency) = _state.update { s ->
        if (name.isBlank()) s else s.copy(accounts = s.accounts + SetupAccount(name.trim(), currency, enabled = true))
    }

    fun finish() {
        val s = _state.value
        if (!s.canFinish) return
        writes.run {
            repo.setUp(
                s.name.trim(),
                s.accounts.filter { it.enabled }.map { Account(newId(), it.name, it.currency) },
            )
            _state.update { it.copy(done = true) }
        }
    }
}
