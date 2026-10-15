package rs.budget.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.ui.add.DateBox
import rs.budget.ui.formatAmount
import rs.budget.ui.settings.ScreenHeader
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans
import java.time.format.DateTimeFormatter

@Composable
fun GoalDetailScreen(onClose: () -> Unit, onEdit: (String) -> Unit, vm: GoalDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    val locale = LocalLocale.current.platformLocale
    LaunchedEffect(s.gone) { if (s.gone) onClose() }
    val status = s.status ?: return

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(status.goal.name, onClose) }
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.surface2).clickable(role = Role.Button) { onEdit(status.goal.id) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.goal_edit), style = sans(14, FontWeight.Medium), color = c.text) }
            }
        }
        item { GoalCard(status, goalColor(0), onClick = null) }
        item {
            Column(Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.goal_add_title), style = sans(16, FontWeight.SemiBold), color = c.text)
                AmountField(stringResource(R.string.goal_add_amount), s.amountText, status.goal.target.currency, vm::setAmount)
                DateBox(s.date, vm::setDate, Modifier.fillMaxWidth(), label = stringResource(R.string.goal_add_date))
                PrimaryButton(stringResource(R.string.goal_add), enabled = s.canAdd, onClick = vm::add)
            }
        }
        item { Text(stringResource(R.string.goal_entries), style = LabelStyle, color = c.muted) }
        if (s.allocations.isEmpty()) item { Text(stringResource(R.string.goal_entries_empty), style = BodyStyle, color = c.muted) }
        items(s.allocations, key = { it.id }) { a ->
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(a.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)), style = sans(14), color = c.muted, modifier = Modifier.weight(1f))
                    Text("${formatAmount(a.money, locale = locale)} ${a.money.currency}", style = mono(14), color = c.text)
                    Text(
                        stringResource(R.string.goal_remove_entry),
                        style = sans(13, FontWeight.Medium), color = c.orange,
                        modifier = Modifier.clickable(role = Role.Button) { vm.deleteAllocation(a.id) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.goal_delete),
                style = sans(14, FontWeight.Medium), color = c.orange,
                modifier = Modifier.clickable(role = Role.Button) { vm.deleteGoal(); onClose() }.padding(vertical = 10.dp),
            )
        }
    }
}
