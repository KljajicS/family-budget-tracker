package rs.budget.ui

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.budget.R
import rs.budget.ui.theme.LocalColors

data class WriteState(val busy: Boolean = false, val failed: Boolean = false, val finished: Boolean = false)

class WriteOperation(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(WriteState())
    val state: StateFlow<WriteState> = mutableState

    fun run(finish: Boolean = true, action: suspend () -> Unit) {
        val current = mutableState.value
        if (current.busy || current.finished) return
        mutableState.value = WriteState(busy = true)
        scope.launch {
            try {
                action()
                mutableState.value = WriteState(finished = finish)
            } catch (e: CancellationException) {
                mutableState.value = WriteState()
                throw e
            } catch (_: Exception) {
                mutableState.value = WriteState(failed = true)
            }
        }
    }
}

@Composable
fun WriteFeedback(operation: WriteOperation) {
    val state by operation.state.collectAsStateWithLifecycle()
    if (state.busy) LinearProgressIndicator()
    if (state.failed) Text(stringResource(R.string.write_failed), color = LocalColors.current.orange)
}
