package rs.budget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import rs.budget.R
import rs.budget.ui.CategoryPalette
import rs.budget.ui.categoryDisplayName
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LabelStyle
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.OutlinedBox
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.colorOf
import rs.budget.ui.theme.sans

@Composable
fun CategoriesScreen(onBack: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val c = LocalColors.current

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.categories_title), onBack) }
                NewPill(onNew)
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
fun NewPill(onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(R.string.list_new), style = sans(14, FontWeight.Medium), color = c.text) }
}

@Composable
fun EditCategoryScreen(onClose: () -> Unit, vm: EditCategoryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    LaunchedEffect(s.done) { if (s.done) onClose() }

    Column(
        Modifier.fillMaxSize().background(c.bg).imePadding().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(if (s.editing) R.string.category_edit_title else R.string.category_new_title), onClose)
        FieldBox(stringResource(R.string.category_name), s.name, vm::setName)
        Text(stringResource(R.string.category_color), style = LabelStyle, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryPalette.forEach { hex ->
                val selected = s.color == hex
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .border(if (selected) 3.dp else 0.dp, if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .padding(if (selected) 5.dp else 0.dp)
                        .background(colorOf(hex), CircleShape)
                        .clickable(role = Role.RadioButton) { vm.setColor(hex) }
                        .semantics { contentDescription = hex },
                )
            }
        }
        if (s.editing) {
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.category_delete_hint), style = LabelStyle, color = c.muted)
            Text(
                stringResource(R.string.category_delete),
                style = sans(14, FontWeight.Medium), color = c.orange,
                modifier = Modifier.clickable(role = Role.Button, onClick = vm::delete).padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.category_save), enabled = s.canSave, onClick = vm::save)
    }
}
