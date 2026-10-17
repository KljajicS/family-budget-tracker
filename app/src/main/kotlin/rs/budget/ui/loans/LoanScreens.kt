package rs.budget.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.*
import rs.budget.ui.WriteFeedback
import rs.budget.ui.formatAmount
import rs.budget.ui.settings.ScreenHeader
import rs.budget.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
private fun money(value: Money): String = "${formatAmount(value, locale = LocalLocale.current.platformLocale)} ${value.currency}"

@Composable
private fun date(value: LocalDate): String = value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalLocale.current.platformLocale))

@Composable
fun LoansSection(onNew: () -> Unit, onOpen: (String) -> Unit, vm: LoanListViewModel = hiltViewModel()) {
    val book by vm.book.collectAsStateWithLifecycle()
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.loans_title), style = TitleStyle, color = c.text)
            TextButton(onClick = onNew) { Text(stringResource(R.string.loan_new), color = c.primary) }
        }
        val data = book ?: return@Column
        val groups = data.loans.groupBy { loan ->
            when {
                loan.archived -> R.string.loan_removed
                data.balance(loan, vm.clock.today()).paidOff -> R.string.loan_paid_off
                else -> R.string.loan_active
            }
        }
        listOf(R.string.loan_active, R.string.loan_paid_off, R.string.loan_removed).forEach { group ->
            val loans = groups[group].orEmpty()
            if (loans.isNotEmpty() && group != R.string.loan_active) Text(stringResource(group), style = LabelStyle, color = c.muted)
            loans.forEach { loan ->
                OutlinedBox(Modifier.fillMaxWidth(), onClick = { onOpen(loan.id) }) {
                    Text(loan.name, style = BodyStyle, color = c.text)
                    LoanBalanceText(data.balance(loan, vm.clock.today()))
                    loan.nextDue?.let { due ->
                        Text(stringResource(R.string.loan_next, date(due), loan.usualPayment?.let { money(it) }.orEmpty()), style = LabelStyle, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanBalanceText(balance: LoanBalance) {
    val c = LocalColors.current
    Text(money(balance.beforeUnknownPayments), style = mono(27), color = c.text, modifier = Modifier.padding(vertical = 10.dp))
    Text(stringResource(when {
        balance.invalidHistory -> R.string.loan_history_conflict
        !balance.complete -> R.string.loan_balance_incomplete
        else -> R.string.loan_remaining
    }), style = LabelStyle, color = if (balance.complete) c.muted else c.orange)
}

@Composable
fun LoanEditorScreen(onBack: () -> Unit, vm: LoanEditorViewModel = hiltViewModel()) {
    val s by vm.form.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    LaunchedEffect(writes.finished) { if (writes.finished) onBack() }
    LoanPage(stringResource(if (vm.editingId == null) R.string.loan_new else R.string.loan_edit), onBack) {
        if (!loaded) return@LoanPage
        FieldBox(stringResource(R.string.loan_name), s.name, { vm.form.value = s.copy(name = it.take(100)) })
        if (vm.editingId == null) {
            LoanChoice(stringResource(R.string.loan_kind), stringResource(if (s.receiving) R.string.loan_receiving else R.string.loan_existing),
                listOf("existing" to stringResource(R.string.loan_existing), "receiving" to stringResource(R.string.loan_receiving))) {
                vm.form.value = s.copy(receiving = it == "receiving")
            }
            LoanChoice(stringResource(R.string.loan_currency), s.currency.name, Currency.entries.map { it.name to it.name }) {
                vm.form.value = s.copy(currency = Currency.valueOf(it))
            }
            LoanAmount(stringResource(R.string.loan_opening), s.opening, s.currency) { vm.form.value = s.copy(opening = it) }
            LoanDate(stringResource(R.string.loan_start), s.start) { vm.form.value = s.copy(start = it) }
            Text(stringResource(R.string.loan_start_hint), style = LabelStyle, color = LocalColors.current.muted)
            if (s.receiving) {
                val account = accounts.find { it.id == s.accountId }
                LoanChoice(stringResource(R.string.loan_receive_into), account?.name ?: stringResource(R.string.loan_select), accounts.map { it.id to "${it.name} · ${it.currency}" }) {
                    vm.form.value = s.copy(accountId = it, deposit = "")
                }
                account?.let { LoanAmount(stringResource(R.string.loan_actual_credit), s.deposit, it.currency) { text -> vm.form.value = s.copy(deposit = text) } }
            } else Text(stringResource(R.string.loan_existing_hint), style = LabelStyle, color = LocalColors.current.muted)
        }
        LoanAmount(stringResource(R.string.loan_usual), s.usual, s.currency) { vm.form.value = s.copy(usual = it) }
        LoanDate(stringResource(R.string.loan_next_date), s.due ?: vm.clock.today()) { vm.form.value = s.copy(due = it) }
        if (s.due == null) Text(stringResource(R.string.loan_no_due), style = LabelStyle, color = LocalColors.current.muted)
        else TextButton(onClick = { vm.form.value = s.copy(due = null) }) { Text(stringResource(R.string.loan_clear_date)) }
        WriteFeedback(vm.writes)
        PrimaryButton(stringResource(R.string.loan_save), vm.canSave() && !writes.busy, vm::save)
    }
}

@Composable
fun LoanDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: LoanDetailViewModel = hiltViewModel()) {
    val book by vm.book.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    var archive by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LoanPayment?>(null) }
    val data = book ?: return
    val loan = data.loans.find { it.id == vm.id } ?: return
    val c = LocalColors.current
    LaunchedEffect(writes.finished) { if (writes.finished) onBack() }
    if (action != LoanAction.NONE) {
        LoanActionScreen(vm, loan) { if (!writes.busy) vm.action.value = LoanAction.NONE }
        return
    }
    LoanPage(loan.name, onBack) {
        LoanBalanceText(data.balance(loan, vm.clock.today()))
        if (loan.archived) Text(stringResource(R.string.loan_removed), style = LabelStyle, color = c.muted)
        loan.nextDue?.let { Text(stringResource(R.string.loan_next, date(it), loan.usualPayment?.let { m -> money(m) }.orEmpty()), color = c.muted) }
        if (!loan.archived) {
            TextButton(onClick = { onEdit(loan.id) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_edit)) }
            PrimaryButton(stringResource(R.string.loan_record), !writes.busy && accounts.any { !it.archived }) { vm.begin(LoanAction.PAYMENT) }
            TextButton(onClick = { vm.begin(LoanAction.LINK) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_link)) }
        }
        Text(stringResource(R.string.loan_history), style = TitleStyle, color = c.text)
        data.payments.filter { it.loanId == loan.id }.mapNotNull { payment -> data.transactions.find { it.id == payment.transactionId }?.let { payment to it } }
            .sortedByDescending { it.second.date }.forEach { (payment, tx) ->
                OutlinedBox(Modifier.fillMaxWidth()) {
                    Text("${date(tx.date)} · ${money(tx.money)}", style = BodyStyle, color = c.text)
                    Text(accounts.find { it.id == tx.accountId }?.name.orEmpty(), style = LabelStyle, color = c.muted)
                    Text(if (payment.principal == null) stringResource(R.string.loan_split_unknown) else
                        stringResource(R.string.loan_split_summary, money(payment.principal), money(payment.interest!!)), style = LabelStyle, color = c.muted)
                    Row {
                        TextButton(onClick = { vm.editSplit(payment) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_edit_split)) }
                        TextButton(onClick = { removing = payment }, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove_payment)) }
                    }
                }
            }
        data.adjustments.filter { it.loanId == loan.id }.sortedByDescending { it.date }.forEach {
            OutlinedBox(Modifier.fillMaxWidth()) {
                Text("${date(it.date)} · ${money(it.amount)}", color = c.text)
                Text(it.reason, color = c.muted)
            }
        }
        if (!loan.archived) {
            TextButton(onClick = { vm.begin(LoanAction.ADJUST) }, enabled = !writes.busy && data.balance(loan, vm.clock.today()).complete) { Text(stringResource(R.string.loan_adjust)) }
            TextButton(onClick = { archive = true }, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove), color = c.orange) }
        }
        WriteFeedback(vm.writes)
    }
    if (archive) AlertDialog(onDismissRequest = { if (!writes.busy) archive = false },
        title = { Text(stringResource(R.string.loan_remove)) }, text = { Text(stringResource(R.string.loan_remove_warning)) },
        confirmButton = { TextButton(onClick = vm::archive, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove)) } },
        dismissButton = { TextButton(onClick = { archive = false }, enabled = !writes.busy) { Text(stringResource(R.string.dialog_cancel)) } })
    removing?.let { payment ->
        AlertDialog(onDismissRequest = { if (!writes.busy) removing = null }, title = { Text(stringResource(R.string.loan_remove_payment)) },
            text = { Text(stringResource(R.string.loan_payment_remove_warning)) },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.remove(payment, false); removing = null }, enabled = !writes.busy) { Text(stringResource(R.string.loan_unlink)) }
                    TextButton(onClick = { vm.remove(payment, true); removing = null }, enabled = !writes.busy) { Text(stringResource(R.string.loan_delete_expense)) }
                }
            }, dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.dialog_cancel)) } })
    }
}

@Composable
private fun LoanActionScreen(vm: LoanDetailViewModel, loan: Loan, onBack: () -> Unit) {
    val action by vm.action.collectAsStateWithLifecycle()
    val s by vm.form.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LoanPage(stringResource(if (action == LoanAction.ADJUST) R.string.loan_adjust else if (action == LoanAction.SPLIT) R.string.loan_edit_split else R.string.loan_record), onBack) {
        if (action == LoanAction.ADJUST) {
            val amount by vm.adjustmentAmount.collectAsStateWithLifecycle()
            val reason by vm.adjustmentReason.collectAsStateWithLifecycle()
            val increase by vm.adjustmentIncrease.collectAsStateWithLifecycle()
            LoanChoice(stringResource(R.string.loan_correction_direction), stringResource(if (increase) R.string.loan_increase else R.string.loan_decrease),
                listOf("up" to stringResource(R.string.loan_increase), "down" to stringResource(R.string.loan_decrease))) { vm.adjustmentIncrease.value = it == "up" }
            LoanAmount(stringResource(R.string.loan_correction_amount), amount, loan.opening.currency) { vm.adjustmentAmount.value = it }
            FieldBox(stringResource(R.string.loan_reason), reason, { vm.adjustmentReason.value = it.take(200) })
            Text(stringResource(R.string.loan_adjust_hint), style = LabelStyle, color = c.muted)
            WriteFeedback(vm.writes)
            PrimaryButton(stringResource(R.string.loan_save), vm.adjustment() != null && !writes.busy, vm::adjust)
        } else {
            val account = accounts.find { it.id == s.accountId }
            if (action == LoanAction.LINK && s.linked == null) {
                val data = book ?: return@LoanPage
                val linked = data.payments.map { it.transactionId }.toSet()
                val candidates = data.transactions.filter { it.type == TransactionType.EXPENSE && it.id !in linked && it.date >= loan.start && it.date <= vm.clock.today() }
                if (candidates.isEmpty()) Text(stringResource(R.string.loan_no_transactions), color = c.muted)
                candidates.sortedByDescending { it.date }.forEach { tx ->
                    OutlinedBox(Modifier.fillMaxWidth(), onClick = { vm.link(tx) }) {
                        Text("${date(tx.date)} · ${money(tx.money)}", color = c.text)
                        Text("${accounts.find { it.id == tx.accountId }?.name.orEmpty()} · ${tx.merchant}", color = c.muted)
                    }
                }
                return@LoanPage
            }
            if (s.linked == null) {
                LoanChoice(stringResource(R.string.loan_pay_from), account?.name ?: stringResource(R.string.loan_select), accounts.filterNot { it.archived }.map { it.id to "${it.name} · ${it.currency}" }) {
                    vm.form.value = s.copy(accountId = it, amount = "", total = "", principal = "")
                }
                account?.let { LoanAmount(stringResource(R.string.loan_amount_paid), s.amount, it.currency) { amount -> vm.form.value = s.copy(amount = amount) } }
                LoanDate(stringResource(R.string.loan_payment_date), s.date) { vm.form.value = s.copy(date = it) }
                LoanChoice(stringResource(R.string.loan_category), categories.find { it.id == s.categoryId }?.name ?: stringResource(R.string.loan_uncategorized),
                    listOf("" to stringResource(R.string.loan_uncategorized)) + categories.map { it.id to it.name }) { vm.form.value = s.copy(categoryId = it.ifBlank { null }) }
            } else s.linked?.let { Text("${date(it.date)} · ${money(it.money)}", color = c.text) }
            if ((s.linked?.money?.currency ?: account?.currency) != loan.opening.currency && action != LoanAction.SPLIT) {
                LoanAmount(stringResource(R.string.loan_payment_total), s.total, loan.opening.currency) { vm.form.value = s.copy(total = it) }
            }
            if (action == LoanAction.SPLIT) Text("${stringResource(R.string.loan_payment_total)}: ${s.total} ${loan.opening.currency}", color = c.muted)
            LoanAmount(stringResource(R.string.loan_principal_paid), s.principal, loan.opening.currency) { vm.form.value = s.copy(principal = it) }
            Text(stringResource(R.string.loan_unknown_hint), style = LabelStyle, color = c.muted)
            vm.payment()?.interest?.let { Text(stringResource(R.string.loan_interest_value, money(it)), color = c.text) }
            WriteFeedback(vm.writes)
            PrimaryButton(stringResource(R.string.loan_save_payment), vm.payment() != null && !writes.busy, vm::savePayment)
            Text(stringResource(R.string.loan_record_hint), style = LabelStyle, color = c.muted)
        }
    }
}

@Composable
private fun LoanPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalColors.current.bg).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun LoanAmount(label: String, value: String, currency: Currency, onChange: (String) -> Unit) {
    FieldBox("$label (${currency.name})", value, { if (it.length <= 15) onChange(it.replace(',', '.')) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun LoanChoice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedBox(Modifier.fillMaxWidth(), onClick = { open = true }) {
        Text(label, style = LabelStyle, color = LocalColors.current.muted)
        Text("$value  ▾", style = BodyStyle, color = LocalColors.current.text)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) },
        text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) { options.forEach { (id, text) ->
            TextButton(onClick = { onPick(id); open = false }, modifier = Modifier.fillMaxWidth()) { Text(text) }
        } } }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) } })
}

@Composable
private fun LoanDate(label: String, value: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedBox(Modifier.fillMaxWidth(), onClick = { open = true }) {
        Text(label, style = LabelStyle, color = LocalColors.current.muted)
        Text(date(value), style = BodyStyle, color = LocalColors.current.text)
    }
    if (open) BudgetDatePickerDialog(value, { open = false }) { onPick(it); open = false }
}
