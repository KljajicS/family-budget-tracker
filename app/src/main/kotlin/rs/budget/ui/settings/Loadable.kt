package rs.budget.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A failing [source] would otherwise crash the app from the sharing coroutine; here it surfaces as [failed] until [retry]. */
@OptIn(ExperimentalCoroutinesApi::class)
class Loadable<T : Any>(scope: CoroutineScope, source: Flow<T>) {
    private val attempt = MutableStateFlow(0)
    private val failure = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failure

    val state: StateFlow<T?> = attempt.flatMapLatest {
        val nullable: Flow<T?> = source
        nullable.onStart { failure.value = false }.catch {
            failure.value = true
            emit(null)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry() = attempt.update { it + 1 }
}
