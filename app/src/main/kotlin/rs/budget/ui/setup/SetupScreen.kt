package rs.budget.ui.setup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.ui.WriteFeedback
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.SectionStyle
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.mono
import rs.budget.ui.theme.sans

@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(state.done) { if (state.done) onDone() }

    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCurrency by remember { mutableStateOf(Currency.RSD) }

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = TitleStyle, color = c.text)
        FieldBox(stringResource(R.string.setup_name_label), state.name, vm::setName)

        Text(stringResource(R.string.setup_accounts_title), style = SectionStyle, color = c.text, modifier = Modifier.padding(top = 6.dp))
        state.accounts.forEachIndexed { i, a ->
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), selected = a.enabled, onClick = { vm.toggleAccount(i) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = sans(14, FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
                    Text(a.currency.name, style = mono(13), color = c.muted)
                }
            }
        }

        if (adding) {
            FieldBox(stringResource(R.string.setup_account_name), newName, { newName = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { cur ->
                    val sel = newCurrency == cur
                    val shape = RoundedCornerShape(17.dp)
                    Box(
                        Modifier.widthIn(min = 64.dp).height(34.dp)
                            .background(if (sel) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
                            .border(1.dp, if (sel) c.text else c.border, shape)
                            .clickable(role = Role.RadioButton) { newCurrency = cur }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(cur.name, style = mono(13), color = if (sel) c.bg else c.muted) }
                }
            }
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(12.dp), onClick = { vm.addAccount(newName, newCurrency); newName = ""; adding = false }) {
                Text(stringResource(R.string.setup_add_account), style = sans(14, FontWeight.Medium), color = c.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            Text(
                "+ " + stringResource(R.string.setup_add_account),
                style = sans(14, FontWeight.Medium), color = c.primary,
                modifier = Modifier.clickable(role = Role.Button) { adding = true }.padding(vertical = 10.dp),
            )
        }

        Spacer(Modifier.size(10.dp))
        WriteFeedback(vm.writes)
        PrimaryButton(stringResource(R.string.setup_done), enabled = state.canFinish && !vm.writes.state.collectAsStateWithLifecycle().value.busy, onClick = vm::finish)
    }
}
