package rs.budget.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun EditGoalScreen(onClose: () -> Unit, vm: EditGoalViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.goal_edit_title else R.string.goal_new_title), onClose)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(stringResource(R.string.goal_name), s.name, vm::setName)

            Text(stringResource(R.string.goal_currency), style = LabelStyle, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { cur -> CurrencyPill(cur.name, s.currency == cur) { vm.setCurrency(cur) } }
            }

            AmountField(stringResource(R.string.goal_target), s.targetText, s.currency, vm::setTarget)
            Text(stringResource(R.string.goal_fill_hint), style = LabelStyle, color = c.muted)

            if (s.editing) {
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.goal_delete_hint), style = LabelStyle, color = c.muted)
                Text(
                    stringResource(R.string.goal_delete),
                    style = sans(14, FontWeight.Medium), color = c.orange,
                    modifier = Modifier.clickable(role = Role.Button, onClick = vm::delete).padding(vertical = 10.dp),
                )
            }
        }

        WriteFeedback(vm.writes)

        PrimaryButton(stringResource(R.string.goal_save), enabled = s.canSave && !vm.writes.state.collectAsStateWithLifecycle().value.busy, onClick = vm::save)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = mono(22).copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            Text(currency.name, style = sans(14, FontWeight.Medium), color = c.muted)
        }
    }
}

@Composable
private fun CurrencyPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.widthIn(min = 64.dp).height(34.dp)
            .background(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = mono(13), color = if (selected) c.bg else c.muted) }
}
