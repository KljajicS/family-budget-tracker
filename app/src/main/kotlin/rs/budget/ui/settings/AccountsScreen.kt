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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun AccountsScreen(onBack: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit, vm: AccountsViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.accounts_title), onBack) }
                NewPill(onNew)
            }
        }
        if (accounts.isEmpty()) item { Text(stringResource(R.string.accounts_empty), style = BodyStyle, color = c.muted) }
        items(accounts, key = { it.id }) { a ->
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = { onEdit(a.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = sans(15, FontWeight.Medium), color = c.text)
                        Text(
                            stringResource(if (a.kind == AccountKind.SAVINGS) R.string.account_kind_savings else R.string.account_kind_spending),
                            style = CaptionStyle, color = c.muted,
                        )
                    }
                    Text(a.currency.name, style = mono(13), color = c.muted)
                }
            }
        }
    }
}

@Composable
fun EditAccountScreen(onClose: () -> Unit, vm: EditAccountViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
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
                Currency.entries.forEach { cur -> Pill(cur.name, s.currency == cur, mono = true) { vm.setCurrency(cur) } }
            }
        }
        Text(stringResource(R.string.account_kind), style = LabelStyle, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(stringResource(R.string.account_kind_spending), s.kind == AccountKind.SPENDING) { vm.setKind(AccountKind.SPENDING) }
            Pill(stringResource(R.string.account_kind_savings), s.kind == AccountKind.SAVINGS) { vm.setKind(AccountKind.SAVINGS) }
        }
        if (s.editing) {
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.account_delete_hint), style = LabelStyle, color = c.muted)
            Text(
                stringResource(R.string.account_delete),
                style = sans(14, FontWeight.Medium), color = c.orange,
                modifier = Modifier.clickable(role = Role.Button, onClick = vm::delete).padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.account_save), enabled = s.canSave, onClick = vm::save)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, mono: Boolean = false, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.widthIn(min = 64.dp).height(34.dp)
            .background(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = if (mono) mono(13) else sans(13, FontWeight.Medium), color = if (selected) c.bg else c.muted) }
}
