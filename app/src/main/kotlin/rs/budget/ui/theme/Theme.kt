package rs.budget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun BudgetTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.onPrimary,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        outline = c.border, error = c.orange,
    )
    // Start from Material defaults so Material dialogs keep sane sizes, then put Geist on every slot.
    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Sans),
        displayMedium = base.displayMedium.copy(fontFamily = Sans),
        displaySmall = base.displaySmall.copy(fontFamily = Sans),
        headlineLarge = base.headlineLarge.copy(fontFamily = Sans),
        headlineMedium = TitleStyle,
        headlineSmall = base.headlineSmall.copy(fontFamily = Sans),
        titleLarge = base.titleLarge.copy(fontFamily = Sans),
        titleMedium = SectionStyle,
        titleSmall = base.titleSmall.copy(fontFamily = Sans),
        bodyLarge = BodyStyle,
        bodyMedium = LabelStyle,
        bodySmall = CaptionStyle,
        labelLarge = sans(14, androidx.compose.ui.text.font.FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Sans),
        labelSmall = base.labelSmall.copy(fontFamily = Sans),
    )
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
