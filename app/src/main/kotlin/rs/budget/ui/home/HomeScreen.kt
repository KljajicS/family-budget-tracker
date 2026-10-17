package rs.budget.ui.home

import rs.budget.ui.theme.AddButton

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.ui.RateNotice
import rs.budget.ui.formatTransactionDate
import rs.budget.ui.RateStatus
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.monthLocale
import rs.budget.ui.monthName
import rs.budget.ui.theme.BigAmountStyle
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.SectionStyle
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(onAdd: () -> Unit, onAccounts: () -> Unit, onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale

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
                Column(Modifier.weight(1f)) {
                    Text(monthName(YearMonth.from(s.today), locale), style = TitleStyle, color = c.text)
                    Text(s.today.year.toString(), style = LabelStyle, color = c.muted)
                }
                AddButton(stringResource(R.string.home_add), onAdd)
            }
        }
        item { RateNotice(s.rateStatus) }
        item { SavingsCard(s, locale) }
        item { MonthSection(s, locale) }
        item { AccountsSection(s.accounts, onAccounts, locale) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_recent), style = SectionStyle, color = c.text, modifier = Modifier.padding(bottom = 6.dp))
                if (s.recent.isEmpty()) Text(stringResource(R.string.home_empty), style = BodyStyle, color = c.muted)
            }
        }
        items(s.recent, key = { it.transaction.id }) { RecentRow(it, s.accountNames, s.currentMemberId, s.today) }
    }
}

@Composable
fun accountColor(index: Int): Color {
    val c = LocalColors.current
    return listOf(c.green, c.primary, c.orange)[index % 3]
}

@Composable
fun AccountTile(name: String, color: Color) {
    val c = LocalColors.current
    Box(Modifier.size(40.dp).background(color.copy(alpha = c.tintAlpha), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = sans(15, FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun SavingsCard(s: HomeState, locale: Locale) {
    val c = LocalColors.current
    val eur = s.summary.savingsEur
    val rsd = s.summary.savingsRsd
    val primary = eur ?: rsd
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.home_total_savings).uppercase(locale), style = sans(13, FontWeight.Medium, 0.06.em), color = c.muted)
        if (primary == null) {
            Text("-", style = BigAmountStyle, color = c.muted)
            Text(stringResource(R.string.home_savings_unavailable), style = LabelStyle, color = c.orange)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatAmount(primary, locale = locale), style = BigAmountStyle, color = c.text)
                Text(primary.currency.name, style = mono(22, FontWeight.SemiBold), color = c.text, modifier = Modifier.padding(bottom = 5.dp))
            }
            if (eur != null && rsd != null) Text("≈ ${formatAmount(rsd, locale = locale)} RSD", style = mono(17), color = c.muted)
            if (eur == null) Text(stringResource(R.string.home_eur_unavailable), style = LabelStyle, color = c.orange)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_all_balances), style = LabelStyle, color = c.muted, modifier = Modifier.weight(1f))
            rateSource(s.rateStatus, locale)?.let { Text(it, style = LabelStyle, color = c.muted) }
        }
    }
}

@Composable
private fun rateSource(status: RateStatus, locale: Locale): String? {
    val snapshot = status.snapshot
    if (Currency.EUR in snapshot.overrides) return stringResource(R.string.home_rate_manual)
    val quote = snapshot.cached[Currency.EUR] ?: return null
    return stringResource(R.string.home_rate_nbs, quote.effectiveDate.format(DateTimeFormatter.ofPattern("d MMM", monthLocale(locale))))
}

@Composable
private fun MonthSection(s: HomeState, locale: Locale) {
    val c = LocalColors.current
    val month = YearMonth.from(s.today)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(stringResource(R.string.home_this_month), style = SectionStyle, color = c.text)
            Text(stringResource(R.string.home_month_range, month.lengthOfMonth(), monthName(month, locale)), style = LabelStyle, color = c.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.home_income), s.summary.income, sign = true, c.green, Modifier.weight(1f), locale)
            StatCard(stringResource(R.string.home_spent), s.summary.spent, sign = false, c.text, Modifier.weight(1f), locale)
        }
        val difference = s.summary.difference
        OutlinedBox(Modifier.fillMaxWidth(), radius = 18, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_difference), style = LabelStyle, color = c.muted, modifier = Modifier.weight(1f))
                AmountText(difference, sign = true, if (difference.isNegative) c.orange else c.green, locale)
            }
        }
        if (s.summary.monthPartial) Text(stringResource(R.string.home_partial), style = CaptionStyle, color = c.orange)
    }
}

@Composable
private fun StatCard(title: String, value: Money, sign: Boolean, color: Color, modifier: Modifier, locale: Locale) {
    val c = LocalColors.current
    OutlinedBox(modifier, radius = 18, padding = PaddingValues(16.dp)) {
        Text(title, style = LabelStyle, color = c.muted)
        Spacer(Modifier.size(6.dp))
        AmountText(value, sign, color, locale)
    }
}

@Composable
private fun AmountText(value: Money, sign: Boolean, color: Color, locale: Locale) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatAmount(value, sign = sign, locale = locale), style = mono(20, FontWeight.SemiBold), color = color, maxLines = 1)
        Text(value.currency.name, style = sans(13), color = if (color == c.text) c.muted else color, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun AccountsSection(accounts: List<AccountBalance>, onAccounts: () -> Unit, locale: Locale) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_accounts), style = SectionStyle, color = c.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.home_see_all), style = sans(14), color = c.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAccounts).padding(vertical = 10.dp),
            )
        }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.accounts_empty), style = BodyStyle, color = c.muted)
        } else OutlinedBox(Modifier.fillMaxWidth(), radius = 18, padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            accounts.forEachIndexed { i, (account, balance) ->
                if (i > 0) HorizontalDivider(color = c.border, thickness = 1.dp)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountTile(account.name, accountColor(i))
                    Text(account.name, style = sans(15), color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${formatAmount(balance, locale = locale)} ${balance.currency}", style = mono(14), color = if (balance.isNegative) c.orange else c.text)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentTx, accountNames: Map<String, String>, me: String?, today: LocalDate) {
    val c = LocalColors.current
    val t = r.transaction
    val category = t.categoryId?.let { id -> categoryDisplayName(id, r.categoryName) }
    val typeLabel = when (t.type) {
        TransactionType.EXPENSE -> R.string.add_expense
        TransactionType.INCOME -> R.string.add_income
        TransactionType.TRANSFER -> R.string.add_transfer
        TransactionType.LOAN_RECEIPT -> R.string.loan_received
    }
    val title = t.merchant.ifBlank { category ?: stringResource(typeLabel) }
    val who = if (t.paidBy == me) stringResource(R.string.member_you) else r.paidByName ?: ""
    val source = when (t.source) {
        TransactionSource.MANUAL -> R.string.source_manual
        TransactionSource.RECEIPT -> R.string.source_receipt
        TransactionSource.RECURRING -> R.string.source_recurring
    }
    val account = listOfNotNull(accountNames[t.accountId], t.toAccountId?.let { accountNames[it] }).joinToString(" → ").ifBlank { null }
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
            Text(listOfNotNull(category, account, who.ifBlank { null }, stringResource(source)).joinToString(" · "), style = LabelStyle, color = c.muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${formatAmount(signed, sign = t.type != TransactionType.TRANSFER)} ${t.money.currency}", style = mono(15), color = if (t.type == TransactionType.INCOME) c.green else c.text)
            Text(formatTransactionDate(t.date, today, stringResource(R.string.home_today), LocalLocale.current.platformLocale), style = CaptionStyle, color = c.muted)
        }
    }
}
