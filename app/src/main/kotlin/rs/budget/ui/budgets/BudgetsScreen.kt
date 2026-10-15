package rs.budget.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.data.StarterCategories
import rs.budget.domain.Bill
import rs.budget.domain.BudgetStatus
import rs.budget.domain.Category
import rs.budget.domain.PaceState
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.PaceBar
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun BudgetsScreen(onNew: () -> Unit, onEdit: (String) -> Unit, onPay: (String, LocalDate) -> Unit, vm: BudgetsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.budgets_title), style = TitleStyle, color = c.text)
                    Text(stringResource(R.string.budgets_subtitle, monthName(s.month), s.daysLeft), style = LabelStyle, color = c.muted)
                }
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onNew).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.budgets_new), style = sans(14, FontWeight.Medium), color = c.text) }
            }
        }
        item { rs.budget.ui.RateNotice(s.rateStatus) }
        if (s.statuses.isEmpty()) {
            item { Text(stringResource(R.string.budgets_empty), style = BodyStyle, color = c.muted) }
        } else {
            item { TotalCard(s) }
            items(s.statuses, key = { it.categoryId }) { st ->
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onEdit(st.categoryId) }) { BudgetRow(st, s.categories) }
            }
        }
        if (s.upcoming.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.bills_upcoming), style = TitleStyle.copy(fontSize = 18.sp), color = c.text, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.bills_total, formatAmount(s.upcomingTotal)), style = mono(13), color = c.muted)
                }
            }
            items(s.upcoming, key = { it.txId }) { bill ->
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onPay(bill.rule.id, bill.dueDate) }) {
                    BillRow(bill, s.accountNames[bill.rule.accountId] ?: "")
                }
            }
        }
    }
}

@Composable
private fun BillRow(bill: Bill, accountName: String) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val tone = if (bill.overdue) c.orange else c.text
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.size(44.dp).background(c.surface2, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(bill.dueDate.dayOfMonth.toString(), style = mono(15, FontWeight.SemiBold), color = tone)
            Text(bill.dueDate.format(DateTimeFormatter.ofPattern("MMM", locale)).uppercase(locale).trimEnd('.'), style = CaptionStyle, color = c.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(bill.rule.name, style = sans(15, FontWeight.Medium), color = c.text)
            Text(stringResource(if (bill.overdue) R.string.bills_overdue else R.string.bills_from, accountName), style = CaptionStyle, color = if (bill.overdue) c.orange else c.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatAmount(bill.rule.money), style = mono(15, FontWeight.Medium), color = tone)
            if (bill.rule.money.currency != rs.budget.domain.Currency.RSD) Text(bill.rule.money.currency.name, style = CaptionStyle, color = c.muted)
        }
    }
}

@Composable
private fun TotalCard(s: BudgetsState) {
    val c = LocalColors.current
    val left = s.total - s.spent
    val ratio = if (s.total.isZero) 0f else (s.spent.amount.toFloat() / s.total.amount.toFloat())
    val pace = s.statuses.firstOrNull()?.pace?.toFloat() ?: 0f
    Column(Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatAmount(left), style = mono(22, FontWeight.SemiBold), color = c.text)
                Text(stringResource(R.string.budgets_left), style = sans(14, FontWeight.Medium), color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(stringResource(R.string.budgets_of, formatAmount(s.total)), style = LabelStyle, color = c.muted)
        }
        PaceBar(ratio, pace, c.primary, 10.dp)
    }
}

@Composable
fun BudgetRow(st: BudgetStatus, categories: List<Category>, compact: Boolean = false) {
    val c = LocalColors.current
    val category = categories.firstOrNull { it.id == st.categoryId }
    val name = categoryDisplayName(st.categoryId, category?.name)
    val color = StarterCategories.all.firstOrNull { it.id == st.categoryId }?.let { colorOf(it.color) } ?: category?.let { colorOf(it.color) } ?: c.muted
    val warn = st.state == PaceState.LOW || st.state == PaceState.OVER || st.state == PaceState.OVER_PACE
    val barColor = if (st.state == PaceState.LOW || st.state == PaceState.OVER) c.orange else color
    val hint = when (st.state) {
        PaceState.AHEAD -> stringResource(R.string.budgets_row_left, formatAmount(st.left)) + " · " + stringResource(R.string.budgets_ahead)
        PaceState.ON_TRACK -> stringResource(R.string.budgets_row_left, formatAmount(st.left)) + " · " + stringResource(R.string.budgets_on_track)
        PaceState.OVER_PACE -> stringResource(R.string.budgets_row_left, formatAmount(st.left)) + " · " + stringResource(R.string.budgets_over_pace)
        PaceState.LOW -> stringResource(R.string.budgets_low, formatAmount(st.left))
        PaceState.OVER -> stringResource(R.string.budgets_over, formatAmount(-st.left))
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!compact) Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
            Text(name, style = if (compact) sans(14) else sans(15, FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
            val suffix = if (st.budget.currency == rs.budget.domain.Currency.RSD) "" else " ${st.budget.currency}"
            Text("${formatAmount(st.spent)} / ${formatAmount(st.budget)}$suffix", style = mono(13), color = if (warn) c.orange else c.muted)
        }
        PaceBar(st.ratio.toFloat(), st.pace.toFloat(), barColor, if (compact) 6.dp else 8.dp)
        if (!compact) Text(hint, style = CaptionStyle, color = if (warn) c.orange else c.muted)
    }
}

@Composable
private fun monthName(month: YearMonth): String = rs.budget.ui.monthName(month, LocalLocale.current.platformLocale)
