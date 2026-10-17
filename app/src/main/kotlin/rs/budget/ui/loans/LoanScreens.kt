package rs.budget.ui.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.*
import rs.budget.ui.WriteFeedback
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.formatDate
import rs.budget.ui.settings.ScreenHeader
import rs.budget.ui.theme.*
import java.time.LocalDate

@Composable
private fun money(value: Money): String = "${formatAmount(value, locale = LocalLocale.current.platformLocale)} ${value.currency}"

@Composable
private fun date(value: LocalDate): String = formatDate(value, LocalLocale.current.platformLocale)

@Composable
private fun dated(day: LocalDate, value: Money): String = "${date(day)} · ${money(value)}"

private fun accountOptions(accounts: List<Account>) = accounts.filterNot { it.archived }.map { it.id to "${it.name} · ${it.currency}" }

@Composable
private fun Hint(text: String) = Text(text, style = LabelStyle, color = LocalColors.current.muted)

@Composable
fun LoansSection(onNew: () -> Unit, onOpen: (String) -> Unit, vm: LoanListViewModel = hiltViewModel()) {
    val book by vm.book.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loans_title), style = TitleStyle, color = c.text)
            AddButton(stringResource(R.string.loan_new), onNew)
        }
        val data = book ?: return@Column LoadingOrError(failed, vm::retry)
        val today = vm.clock.today()
        val balances = remember(data, today) { data.loans.associate { it.id to data.balance(it, today) } }
        val groups = data.loans.groupBy { loan ->
            when {
                loan.archived -> R.string.loan_removed
                balances.getValue(loan.id).paidOff -> R.string.loan_paid_off
                else -> R.string.loan_active
            }
        }
        listOf(R.string.loan_active, R.string.loan_paid_off, R.string.loan_removed).forEach { group ->
            val loans = groups[group].orEmpty()
            if (loans.isNotEmpty() && group != R.string.loan_active) Hint(stringResource(group))
            loans.forEach { loan ->
                OutlinedBox(Modifier.fillMaxWidth(), onClick = { onOpen(loan.id) }) {
                    Text(loan.name, style = BodyStyle, color = c.text)
                    LoanBalanceText(balances.getValue(loan.id))
                    loan.nextDue?.let { due ->
                        Hint(stringResource(R.string.loan_next, date(due), loan.usualPayment?.let { money(it) }.orEmpty()))
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
        balance.paidOff -> R.string.loan_paid_off
        else -> R.string.loan_remaining
    }), style = LabelStyle, color = if (balance.complete) c.muted else c.orange)
}

@Composable
private fun IssueText(issue: LoanIssue?) {
    issue?.let { Text(stringResource(it.message), style = LabelStyle, color = LocalColors.current.orange) }
}

@Composable
fun LoanEditorScreen(onBack: () -> Unit, vm: LoanEditorViewModel = hiltViewModel()) {
    val s by vm.form.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    LaunchedEffect(writes.finished) { if (writes.finished) onBack() }
    LoanPage(stringResource(if (vm.editingId == null) R.string.loan_new else R.string.loan_edit), { if (!writes.busy) onBack() }) {
        val d = data ?: return@LoanPage item { LoadingOrError(failed, vm::retry) }
        if (vm.editingId != null && d.loan == null) return@LoanPage section { Hint(stringResource(R.string.loan_not_found)) }
        val accounts = d.accounts
        section {
            FieldBox(stringResource(R.string.loan_name), s.name, { vm.edit(s.copy(name = it.take(100))) })
            if (vm.editingId == null) {
                LoanChoice(stringResource(R.string.loan_kind), stringResource(if (s.receiving) R.string.loan_receiving else R.string.loan_existing),
                    listOf("existing" to stringResource(R.string.loan_existing), "receiving" to stringResource(R.string.loan_receiving))) {
                    vm.edit(s.copy(receiving = it == "receiving"))
                }
                LoanChoice(stringResource(R.string.loan_currency), s.currency.name, Currency.entries.map { it.name to it.name }) {
                    vm.edit(s.copy(currency = Currency.valueOf(it)))
                }
                LoanAmount(stringResource(R.string.loan_opening), s.opening, s.currency) { vm.edit(s.copy(opening = it)) }
                LoanDate(stringResource(R.string.loan_start), s.start) { vm.edit(s.copy(start = it)) }
                Hint(stringResource(R.string.loan_start_hint))
                if (s.receiving) {
                    val account = accounts.find { it.id == s.accountId }
                    LoanChoice(stringResource(R.string.loan_receive_into), account?.name ?: stringResource(R.string.loan_select), accountOptions(accounts)) {
                        vm.edit(s.copy(accountId = it, deposit = ""))
                    }
                    account?.let { LoanAmount(stringResource(R.string.loan_actual_credit), s.deposit, it.currency) { text -> vm.edit(s.copy(deposit = text)) } }
                } else Hint(stringResource(R.string.loan_existing_hint))
            }
            LoanAmount(stringResource(R.string.loan_usual), s.usual, s.currency) { vm.edit(s.copy(usual = it)) }
            LoanDate(stringResource(R.string.loan_next_date), s.due ?: vm.clock.today()) { vm.edit(s.copy(due = it)) }
            if (s.due == null) Hint(stringResource(R.string.loan_no_due))
            else TextButton(onClick = { vm.edit(s.copy(due = null)) }) { Text(stringResource(R.string.loan_clear_date)) }
            IssueText(vm.issue())
            WriteFeedback(vm.writes)
            PrimaryButton(stringResource(R.string.loan_save), vm.canSave() && !writes.busy, vm::save)
        }
    }
}

@Composable
fun LoanDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: LoanDetailViewModel = hiltViewModel()) {
    val loaded by vm.data.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    var archive by rememberSaveable { mutableStateOf(false) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(writes.finished) { if (writes.finished) onBack() }
    val back = { if (!writes.busy) onBack() }
    val d = loaded
    val loan = d?.book?.loans?.find { it.id == vm.id }
    if (d == null || loan == null) {
        LoanPage(stringResource(R.string.loans_title), back) {
            if (d == null) item { LoadingOrError(failed, vm::retry) } else section { Hint(stringResource(R.string.loan_not_found)) }
        }
        return
    }
    if (action != LoanAction.NONE) {
        LoanActionScreen(vm, loan, d)
        return
    }
    val data = d.book
    val accounts = d.accounts
    val c = LocalColors.current
    val today = vm.clock.today()
    val balance = remember(data, loan, today) { data.balance(loan, today) }
    val history = remember(data, loan.id) { data.history(loan.id) }
    val adjustments = remember(data, loan.id) { data.adjustments.filter { it.loanId == loan.id }.sortedByDescending { it.date } }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    val receipt = remember(data, loan.id) { data.receipt(loan.id) }
    LoanPage(loan.name, back) {
        section {
            LoanBalanceText(balance)
            if (loan.archived) Hint(stringResource(R.string.loan_removed))
            loan.nextDue?.let { Text(stringResource(R.string.loan_next, date(it), loan.usualPayment?.let { m -> money(m) }.orEmpty()), color = c.muted) }
            receipt?.let { Hint(stringResource(R.string.loan_receipt_summary, money(it.money), accountNames[it.accountId].orEmpty())) }
            if (!loan.archived) {
                TextButton(onClick = { onEdit(loan.id) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_edit)) }
                PrimaryButton(stringResource(R.string.loan_record), !writes.busy && accounts.any { !it.archived }) { vm.begin(LoanAction.PAYMENT) }
                TextButton(onClick = { vm.begin(LoanAction.LINK) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_link)) }
                if (receipt != null) TextButton(onClick = vm::beginReceipt, enabled = !writes.busy) { Text(stringResource(R.string.loan_correct_receipt)) }
            }
            Text(stringResource(R.string.loan_history), style = TitleStyle, color = c.text)
        }
        items(history, key = { "payment-${it.first.id}" }) { (payment, tx) ->
            OutlinedBox(Modifier.fillMaxWidth()) {
                if (tx == null) Text(stringResource(R.string.loan_missing_expense), style = BodyStyle, color = c.orange) else {
                    Text(dated(tx.date, tx.money), style = BodyStyle, color = c.text)
                    Hint(accountNames[tx.accountId].orEmpty())
                }
                Hint(if (payment.principal == null) stringResource(R.string.loan_split_unknown) else
                    stringResource(R.string.loan_split_summary, money(payment.principal), money(payment.interest!!)))
                Row {
                    if (tx != null) TextButton(onClick = { vm.editSplit(payment) }, enabled = !writes.busy) { Text(stringResource(R.string.loan_edit_split)) }
                    if (!loan.archived) TextButton(onClick = { removingId = payment.id }, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove_payment)) }
                }
            }
        }
        items(adjustments, key = { "adjustment-${it.id}" }) {
            OutlinedBox(Modifier.fillMaxWidth()) {
                Text(dated(it.date, it.amount), color = c.text)
                Text(it.reason, color = c.muted)
            }
        }
        section {
            if (!loan.archived) {
                TextButton(onClick = { vm.begin(LoanAction.ADJUST) }, enabled = !writes.busy && balance.complete) { Text(stringResource(R.string.loan_adjust)) }
                TextButton(onClick = { archive = true }, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove), color = c.orange) }
            }
            WriteFeedback(vm.writes)
        }
    }
    if (archive) AlertDialog(onDismissRequest = { if (!writes.busy) archive = false },
        title = { Text(stringResource(R.string.loan_remove)) }, text = { Text(stringResource(R.string.loan_remove_warning)) },
        confirmButton = { TextButton(onClick = vm::archive, enabled = !writes.busy) { Text(stringResource(R.string.loan_remove)) } },
        dismissButton = { TextButton(onClick = { archive = false }, enabled = !writes.busy) { Text(stringResource(R.string.dialog_cancel)) } })
    history.find { it.first.id == removingId }?.takeIf { !loan.archived }?.let { (payment, tx) ->
        AlertDialog(onDismissRequest = { if (!writes.busy) removingId = null }, title = { Text(stringResource(R.string.loan_remove_payment)) },
            text = { Text(stringResource(R.string.loan_payment_remove_warning)) },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.remove(payment, false); removingId = null }, enabled = !writes.busy) { Text(stringResource(R.string.loan_unlink)) }
                    if (tx != null) TextButton(onClick = { vm.remove(payment, true); removingId = null }, enabled = !writes.busy) { Text(stringResource(R.string.loan_delete_expense)) }
                }
            }, dismissButton = { TextButton(onClick = { removingId = null }) { Text(stringResource(R.string.dialog_cancel)) } })
    }
}

@Composable
private fun LoanActionScreen(vm: LoanDetailViewModel, loan: Loan, d: LoanDetailData) {
    val action by vm.action.collectAsStateWithLifecycle()
    val s by vm.form.collectAsStateWithLifecycle()
    val adjustment by vm.adjustment.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    val accounts = d.accounts
    val categories = d.categories
    val c = LocalColors.current
    val picking = action == LoanAction.LINK && s.linked == null
    val today = vm.clock.today()
    val candidates = remember(d.book, loan, today, picking) { if (picking) d.book.linkCandidates(loan, today) else emptyList() }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    val title = when {
        action == LoanAction.ADJUST -> R.string.loan_adjust
        action == LoanAction.SPLIT -> R.string.loan_edit_split
        action == LoanAction.RECEIPT -> R.string.loan_correct_receipt
        picking -> R.string.loan_link
        else -> R.string.loan_record
    }
    LoanPage(stringResource(title), vm::close) {
        when {
            action == LoanAction.ADJUST -> section {
                LoanChoice(stringResource(R.string.loan_correction_direction), stringResource(if (adjustment.increase) R.string.loan_increase else R.string.loan_decrease),
                    listOf("up" to stringResource(R.string.loan_increase), "down" to stringResource(R.string.loan_decrease))) { vm.editAdjustment(adjustment.copy(increase = it == "up")) }
                LoanAmount(stringResource(R.string.loan_correction_amount), adjustment.amount, loan.opening.currency) { vm.editAdjustment(adjustment.copy(amount = it)) }
                FieldBox(stringResource(R.string.loan_reason), adjustment.reason, { vm.editAdjustment(adjustment.copy(reason = it.take(200))) })
                Hint(stringResource(R.string.loan_adjust_hint))
                val checked = vm.adjustmentCheck()
                IssueText(checked.issue)
                WriteFeedback(vm.writes)
                PrimaryButton(stringResource(R.string.loan_save), checked.value != null && !writes.busy, vm::adjust)
            }
            action == LoanAction.RECEIPT -> section {
                val account = accounts.find { it.id == s.accountId }
                LoanChoice(stringResource(R.string.loan_receive_into), account?.name ?: stringResource(R.string.loan_select),
                    accountOptions(accounts)) { vm.editForm(s.copy(accountId = it, amount = "")) }
                account?.let { LoanAmount(stringResource(R.string.loan_actual_credit), s.amount, it.currency) { amount -> vm.editForm(s.copy(amount = amount)) } }
                Hint(stringResource(R.string.loan_receipt_hint))
                val checked = vm.receiptCheck()
                IssueText(checked.issue)
                WriteFeedback(vm.writes)
                PrimaryButton(stringResource(R.string.loan_save), checked.value != null && !writes.busy, vm::correctReceipt)
            }
            picking -> {
                if (candidates.isEmpty()) section { Text(stringResource(R.string.loan_no_transactions), color = c.muted) }
                items(candidates, key = { it.id }) { tx ->
                    OutlinedBox(Modifier.fillMaxWidth(), onClick = { vm.link(tx) }) {
                        Text(dated(tx.date, tx.money), color = c.text)
                        Text("${accountNames[tx.accountId].orEmpty()} · ${tx.merchant}", color = c.muted)
                    }
                }
            }
            else -> section {
                val account = accounts.find { it.id == s.accountId }
                if (s.linked == null) {
                    LoanChoice(stringResource(R.string.loan_pay_from), account?.name ?: stringResource(R.string.loan_select), accountOptions(accounts)) {
                        vm.editForm(s.copy(accountId = it, amount = "", total = "", principal = ""))
                    }
                    account?.let { LoanAmount(stringResource(R.string.loan_amount_paid), s.amount, it.currency) { amount -> vm.editForm(s.copy(amount = amount)) } }
                    LoanDate(stringResource(R.string.loan_payment_date), s.date) { vm.editForm(s.copy(date = it)) }
                    LoanChoice(stringResource(R.string.loan_category), categories.find { it.id == s.categoryId }?.let { categoryDisplayName(it.id, it.name) } ?: stringResource(R.string.loan_uncategorized),
                        listOf("" to stringResource(R.string.loan_uncategorized)) + categories.map { it.id to categoryDisplayName(it.id, it.name) }) { vm.editForm(s.copy(categoryId = it.ifBlank { null })) }
                } else s.linked?.let { Text(dated(it.date, it.money), color = c.text) }
                if ((s.linked?.money?.currency ?: account?.currency) != loan.opening.currency && action != LoanAction.SPLIT) {
                    LoanAmount(stringResource(R.string.loan_payment_total), s.total, loan.opening.currency) { vm.editForm(s.copy(total = it)) }
                }
                if (action == LoanAction.SPLIT) Text("${stringResource(R.string.loan_payment_total)}: ${s.total} ${loan.opening.currency}", color = c.muted)
                LoanAmount(stringResource(R.string.loan_principal_paid), s.principal, loan.opening.currency) { vm.editForm(s.copy(principal = it)) }
                Hint(stringResource(R.string.loan_unknown_hint))
                val checked = vm.paymentCheck()
                checked.value?.interest?.let { Text(stringResource(R.string.loan_interest_value, money(it)), color = c.text) }
                IssueText(checked.issue)
                WriteFeedback(vm.writes)
                PrimaryButton(stringResource(R.string.loan_save_payment), checked.value != null && !writes.busy, vm::savePayment)
                Hint(stringResource(R.string.loan_record_hint))
            }
        }
    }
}

/** System Back runs the same guarded action as the header arrow. */
@Composable
private fun LoanPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(LocalColors.current.bg).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title, onBack)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

private fun LazyListScope.section(content: @Composable ColumnScope.() -> Unit) = item {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun LoanAmount(label: String, value: String, currency: Currency, onChange: (String) -> Unit) {
    FieldBox("$label (${currency.name})", value, { if (it.length <= 15) onChange(it.replace(',', '.')) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun LoanChoice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedBox(Modifier.fillMaxWidth(), onClick = { open = true }) {
        Hint(label)
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
        Hint(label)
        Text(date(value), style = BodyStyle, color = LocalColors.current.text)
    }
    if (open) BudgetDatePickerDialog(value, { open = false }) { onPick(it); open = false }
}
