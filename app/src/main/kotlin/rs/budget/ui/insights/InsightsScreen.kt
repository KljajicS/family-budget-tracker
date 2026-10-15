package rs.budget.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Category
import rs.budget.domain.InsightsSummary
import rs.budget.domain.Money
import rs.budget.domain.Tip
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.formatAmount
import rs.budget.ui.formatCompact
import rs.budget.ui.monthName
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.format.TextStyle

@Composable
fun InsightsScreen(vm: InsightsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val sum = s.summary

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_title), style = TitleStyle, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::previousMonth, enabled = s.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.insights_prev), tint = if (s.canGoBack) c.text else c.border)
                }
                Text("${monthName(sum.month, locale, TextStyle.SHORT_STANDALONE)} ${sum.month.year}", style = sans(15, FontWeight.Medium), color = c.text)
                IconButton(onClick = vm::nextMonth, enabled = s.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.insights_next), tint = if (s.canGoForward) c.text else c.border)
                }
            }
        }
        item { rs.budget.ui.RateNotice(s.rateStatus) }
        if (!s.hasData) {
            item { Text(stringResource(R.string.insights_empty), style = BodyStyle, color = c.muted) }
            return@LazyColumn
        }
        item { SpendingCard(sum, s.categories) }
        item { MonthsCard(sum) }
        item { SavingsCard(sum) }
        if (sum.partial) item { Text(stringResource(R.string.home_partial), style = CaptionStyle, color = c.muted) }
        sum.tip?.let { tip -> item { TipCard(tip, s.categories) } }
    }
}

@Composable
private fun categoryName(id: String?, categories: Map<String, Category>): String =
    if (id == null) stringResource(R.string.insights_other) else categoryDisplayName(id, categories[id]?.name)

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun CardTitle(text: String, trailing: @Composable () -> Unit = {}) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = sans(16, FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SpendingCard(sum: InsightsSummary, categories: Map<String, Category>) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val colors = sum.slices.map { slice -> slice.categoryId?.let { categories[it]?.color }?.let(::colorOf) ?: c.muted }
    val legend = sum.slices.mapIndexed { i, slice -> Triple(colors[i], categoryName(slice.categoryId, categories), slice.share) }
    Card {
        CardTitle(stringResource(R.string.insights_where))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Donut(
                    sum.slices.mapIndexed { i, s -> colors[i] to s.share / 100f },
                    stringResource(R.string.insights_chart_spending, legend.joinToString { "${it.second} ${it.third}%" }),
                    Modifier.size(130.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCompact(sum.spent, locale), style = mono(18, FontWeight.SemiBold), color = c.text)
                    Text(stringResource(R.string.insights_spent_unit), style = CaptionStyle, color = c.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                legend.forEach { (color, label, share) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                        Text(label, style = sans(13), color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("$share%", style = mono(13), color = c.muted)
                    }
                }
                if (legend.isEmpty()) Text(stringResource(R.string.insights_no_spending), style = LabelStyle, color = c.muted)
            }
        }
    }
}

@Composable
private fun MonthsCard(sum: InsightsSummary) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val labels = sum.months.map { monthName(it.month, locale, TextStyle.SHORT_STANDALONE) }
    val current = sum.months.last()
    Card {
        CardTitle(stringResource(R.string.insights_income_vs)) {
            LegendDot(c.primary, stringResource(R.string.insights_income))
            LegendDot(c.orange, stringResource(R.string.insights_expenses), Modifier.padding(start = 10.dp))
        }
        BarPairs(
            sum.months.mapIndexed { i, m -> BarPair(labels[i], m.income.amount.toFloat(), m.expenses.amount.toFloat(), m.inProgress) },
            c.primary, c.orange,
            stringResource(R.string.insights_chart_months, sum.months.mapIndexed { i, m -> "${labels[i]} ${formatAmount(m.income, locale = locale)} ${formatAmount(m.expenses, locale = locale)}" }.joinToString()),
        )
        Text(
            if (current.inProgress) stringResource(R.string.insights_in_progress, monthName(current.month, locale), formatAmount(sum.averageSaved, locale = locale))
            else stringResource(R.string.insights_average_saved, formatAmount(sum.averageSaved, locale = locale)),
            style = LabelStyle, color = c.muted,
        )
    }
}

@Composable
private fun SavingsCard(sum: InsightsSummary) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val labels = sum.savings.map { monthName(it.first, locale, TextStyle.SHORT_STANDALONE) }
    Card {
        CardTitle(stringResource(R.string.insights_savings_trend)) {
            Text(
                stringResource(R.string.insights_since, formatAmount(sum.savingsDelta, sign = true, locale = locale), labels.first()),
                style = mono(13), color = if (sum.savingsDelta.isNegative) c.orange else c.green,
            )
        }
        TrendLine(
            sum.savings.mapIndexed { i, (_, m) -> labels[i] to m.amount.toFloat() },
            c.primary,
            stringResource(R.string.insights_chart_savings, sum.savings.mapIndexed { i, (_, m) -> "${labels[i]} ${formatAmount(m, locale = locale)}" }.joinToString()),
        )
    }
}

@Composable
private fun TipCard(tip: Tip, categories: Map<String, Category>) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    fun amt(m: Money) = formatAmount(m, locale = locale)
    val (title, body, tone) = when (tip) {
        is Tip.BudgetNearLimit -> Triple(
            stringResource(R.string.insights_tip_budget_title, categoryName(tip.categoryId, categories), tip.percent, tip.daysLeft),
            tip.average?.let { stringResource(R.string.insights_tip_budget_body_avg, amt(it), amt(tip.left)) } ?: stringResource(R.string.insights_tip_budget_body, amt(tip.left)),
            c.orange,
        )
        is Tip.AboveAverage -> Triple(
            stringResource(R.string.insights_tip_average_title, categoryName(tip.categoryId, categories)),
            stringResource(R.string.insights_tip_average_body, amt(tip.spent), amt(tip.average)),
            c.orange,
        )
        is Tip.Saved -> Triple(
            stringResource(R.string.insights_tip_saved_title, amt(tip.saved)),
            stringResource(R.string.insights_tip_saved_body, amt(tip.average)),
            c.green,
        )
    }
    Row(
        Modifier.fillMaxWidth().border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).background(tone.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Text("!", style = sans(16, FontWeight.SemiBold), color = tone)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = sans(15, FontWeight.SemiBold), color = c.text)
            Text(body, style = sans(14), color = c.muted)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = CaptionStyle, color = c.muted)
    }
}
