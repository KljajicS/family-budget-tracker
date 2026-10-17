package rs.budget.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.GoalProgress
import rs.budget.ui.DragHandle
import rs.budget.ui.ReorderColumn
import rs.budget.ui.WriteFeedback
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.AddButton
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LoadingOrError
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun GoalsScreen(onNew: () -> Unit, onOpen: (String) -> Unit, vm: GoalsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val s = state ?: return LoadingOrError(failed, vm::retryLoad)
    val c = LocalColors.current
    val list = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goals_title), style = TitleStyle, color = c.text, modifier = Modifier.weight(1f))
                AddButton(stringResource(R.string.goal_new_title), onNew)
            }
        }
        if (s.goals.isEmpty()) {
            item { Text(stringResource(R.string.goals_empty), style = BodyStyle, color = c.muted) }
        } else {
            item {
                ReorderColumn(s.goals, { it.goal.id }, s.reorderFailures, vm::reorder, list, spacing = 12.dp) { goal, index, handle, actions ->
                    GoalCard(goal, index, actions, handle) { onOpen(goal.goal.id) }
                }
            }
            item { WriteFeedback(vm.writes) }
        }
    }
}

@Composable
private fun GoalCard(p: GoalProgress, index: Int, modifier: Modifier, handle: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    val target = p.goal.target
    val ratio = p.ratio?.toFloat()?.coerceIn(0f, 1f)
    val color = when {
        p.reached -> c.green
        ratio == null || ratio == 0f -> c.muted
        else -> c.primary
    }
    val percent = if (p.reached) 100 else ratio?.let { (it * 100).toInt() }
    OutlinedBox(modifier.fillMaxWidth(), radius = 16, padding = PaddingValues(start = 4.dp, top = 12.dp, end = 16.dp, bottom = 14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            DragHandle(handle.align(Alignment.CenterVertically))
            Box(Modifier.padding(top = 10.dp, end = 12.dp).size(28.dp).background(c.surface2, CircleShape), contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = mono(13, FontWeight.SemiBold), color = c.text)
            }
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.goal.name, style = sans(16, FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
                    Text(percent?.let { "$it%" } ?: "-", style = mono(16, FontWeight.SemiBold), color = color)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(c.track, RoundedCornerShape(5.dp))) {
                    if (ratio != null && ratio > 0f) Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(color, RoundedCornerShape(5.dp)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${p.funded?.let { formatAmount(it, locale = locale) } ?: "?"} / ${formatAmount(target, locale = locale)} ${target.currency}",
                        style = mono(12), color = c.muted, modifier = Modifier.weight(1f),
                    )
                    val left = p.left
                    Text(
                        when {
                            left == null -> stringResource(R.string.goals_unknown)
                            p.reached -> stringResource(R.string.goals_funded)
                            p.waiting -> stringResource(R.string.goals_waiting)
                            else -> stringResource(R.string.goals_to_go, formatAmount(left, locale = locale), left.currency.name)
                        },
                        style = CaptionStyle, color = when {
                            left == null -> c.orange
                            p.reached -> c.green
                            else -> c.muted
                        },
                    )
                }
            }
        }
    }
}
