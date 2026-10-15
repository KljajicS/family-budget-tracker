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
import rs.budget.data.Clock
import rs.budget.data.StarterCategories
import rs.budget.data.ThemeMode
import rs.budget.data.ThemeStore
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Cadence
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.RecurringRule
import rs.budget.domain.newId
import java.time.LocalDate
import rs.budget.ui.CategoryPalette
import javax.inject.Inject

data class SettingsState(val memberId: String? = null, val name: String = "", val saved: Boolean = false, val theme: ThemeMode = ThemeMode.SYSTEM)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: BudgetRepository, private val themeStore: ThemeStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(theme = themeStore.mode.value))
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            val m = repo.currentMember()
            _state.update { it.copy(memberId = m?.id, name = m?.displayName ?: "") }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name, saved = false) }

    fun setTheme(mode: ThemeMode) {
        themeStore.set(mode)
        _state.update { it.copy(theme = mode) }
    }

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
    val currencyLocked: Boolean = false,
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
            val used = repo.transactions.first().any { it.accountId == id || it.toAccountId == id } ||
                repo.rules.first().any { it.accountId == id }
            repo.accounts.first().firstOrNull { it.id == id }?.let { a -> _state.update { it.copy(name = a.name, currency = a.currency, kind = a.kind, currencyLocked = used) } }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setCurrency(currency: Currency) = _state.update { if (it.currencyLocked) it else it.copy(currency = currency) }
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

@HiltViewModel
class RecurringViewModel @Inject constructor(repo: BudgetRepository) : ViewModel() {
    val rules: StateFlow<List<RecurringRule>> = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class EditRecurringState(
    val editing: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val cadence: Cadence = Cadence.MONTHLY,
    val firstDue: LocalDate,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val done: Boolean = false,
) {
    val currency: Currency? get() = accounts.firstOrNull { it.id == accountId }?.currency
    val amount: Money? get() = currency?.let { c -> amountText.toBigDecimalOrNull()?.let { Money.of(it, c) } }?.takeIf { !it.isZero && !it.isNegative }
    val canSave: Boolean get() = name.isNotBlank() && amount != null && categoryId != null
}

@HiltViewModel
class EditRecurringViewModel @Inject constructor(private val repo: BudgetRepository, clock: Clock, savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(EditRecurringState(editing = savedState.get<String>("id") != null, id = savedState.get<String>("id"), firstDue = clock.today()))
    val state: StateFlow<EditRecurringState> = _state

    init {
        viewModelScope.launch {
            val accounts = repo.accounts.first()
            val categories = repo.categories.first().sortedBy { c -> StarterCategories.all.indexOfFirst { it.id == c.id }.let { if (it < 0) Int.MAX_VALUE else it } }
            val existing = _state.value.id?.let { repo.rule(it) }
            _state.update { s ->
                s.copy(
                    accounts = accounts,
                    categories = categories,
                    name = existing?.name ?: s.name,
                    amountText = existing?.money?.amount?.stripTrailingZeros()?.toPlainString() ?: s.amountText,
                    accountId = existing?.accountId ?: accounts.firstOrNull()?.id,
                    categoryId = existing?.categoryId ?: categories.firstOrNull { it.id == "cat-bills" }?.id ?: categories.firstOrNull()?.id,
                    cadence = existing?.cadence ?: s.cadence,
                    firstDue = existing?.firstDue ?: s.firstDue,
                )
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setAccount(id: String) = _state.update { it.copy(accountId = id) }
    fun setCategory(id: String) = _state.update { it.copy(categoryId = id) }
    fun setCadence(cadence: Cadence) = _state.update { it.copy(cadence = cadence) }
    fun setFirstDue(date: LocalDate) = _state.update { it.copy(firstDue = date) }

    fun setAmount(text: String) {
        if (text.isNotEmpty() && text.toBigDecimalOrNull() == null) return
        _state.update { it.copy(amountText = text) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.setRule(RecurringRule(s.id ?: newId(), s.name.trim(), s.amount!!, s.accountId!!, s.categoryId!!, s.cadence, s.firstDue))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repo.archiveRule(id)
            _state.update { it.copy(done = true) }
        }
    }
}
