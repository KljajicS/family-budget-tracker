package rs.budget.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalLocale
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
import rs.budget.domain.Cadence
import rs.budget.ui.add.AccountBox
import rs.budget.ui.add.DateBox
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onPay: (String, LocalDate) -> Unit,
    vm: RecurringViewModel = hiltViewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.recurring_title), onBack) }
                NewPill(onNew)
            }
        }
        if (rules.isEmpty()) item { Text(stringResource(R.string.recurring_empty), style = BodyStyle, color = c.muted) }
        if (bills.isNotEmpty()) {
            item { Text(stringResource(R.string.bills_upcoming), style = sans(18, FontWeight.SemiBold), color = c.text) }
            items(bills, key = { "due:${it.txId}" }) { bill ->
                val account = accounts.firstOrNull { it.id == bill.rule.accountId }?.name.orEmpty()
                OutlinedBox(
                    Modifier.fillMaxWidth(), radius = 14,
                    padding = PaddingValues(14.dp),
                    onClick = { onPay(bill.rule.id, bill.dueDate) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.rule.name, style = sans(15, FontWeight.Medium), color = c.text)
                            Text(bill.dueDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)), style = CaptionStyle, color = c.muted)
                            Text(stringResource(if (bill.overdue) R.string.bills_overdue else R.string.bills_from, account), style = CaptionStyle, color = if (bill.overdue) c.orange else c.muted)
                        }
                        Text("${formatAmount(bill.rule.money)} ${bill.rule.money.currency}", style = mono(13), color = c.text)
                    }
                }
            }
            item { Text(stringResource(R.string.recurring_title), style = sans(18, FontWeight.SemiBold), color = c.text, modifier = Modifier.padding(top = 12.dp)) }
        }
        items(rules, key = { it.id }) { r ->
            val account = accounts.firstOrNull { it.id == r.accountId }?.name ?: stringResource(R.string.recurring_account_gone)
            val cadence = stringResource(if (r.cadence == Cadence.MONTHLY) R.string.recurring_monthly else R.string.recurring_yearly)
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = { onEdit(r.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = sans(15, FontWeight.Medium), color = c.text)
                        Text("$cadence · $account", style = CaptionStyle, color = c.muted)
                    }
                    Text("${formatAmount(r.money)} ${r.money.currency}", style = mono(13), color = c.text)
                }
            }
        }
    }
}

@Composable
fun EditRecurringScreen(onClose: () -> Unit, vm: EditRecurringViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.recurring_edit_title else R.string.recurring_new_title), onClose)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(stringResource(R.string.recurring_name), s.name, vm::setName)

            Text(stringResource(R.string.recurring_amount), style = LabelStyle, color = c.muted)
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.recurring_amount)
                    BasicTextField(
                        value = s.amountText,
                        onValueChange = vm::setAmount,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = mono(22).copy(color = c.text),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    )
                    Text(s.currency?.name ?: "", style = sans(14, FontWeight.Medium), color = c.muted)
                }
            }

            AccountBox(stringResource(R.string.add_account), s.accounts, s.accountId, vm::setAccount, Modifier.fillMaxWidth())

            Text(stringResource(R.string.budget_category), style = LabelStyle, color = c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.categories.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { cat -> Pill(categoryDisplayName(cat.id, cat.name), s.categoryId == cat.id) { vm.setCategory(cat.id) } }
                        }
                    }
                }
            }

            Text(stringResource(R.string.recurring_cadence), style = LabelStyle, color = c.muted)
            if (s.editing) {
                Text(stringResource(if (s.cadence == Cadence.MONTHLY) R.string.recurring_monthly else R.string.recurring_yearly), color = c.text)
                DateBox(s.firstDue, vm::setFirstDue, Modifier.fillMaxWidth(), label = stringResource(R.string.recurring_first_due), enabled = false)
                Text(stringResource(R.string.recurring_schedule_locked), style = LabelStyle, color = c.muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.recurring_monthly), s.cadence == Cadence.MONTHLY) { vm.setCadence(Cadence.MONTHLY) }
                    Pill(stringResource(R.string.recurring_yearly), s.cadence == Cadence.YEARLY) { vm.setCadence(Cadence.YEARLY) }
                }
                DateBox(s.firstDue, vm::setFirstDue, Modifier.fillMaxWidth(), label = stringResource(R.string.recurring_first_due))
                Text(stringResource(R.string.recurring_first_due_hint), style = LabelStyle, color = c.muted)
            }

            if (s.editing) {
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.recurring_delete_hint), style = LabelStyle, color = c.muted)
                Text(
                    stringResource(R.string.recurring_delete),
                    style = sans(14, FontWeight.Medium), color = c.orange,
                    modifier = Modifier.clickable(role = Role.Button, onClick = vm::delete).padding(vertical = 10.dp),
                )
            }
        }

        WriteFeedback(vm.writes)

        PrimaryButton(stringResource(R.string.recurring_save), enabled = s.canSave && !vm.writes.state.collectAsStateWithLifecycle().value.busy, onClick = vm::save)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.widthIn(min = 64.dp).height(34.dp)
            .background(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = sans(13, FontWeight.Medium), color = if (selected) c.bg else c.muted) }
}
