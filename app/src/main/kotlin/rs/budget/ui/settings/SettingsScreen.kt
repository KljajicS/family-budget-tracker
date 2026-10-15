package rs.budget.ui.settings

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import rs.budget.data.ThemeMode
import rs.budget.ui.theme.LabelStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.ui.WriteFeedback
import rs.budget.R
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.RoundIconButton
import rs.budget.ui.theme.TitleStyle
import rs.budget.ui.theme.sans

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onAccounts: () -> Unit, onRecurring: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)
        FieldBox(stringResource(R.string.settings_name), s.name, vm::setName)
        WriteFeedback(vm.writes)
        PrimaryButton(stringResource(R.string.settings_save_name), enabled = s.name.isNotBlank() && !s.saved && !vm.writes.state.collectAsStateWithLifecycle().value.busy, onClick = vm::saveName)
        Text(stringResource(R.string.settings_theme), style = LabelStyle, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { mode ->
                val label = stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })
                ThemePill(label, s.theme == mode) { vm.setTheme(mode) }
            }
        }
        SettingsRow(stringResource(R.string.settings_categories), onCategories)
        SettingsRow(stringResource(R.string.settings_accounts), onAccounts)
        SettingsRow(stringResource(R.string.settings_recurring), onRecurring)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.add_close), onBack)
        Text(title, style = TitleStyle, color = c.text)
    }
}

@Composable
private fun ThemePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.widthIn(min = 64.dp).height(34.dp)
            .background(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (selected) c.text else c.border, shape)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = sans(13, FontWeight.Medium), color = if (selected) c.bg else c.muted) }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    val c = LocalColors.current
    OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = sans(15, FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
    }
}
