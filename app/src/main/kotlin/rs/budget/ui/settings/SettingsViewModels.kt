package rs.budget.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.budget.data.BudgetRepository
import rs.budget.data.StarterCategories
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.newId
import rs.budget.ui.CategoryPalette
import javax.inject.Inject

data class SettingsState(val memberId: String? = null, val name: String = "", val saved: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: BudgetRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            val m = repo.currentMember()
            _state.update { it.copy(memberId = m?.id, name = m?.displayName ?: "") }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name, saved = false) }

    fun saveName() {
        val s = _state.value
        val id = s.memberId ?: return
        if (s.name.isBlank()) return
        viewModelScope.launch {
            repo.renameMember(id, s.name)
            _state.update { it.copy(saved = true) }
        }
    }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(repo: BudgetRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> = repo.categories
        .map { list -> list.sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class EditCategoryState(
    val editing: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val color: String = CategoryPalette.first(),
    val done: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class EditCategoryViewModel @Inject constructor(private val repo: BudgetRepository, savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(EditCategoryState(editing = savedState.get<String>("id") != null, id = savedState.get<String>("id")))
    val state: StateFlow<EditCategoryState> = _state

    init {
        val id = _state.value.id
        if (id != null) viewModelScope.launch {
            repo.categories.first().firstOrNull { it.id == id }?.let { c -> _state.update { it.copy(name = c.name, color = c.color) } }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setColor(color: String) = _state.update { it.copy(color = color) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            if (s.id == null) repo.addCategory(s.name, s.color) else repo.updateCategory(Category(s.id, s.name.trim(), s.color))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repo.archiveCategory(id)
            _state.update { it.copy(done = true) }
        }
    }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(repo: BudgetRepository) : ViewModel() {
    val accounts: StateFlow<List<Account>> = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class EditAccountState(
    val editing: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val currency: Currency = Currency.RSD,
    val kind: AccountKind = AccountKind.SPENDING,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class EditAccountViewModel @Inject constructor(private val repo: BudgetRepository, savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(EditAccountState(editing = savedState.get<String>("id") != null, id = savedState.get<String>("id")))
    val state: StateFlow<EditAccountState> = _state

    init {
        val id = _state.value.id
        if (id != null) viewModelScope.launch {
            repo.accounts.first().firstOrNull { it.id == id }?.let { a -> _state.update { it.copy(name = a.name, currency = a.currency, kind = a.kind) } }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setCurrency(currency: Currency) = _state.update { it.copy(currency = currency) }
    fun setKind(kind: AccountKind) = _state.update { it.copy(kind = kind) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val account = Account(s.id ?: newId(), s.name.trim(), s.currency, s.kind)
            if (s.id == null) repo.addAccount(account) else repo.updateAccount(account)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repo.archiveAccount(id)
            _state.update { it.copy(done = true) }
        }
    }
}
