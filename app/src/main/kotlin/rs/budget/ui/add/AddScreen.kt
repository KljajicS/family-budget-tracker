package rs.budget.ui.add

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Account
import rs.budget.domain.TransactionType
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.theme.BudgetDatePickerDialog
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.RoundIconButton
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AddScreen(onClose: () -> Unit, vm: AddViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.saved) { if (s.saved) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIconButton(Icons.Default.Close, stringResource(R.string.add_close), onClose)
            TypeSwitch(s.type, vm::setType)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.amount.text.ifEmpty { "0" }, style = mono(48, FontWeight.SemiBold, (-0.03).em), color = c.text)
                Text(s.currency.name, style = sans(18, FontWeight.Medium), color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        if (s.type == TransactionType.EXPENSE) CategoryGrid(s.categories, s.categoryId, vm::setCategory)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountBox(stringResource(R.string.add_account), s.accounts, s.accountId, vm::setAccount, Modifier.weight(1f))
            if (s.type == TransactionType.TRANSFER) {
                AccountBox(stringResource(R.string.add_to_account), s.accounts, s.toAccountId, vm::setToAccount, Modifier.weight(1f))
            } else {
                DateBox(s.date, vm::setDate, Modifier.weight(1f))
            }
        }
        s.error?.let { Text(stringResource(it), style = sans(13), color = c.orange) }

        FieldBox(stringResource(R.string.add_merchant), s.merchant, vm::setMerchant)
        }

        Keypad(onKey = vm::press)
        PrimaryButton(stringResource(s.type.saveLabel()), enabled = s.canSave, onClick = vm::save)
    }
}

@Composable
private fun TypeSwitch(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.width(270.dp).background(c.surface2, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TransactionType.entries.forEach { t ->
            val active = t == selected
            Box(
                Modifier.weight(1f).height(36.dp)
                    .background(if (active) (if (c.dark) c.track else c.bg) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab) { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(t.label()), style = sans(14, FontWeight.Medium), color = if (active) c.text else c.muted) }
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<rs.budget.domain.Category>, selectedId: String?, onSelect: (String?) -> Unit) {
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
            val label = categoryDisplayName(cat.id, cat.name)
            OutlinedBox(Modifier.height(62.dp), radius = 14, padding = PaddingValues(0.dp), selected = selectedId == cat.id, onClick = { onSelect(cat.id) }) {
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
private fun AccountBox(label: String, accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    val value = selected?.let { "${it.name} · ${it.currency}" } ?: ""
    Box(modifier) {
        OutlinedBox(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
            radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), onClick = { open = true },
        ) {
            Text(label, style = CaptionStyle, color = c.muted)
            Text(value, style = sans(14, FontWeight.Medium), color = c.text, modifier = Modifier.padding(top = 2.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text("${a.name} · ${a.currency}", style = sans(14)) }, onClick = { onSelect(a.id); open = false })
            }
        }
    }
}

/** Tap opens the Material date picker; the box itself is a button whose label reads "Date, Today, 14 Oct" to screen readers. */
@Composable
private fun DateBox(date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val today = LocalDate.now()
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val value = when (date) {
        today -> stringResource(R.string.home_today) + ", " + formatted
        today.minusDays(1) -> stringResource(R.string.home_yesterday) + ", " + formatted
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
    val label = stringResource(R.string.add_date)
    var open by remember { mutableStateOf(false) }

    OutlinedBox(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), onClick = { open = true },
    ) {
        Text(label, style = CaptionStyle, color = c.muted)
        Text(value, style = sans(14, FontWeight.Medium), color = c.text, modifier = Modifier.padding(top = 2.dp))
    }

    if (open) BudgetDatePickerDialog(date, onDismiss = { open = false }, onPick = { onPick(it); open = false })
}

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
    val c = LocalColors.current
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<")
    val backspace = stringResource(R.string.add_backspace)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val semantics = if (k == "<") Modifier.semantics { contentDescription = backspace } else Modifier
                    Box(
                        Modifier.weight(1f).height(52.dp).background(c.surface2, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onKey(k[0]) }.then(semantics),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (k == "<") "⌫" else k, style = mono(22), color = c.text) }
                }
            }
        }
    }
}

private fun TransactionType.label() = when (this) {
    TransactionType.EXPENSE -> R.string.add_expense
    TransactionType.INCOME -> R.string.add_income
    TransactionType.TRANSFER -> R.string.add_transfer
}

private fun TransactionType.saveLabel() = when (this) {
    TransactionType.EXPENSE -> R.string.add_save_expense
    TransactionType.INCOME -> R.string.add_save_income
    TransactionType.TRANSFER -> R.string.add_save_transfer
}
