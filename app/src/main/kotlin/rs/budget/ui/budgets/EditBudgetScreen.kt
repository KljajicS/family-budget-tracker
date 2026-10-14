package rs.budget.ui.budgets

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
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
import rs.budget.R
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.ui.categoryLabel
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.RoundIconButton
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun EditBudgetScreen(onClose: () -> Unit, vm: EditBudgetViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Icons.Default.Close, stringResource(R.string.add_close), onClose)
            Text(stringResource(if (s.editing) R.string.budget_edit_title else R.string.budget_new_title), style = TitleStyle, color = c.text)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.budget_category), style = LabelStyle, color = c.muted)
            CategoryPicker(s.categories, s.categoryId, s.taken, s.editing, vm::setCategory)

            Text(stringResource(R.string.budget_amount), style = LabelStyle, color = c.muted)
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.budget_amount)
                    BasicTextField(
                        value = s.amountText,
                        onValueChange = vm::setAmount,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = mono(22).copy(color = c.text),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    )
                    Text(s.currency.name, style = sans(14, FontWeight.Medium), color = c.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { cur -> CurrencyPill(cur, s.currency == cur) { vm.setCurrency(cur) } }
            }
            if (s.editing) {
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.budget_delete),
                    style = sans(14, FontWeight.Medium), color = c.orange,
                    modifier = Modifier.clickable(role = Role.Button, onClick = vm::delete).padding(vertical = 10.dp),
                )
            }
        }

        PrimaryButton(stringResource(R.string.budget_save), enabled = s.canSave, onClick = vm::save)
    }
}

@Composable
private fun CategoryPicker(categories: List<Category>, selectedId: String?, taken: Set<String>, locked: Boolean, onSelect: (String) -> Unit) {
    val c = LocalColors.current
    val rows = (categories.size + 3) / 4
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = Modifier.height((rows * 62 + (rows - 1) * 8).dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            val label = categoryLabel(cat.id)?.let { stringResource(it) } ?: cat.name
            val selected = selectedId == cat.id
            // A category that already has a budget cannot get a second one; tap its row on the list to edit it instead.
            val disabled = (locked && !selected) || (!selected && cat.id in taken)
            OutlinedBox(
                Modifier.height(62.dp).alpha(if (disabled) 0.4f else 1f), radius = 14, padding = PaddingValues(0.dp),
                selected = selected, onClick = if (disabled) null else ({ onSelect(cat.id) }),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(10.dp).background(colorOf(cat.color), CircleShape))
                    Spacer(Modifier.size(6.dp))
                    Text(label, style = sans(12, FontWeight.Medium), color = c.text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CurrencyPill(currency: Currency, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.widthIn(min = 64.dp).height(34.dp)
            .background(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(currency.name, style = mono(13), color = if (selected) c.bg else c.muted) }
}
