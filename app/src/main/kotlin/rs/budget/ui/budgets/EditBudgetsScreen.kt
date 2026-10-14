package rs.budget.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.ui.categoryLabel
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.RoundIconButton
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun EditBudgetsScreen(onClose: () -> Unit, vm: EditBudgetsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.saved) { if (s.saved) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Icons.Default.Close, stringResource(R.string.add_close), onClose)
            Text(stringResource(R.string.budgets_edit_title), style = TitleStyle, color = c.text)
        }
        Text(stringResource(R.string.budgets_edit_hint), style = LabelStyle, color = c.muted)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.rows.forEach { row ->
                val label = categoryLabel(row.category.id)?.let { stringResource(it) } ?: row.category.name
                OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = sans(15), color = c.text, modifier = Modifier.weight(1f))
                        BasicTextField(
                            value = row.amountText,
                            onValueChange = { vm.setAmount(row.category.id, it) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = mono(15).copy(color = c.text, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier.width(120.dp).semantics { contentDescription = label },
                        )
                        Text(" RSD", style = sans(13), color = c.muted)
                    }
                }
            }
        }
        PrimaryButton(stringResource(R.string.budgets_save), onClick = vm::save)
    }
}
