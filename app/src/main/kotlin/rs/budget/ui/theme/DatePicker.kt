package rs.budget.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rs.budget.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun BudgetDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = LocalColors.current
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val colors = DatePickerDefaults.colors(
        containerColor = c.bg,
        titleContentColor = c.muted,
        headlineContentColor = c.text,
        weekdayContentColor = c.muted,
        subheadContentColor = c.text,
        navigationContentColor = c.text,
        yearContentColor = c.text,
        currentYearContentColor = c.primary,
        selectedYearContentColor = c.onPrimary,
        selectedYearContainerColor = c.primary,
        dayContentColor = c.text,
        selectedDayContentColor = c.onPrimary,
        selectedDayContainerColor = c.primary,
        todayContentColor = c.primary,
        todayDateBorderColor = c.primary,
        dividerColor = c.border,
    )
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = c.primary)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                colors = buttonColors,
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() },
            ) { Text(stringResource(R.string.dialog_ok), style = sans(14, FontWeight.Medium)) }
        },
        dismissButton = {
            TextButton(colors = buttonColors, onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel), style = sans(14, FontWeight.Medium)) }
        },
        shape = RoundedCornerShape(24.dp),
        colors = colors,
    ) { DatePicker(state = state, colors = colors) }
}
