package rs.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import rs.budget.data.RecentTx
import rs.budget.data.StarterCategories
import androidx.compose.ui.graphics.Color
import rs.budget.domain.Currency
import rs.budget.domain.GoalStatus
import rs.budget.domain.Money
import rs.budget.ui.goals.goalColor
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.ui.budgets.BudgetRow
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.BigAmountStyle
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PaceBar
import rs.budget.ui.theme.RoundIconButton
import rs.budget.ui.theme.SectionStyle
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(onAdd: () -> Unit, onBudgets: () -> Unit, onGoals: () -> Unit, onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val settings = stringResource(R.string.settings_title)
                Box(
                    Modifier.size(44.dp).background(c.primary.copy(alpha = c.tintAlpha), CircleShape)
                        .clickable(role = Role.Button, onClick = onSettings)
                        .semantics { contentDescription = settings },
                    contentAlignment = Alignment.Center,
                ) { Text(s.memberName.take(1).uppercase(), style = sans(16, FontWeight.SemiBold), color = c.primary) }
                Text(monthName(s.today), style = TitleStyle, color = c.text, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Default.Add, stringResource(R.string.home_add), onAdd)
            }
        }
        item { rs.budget.ui.RateNotice(s.rateStatus) }
        item { SafeToSpendCard(s) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.home_income), formatAmount(s.summary.income, sign = true), c.green, Modifier.weight(1f))
                StatCard(stringResource(R.string.home_spent), formatAmount(-s.summary.spent, sign = true), c.text, Modifier.weight(1f))
            }
        }
        item { SavingsCard(s.summary.savingsTotal, s.summary.savingsByCurrency) }
        if (s.budgets.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_budgets), style = SectionStyle, color = c.text, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.home_see_all), style = sans(14), color = c.primary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onBudgets).padding(vertical = 10.dp),
                    )
                }
                s.budgets.forEach { BudgetRow(it, emptyList(), compact = true) }
            }
        }
        if (s.goals.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_goals), style = SectionStyle, color = c.text, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.home_see_all), style = sans(14), color = c.primary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onGoals).padding(vertical = 10.dp),
                    )
                }
                s.goals.forEachIndexed { i, g -> GoalMiniRow(g, goalColor(i)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_recent), style = SectionStyle, color = c.text, modifier = Modifier.padding(bottom = 6.dp))
                if (s.recent.isEmpty()) Text(stringResource(R.string.home_empty), style = BodyStyle, color = c.muted)
            }
        }
        items(s.recent, key = { it.transaction.id }) { RecentRow(it, s.currentMemberId, s.today) }
    }
}

@Composable
private fun GoalMiniRow(st: GoalStatus, color: Color) {
    val c = LocalColors.current
    val percent = (st.ratio * 100).toInt().coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.goal.name, style = sans(15, FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
            Text(
                "${formatAmount(st.allocated)} / ${formatAmount(st.goal.target)} ${st.goal.target.currency} · $percent%",
                style = mono(13), color = if (st.reached) c.green else c.muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(c.track, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(st.ratio.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun SafeToSpendCard(s: HomeState) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.home_safe_to_spend).uppercase(),
            style = sans(13, FontWeight.Medium, 0.06.em),
            color = c.muted,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatAmount(s.summary.safeToSpend), style = BigAmountStyle, color = c.text)
            Text(Currency.RSD.name, style = sans(16, FontWeight.Medium), color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(stringResource(R.string.home_per_day, formatAmount(s.summary.safePerDay), s.summary.daysLeft), style = sans(14), color = c.muted)
        if (s.summary.partial) Text(stringResource(R.string.home_partial), style = LabelStyle, color = c.orange)
        val spent = s.budgetSpent
        val total = s.budgetTotal
        if (spent != null && total != null && !total.isZero) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaceBar(spent.amount.toFloat() / total.amount.toFloat(), s.pace, c.primary, 10.dp)
                Text(stringResource(R.string.home_spent_of, formatAmount(spent), formatAmount(total)), style = LabelStyle, color = c.muted)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = LocalColors.current
    OutlinedBox(modifier, radius = 18, padding = PaddingValues(16.dp)) {
        Text(title, style = LabelStyle, color = c.muted)
        Spacer(Modifier.size(6.dp))
        Text(value, style = mono(20, FontWeight.SemiBold), color = valueColor)
    }
}

@Composable
private fun SavingsCard(total: Money, byCurrency: Map<Currency, Money>) {
    val c = LocalColors.current
    OutlinedBox(Modifier.fillMaxWidth(), radius = 18, padding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)) {
        Text(stringResource(R.string.home_savings), style = LabelStyle, color = c.muted)
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatAmount(total), style = mono(24, FontWeight.SemiBold), color = c.text)
            Text(Currency.RSD.name, style = sans(14), color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (byCurrency.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byCurrency.values.forEach { m ->
                    Text(
                        "${formatAmount(m)} ${m.currency}",
                        style = mono(13),
                        color = c.text,
                        modifier = Modifier.background(c.surface2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentTx, me: String?, today: LocalDate) {
    val c = LocalColors.current
    val t = r.transaction
    val category = t.categoryId?.let { id -> categoryDisplayName(id, r.categoryName) }
    val typeLabel = when (t.type) {
        TransactionType.EXPENSE -> R.string.add_expense
        TransactionType.INCOME -> R.string.add_income
        TransactionType.TRANSFER -> R.string.add_transfer
    }
    val title = t.merchant.ifBlank { category ?: stringResource(typeLabel) }
    val who = if (t.paidBy == me) stringResource(R.string.member_you) else r.paidByName ?: ""
    val source = when (t.source) {
        TransactionSource.MANUAL -> R.string.source_manual
        TransactionSource.RECEIPT -> R.string.source_receipt
        TransactionSource.RECURRING -> R.string.source_recurring
    }
    val signed = if (t.type == TransactionType.EXPENSE) -t.money else t.money
    val accent = when (t.type) {
        TransactionType.INCOME -> c.green
        else -> StarterCategories.all.firstOrNull { it.id == t.categoryId }?.let { colorOf(it.color) } ?: c.muted
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(accent.copy(alpha = c.tintAlpha), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), style = sans(15, FontWeight.SemiBold), color = accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = sans(15, FontWeight.Medium), color = c.text, maxLines = 1)
            Text(listOfNotNull(category, who.ifBlank { null }, stringResource(source)).joinToString(" · "), style = LabelStyle, color = c.muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatAmount(signed, sign = t.type != TransactionType.TRANSFER), style = mono(15), color = if (t.type == TransactionType.INCOME) c.green else c.text)
            Text(relativeDate(t.date, today), style = CaptionStyle, color = c.muted)
        }
    }
}

@Composable
private fun monthName(today: LocalDate): String =
    today.month.getDisplayName(TextStyle.FULL_STANDALONE, dateLocale()).replaceFirstChar { it.titlecase() }

@Composable
private fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.home_today)
    today.minusDays(1) -> stringResource(R.string.home_yesterday)
    else -> date.format(DateTimeFormatter.ofPattern("d MMM", dateLocale()))
}

/** The app's Serbian strings are Latin script; sr-RS defaults to Cyrillic for month names, so pin Latin. */
@Composable
private fun dateLocale(): Locale {
    val locale = LocalLocale.current.platformLocale
    return if (locale.language == "sr") Locale.forLanguageTag("sr-Latn") else locale
}
