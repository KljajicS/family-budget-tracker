package rs.budget.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Small building blocks that mirror the mockup components. Material widgets are used only where they look identical. */

@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.size(44.dp).background(c.surface2, CircleShape).clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = c.text, modifier = Modifier.size(22.dp)) }
}

@Composable
fun OutlinedBox(
    modifier: Modifier = Modifier,
    radius: Int = 18,
    padding: PaddingValues = PaddingValues(16.dp),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier
            .then(if (selected) Modifier.background(c.primarySoft, shape) else Modifier)
            .border(1.dp, if (selected) c.primary else c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .background(if (enabled) c.primary else c.surface2, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(16, FontWeight.SemiBold), color = if (enabled) c.onPrimary else c.muted) }
}

/** Bordered single-line input that matches the field boxes in the mockups. */
@Composable
fun FieldBox(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    OutlinedBox(modifier.fillMaxWidth(), radius = 14, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, style = CaptionStyle, color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = sans(14, FontWeight.Medium).copy(color = c.text),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
