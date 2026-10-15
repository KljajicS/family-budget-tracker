package rs.budget.ui.goals

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.domain.GoalStatus
import rs.budget.domain.Money
import rs.budget.ui.formatAmount
import rs.budget.ui.monthName
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.format.TextStyle

@Composable
fun GoalsScreen(onNew: () -> Unit, onOpen: (String) -> Unit, vm: GoalsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goals_title), style = TitleStyle, color = c.text, modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.surface2).clickable(role = Role.Button, onClick = onNew).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.goals_new), style = sans(14, FontWeight.Medium), color = c.text) }
            }
        }
        item { TotalCard(s) }
        if (s.statuses.isEmpty()) {
            item { Text(stringResource(R.string.goals_empty), style = BodyStyle, color = c.muted) }
        } else {
            item { NeedsCard(s) }
            items(s.statuses, key = { it.goal.id }) { st -> GoalCard(st, goalColor(s.statuses.indexOf(st))) { onOpen(st.goal.id) } }
        }
        if (s.partial) item { Text(stringResource(R.string.home_partial), style = CaptionStyle, color = c.muted) }
    }
}

@Composable
fun goalColor(index: Int): Color {
    val c = LocalColors.current
    return listOf(c.green, c.orange, c.primary)[index % 3]
}

@Composable
private fun TotalCard(s: GoalsState) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val palette = listOf(c.primary, c.orange, c.green)
    val entries = s.savingsByCurrency.entries.sortedBy { it.key.ordinal }
    Column(Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.goals_total_savings), style = LabelStyle, color = c.muted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatAmount(s.savingsTotal, locale = locale), style = mono(26, FontWeight.SemiBold), color = c.text)
            Text("RSD", style = sans(14, FontWeight.Medium), color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (entries.size > 1 && !s.savingsTotal.isZero) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                entries.forEachIndexed { i, (_, m) ->
                    val share = rsdShare(m, s)
                    if (share > 0f) Box(Modifier.weight(share).fillMaxSize().background(palette[i % palette.size]))
                }
            }
        }
        entries.forEachIndexed { i, (currency, m) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(palette[i % palette.size], RoundedCornerShape(3.dp)))
                Text("${formatAmount(m, locale = locale)} ${currency.name}", style = mono(13), color = c.text, modifier = Modifier.weight(1f))
                if (currency != Currency.RSD) Text("≈ ${approxRsd(m, s)}", style = mono(13), color = c.muted)
            }
        }
        val over = s.unassigned.isNegative
        Text(
            if (over) stringResource(R.string.goals_over_assigned, formatAmount(-s.unassigned, locale = locale))
            else stringResource(R.string.goals_unassigned, formatAmount(s.unassigned, locale = locale)),
            style = LabelStyle, color = if (over) c.orange else c.muted,
        )
    }
}

private fun rsdShare(m: Money, s: GoalsState): Float =
    if (m.currency == Currency.RSD) (m.amount.toFloat() / s.savingsTotal.amount.toFloat()).coerceAtLeast(0f) else 0f

@Composable
private fun approxRsd(m: Money, s: GoalsState): String = if (m.currency == Currency.RSD) formatAmount(m) else stringResource(R.string.goals_no_rate)

@Composable
private fun NeedsCard(s: GoalsState) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val short = s.needs - s.averageSaved
    val tone = if (short.isNegative || short.isZero) c.green else c.orange
    Column(
        Modifier.fillMaxWidth().background(tone.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.goals_needs, formatAmount(s.needs, locale = locale)), style = sans(15, FontWeight.SemiBold), color = tone)
        Text(
            if (short.isNegative || short.isZero) stringResource(R.string.goals_needs_covered, formatAmount(s.averageSaved, locale = locale))
            else stringResource(R.string.goals_needs_short, formatAmount(s.averageSaved, locale = locale), formatAmount(short, locale = locale)),
            style = sans(14), color = c.text,
        )
    }
}

@Composable
fun GoalCard(st: GoalStatus, color: Color, onClick: (() -> Unit)?) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val percent = (st.ratio * 100).toInt().coerceIn(0, 100)
    OutlinedBox(Modifier.fillMaxWidth(), radius = 16, padding = PaddingValues(16.dp), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.goal.name, style = sans(16, FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
                Text("$percent%", style = mono(14, FontWeight.SemiBold), color = if (st.reached) c.green else color)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(c.track, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(st.ratio.toFloat().coerceIn(0f, 1f)).fillMaxSize().background(color, RoundedCornerShape(4.dp)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${formatAmount(st.allocated, locale = locale)} / ${formatAmount(st.goal.target, locale = locale)} ${st.goal.target.currency}",
                    style = mono(12), color = c.muted, modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        st.reached -> stringResource(R.string.goals_reached)
                        st.arrival != null -> stringResource(R.string.goals_pace, formatAmount(st.goal.monthly, locale = locale), st.goal.monthly.currency.name, "${monthName(st.arrival, locale, TextStyle.SHORT_STANDALONE)} ${st.arrival.year}")
                        else -> stringResource(R.string.goals_no_contribution)
                    },
                    style = CaptionStyle, color = c.muted,
                )
            }
        }
    }
}
