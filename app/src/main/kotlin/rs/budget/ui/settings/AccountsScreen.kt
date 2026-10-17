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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.ui.DragHandle
import rs.budget.ui.ReorderColumn
import rs.budget.ui.WriteFeedback
import rs.budget.ui.WriteOperation
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.AddButton
import rs.budget.ui.home.accountColor
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun AccountsScreen(onNew: () -> Unit, onEdit: (String) -> Unit, onNewLoan: () -> Unit, onLoan: (String) -> Unit, vm: AccountsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val list = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.accounts_title), style = TitleStyle, color = c.text, modifier = Modifier.weight(1f))
                AddButton(stringResource(R.string.account_new_title), onNew)
            }
        }
        if (s.accounts.isEmpty()) {
            item { Text(stringResource(R.string.accounts_empty), style = BodyStyle, color = c.muted) }
        } else {
            item {
                OutlinedBox(Modifier.fillMaxWidth(), radius = 18, padding = PaddingValues(vertical = 4.dp)) {
                    ReorderColumn(s.accounts, { it.account.id }, s.reorderFailures, vm::reorder, list) { (account, balance), index, handle, actions ->
                        Column(Modifier.background(c.bg)) {
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 44.dp, end = 14.dp), color = c.border, thickness = 1.dp)
                            Row(
                                actions.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onEdit(account.id) }.padding(end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DragHandle(handle)
                                val tint = accountColor(index)
                                Box(Modifier.size(40.dp).background(tint.copy(alpha = c.tintAlpha), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                }
                                Text(account.name, style = sans(15, FontWeight.Medium), color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
                                Text("${formatAmount(balance, locale = locale)} ${balance.currency}", style = mono(14), color = if (balance.isNegative) c.orange else c.text)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                            }
                        }
                    }
                }
            }
            item { WriteFeedback(vm.writes) }
        }
        item { rs.budget.ui.loans.LoansSection(onNewLoan, onLoan) }
    }
}

@Composable
fun EditAccountScreen(onClose: () -> Unit, vm: EditAccountViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val write by vm.writes.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    var confirming by remember { mutableStateOf(false) }
    // The warning must name the balance being dropped, and a busy save would silently swallow the archive.
    val canRemove = s.balance != null && !write.busy
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.account_edit_title else R.string.account_new_title), onClose)
        FieldBox(stringResource(R.string.account_name), s.name, vm::setName)
        Text(stringResource(R.string.account_currency), style = LabelStyle, color = c.muted)
        if (s.currencyLocked) {
            Text(s.currency.name, style = mono(15), color = c.text)
            Text(stringResource(R.string.account_currency_locked), style = LabelStyle, color = c.muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { cur -> Pill(cur.name, s.currency == cur) { vm.setCurrency(cur) } }
            }
        }
        if (s.editing) {
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.account_delete_hint), style = LabelStyle, color = c.muted)
            Text(
                stringResource(R.string.account_delete),
                style = sans(14, FontWeight.Medium), color = if (canRemove) c.orange else c.muted,
                modifier = Modifier.clickable(enabled = canRemove, role = Role.Button) { confirming = true }.padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        WriteFeedback(vm.writes)
        PrimaryButton(stringResource(R.string.account_save), enabled = s.canSave && !write.busy, onClick = vm::save)
    }

    // Stays open until the archive succeeds and closes the screen, so a failure shows here and can be retried.
    val balance = s.balance
    if (confirming && balance != null) {
        ArchiveDialog(s, balance, vm.writes, write.busy, onConfirm = vm::delete, onDismiss = { confirming = false })
    }
}

@Composable
private fun ArchiveDialog(s: EditAccountState, balance: Money, writes: WriteOperation, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val rsd = s.balanceRsd
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = c.bg,
        title = { Text(stringResource(R.string.account_archive_title, s.name), style = sans(18, FontWeight.SemiBold), color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val approx = if (balance.currency != Currency.RSD && rsd != null) " (≈ ${formatAmount(rsd, locale = locale)} RSD)" else ""
                Text(
                    stringResource(R.string.account_archive_balance, "${formatAmount(balance, locale = locale)} ${balance.currency}$approx"),
                    style = mono(14), color = c.text,
                )
                Text(
                    stringResource(
                        when {
                            balance.isZero -> R.string.account_archive_zero
                            balance.isNegative -> R.string.account_archive_rise
                            else -> R.string.account_archive_drop
                        },
                    ),
                    style = sans(14), color = if (!balance.isZero && !balance.isNegative) c.orange else c.text,
                )
                Text(stringResource(R.string.account_archive_history), style = sans(14), color = c.muted)
                WriteFeedback(writes)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(stringResource(R.string.account_archive_confirm), color = if (busy) c.muted else c.orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.dialog_cancel), color = if (busy) c.muted else c.text) }
        },
    )
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
    ) { Text(label, style = mono(13), color = if (selected) c.bg else c.muted) }
}
