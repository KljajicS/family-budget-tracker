package rs.budget.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.ui.WriteFeedback
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.ui.settings.ScreenHeader
import rs.budget.ui.theme.ConfirmDialog
import rs.budget.ui.theme.DangerLink
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LoadingOrError
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.SelectPill
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun EditGoalScreen(onClose: () -> Unit, vm: EditGoalViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val write by vm.writes.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.goal_edit_title else R.string.goal_new_title), onClose)
        if (!s.loaded) return@Column LoadingOrError(s.loadFailed, vm::retryLoad)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(stringResource(R.string.goal_name), s.name, vm::setName)

            Text(stringResource(R.string.goal_currency), style = LabelStyle, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { cur -> SelectPill(cur.name, s.currency == cur, { vm.setCurrency(cur) }, style = mono(13)) }
            }

            AmountField(stringResource(R.string.goal_target), s.targetText, s.currency, vm::setTarget)
            Text(stringResource(R.string.goal_fill_hint), style = LabelStyle, color = c.muted)

            if (s.editing) {
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.goal_delete_hint), style = LabelStyle, color = c.muted)
                DangerLink(stringResource(R.string.goal_delete), enabled = !write.busy) { confirming = true }
            }
        }

        WriteFeedback(vm.writes)

        PrimaryButton(stringResource(R.string.goal_save), enabled = s.canSave && !write.busy, onClick = vm::save)
    }

    if (confirming && s.loaded) {
        ConfirmDialog(stringResource(R.string.audit_ui_delete_title, s.name.trim()), stringResource(R.string.goal_delete), write.busy, vm::delete, { confirming = false }) {
            Text(stringResource(R.string.goal_delete_hint), style = sans(14), color = c.text)
            Text(stringResource(R.string.audit_ui_delete_irreversible), style = sans(14), color = c.orange)
            WriteFeedback(vm.writes)
        }
    }
}

@Composable
private fun AmountField(label: String, value: String, currency: Currency, onChange: (String) -> Unit) {
    val c = LocalColors.current
    Text(label, style = LabelStyle, color = c.muted)
    OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = mono(22).copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            Text(currency.name, style = sans(14, FontWeight.Medium), color = c.muted)
        }
    }
}
