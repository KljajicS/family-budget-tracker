package rs.budget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.ui.WriteFeedback
import rs.budget.R
import rs.budget.ui.CategoryPalette
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.AddButton
import rs.budget.ui.theme.ConfirmDialog
import rs.budget.ui.theme.DangerLink
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LoadingOrError
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.sans

@Composable
fun CategoriesScreen(onBack: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val state by vm.categories.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val categories = state ?: return LoadingOrError(failed, vm::retry)
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.categories_title), onBack) }
                AddButton(stringResource(R.string.category_new_title), onNew)
            }
        }
        if (categories.isEmpty()) item { Text(stringResource(R.string.categories_empty), style = BodyStyle, color = c.muted) }
        items(categories, key = { it.id }) { cat ->
            OutlinedBox(Modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp), onClick = { onEdit(cat.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(12.dp).background(colorOf(cat.color), CircleShape))
                    Text(categoryDisplayName(cat.id, cat.name), style = sans(15, FontWeight.Medium), color = c.text)
                }
            }
        }
    }
}

@Composable
fun EditCategoryScreen(onClose: () -> Unit, vm: EditCategoryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val write by vm.writes.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.category_edit_title else R.string.category_new_title), onClose)
        if (!s.loaded) return@Column LoadingOrError(s.loadFailed, vm::retryLoad)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(stringResource(R.string.category_name), s.name, vm::setName)
            Text(stringResource(R.string.category_color), style = LabelStyle, color = c.muted)
            // 48dp targets with 2dp gaps keep the original 40dp swatch on a 50dp pitch.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CategoryPalette.forEachIndexed { index, hex ->
                    val selected = s.color == hex
                    val name = stringResource(PaletteNames[index])
                    Box(
                        Modifier.selectable(selected = selected, role = Role.RadioButton) { vm.setColor(hex) }
                            .minimumInteractiveComponentSize().size(40.dp).clip(CircleShape)
                            .border(if (selected) 3.dp else 0.dp, if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                            .padding(if (selected) 5.dp else 0.dp)
                            .background(colorOf(hex), CircleShape)
                            .semantics { contentDescription = name },
                    )
                }
            }
            if (s.editing) {
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.category_delete_hint), style = LabelStyle, color = c.muted)
                DangerLink(stringResource(R.string.category_delete), enabled = !write.busy) { confirming = true }
            }
        }
        WriteFeedback(vm.writes)
        PrimaryButton(stringResource(R.string.category_save), enabled = s.canSave && !write.busy, onClick = vm::save)
    }

    if (confirming && s.loaded) {
        ConfirmDialog(stringResource(R.string.audit_ui_delete_title, categoryDisplayName(s.id.orEmpty(), s.name.trim())), stringResource(R.string.category_delete), write.busy, vm::delete, { confirming = false }) {
            Text(stringResource(R.string.category_delete_hint), style = sans(14), color = c.text)
            Text(stringResource(R.string.audit_ui_delete_irreversible), style = sans(14), color = c.orange)
            WriteFeedback(vm.writes)
        }
    }
}

/** Spoken names for [CategoryPalette], in the same order. */
internal val PaletteNames = listOf(
    R.string.recovery_color_indigo, R.string.recovery_color_orange, R.string.recovery_color_teal, R.string.recovery_color_purple,
    R.string.recovery_color_gray, R.string.recovery_color_pink, R.string.recovery_color_mustard, R.string.recovery_color_blue,
    R.string.recovery_color_red, R.string.recovery_color_green, R.string.recovery_color_violet, R.string.recovery_color_navy,
)
