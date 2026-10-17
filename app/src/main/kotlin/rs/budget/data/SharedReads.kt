package rs.budget.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.shareIn

private data class ReadAttempt<T>(val attempt: Int, val result: Result<T>)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.shareReads(scope: CoroutineScope): Flow<T> {
    val source = this
    val attempts = MutableStateFlow(0)
    val shared = attempts.flatMapLatest { attempt ->
        source.map { ReadAttempt(attempt, Result.success(it)) }
            .catch { emit(ReadAttempt(attempt, Result.failure(it))) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)
    return flow {
        // A retry must restart the read instead of immediately replaying its cached failure.
        val attempt = if (shared.replayCache.lastOrNull()?.result?.isFailure == true) {
            attempts.updateAndGet { it + 1 }
        } else attempts.value
        emitAll(shared.dropWhile { it.attempt < attempt }.map { it.result.getOrThrow() })
    }
}
