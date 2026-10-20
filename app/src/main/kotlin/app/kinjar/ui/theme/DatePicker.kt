package app.kinjar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kinjar.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun KinjarDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = LocalColors.current
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val colors = budgetDatePickerColors()
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

@Composable
fun KinjarDateRangePickerDialog(start: LocalDate?, end: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    val colors = budgetDatePickerColors()
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = LocalColors.current.primary)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                colors = buttonColors,
                onClick = {
                    val first = state.selectedStartDateMillis
                    val last = state.selectedEndDateMillis
                    if (first != null && last != null) onPick(
                        Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).toLocalDate(),
                        Instant.ofEpochMilli(last).atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                },
            ) { Text(stringResource(R.string.dialog_ok), style = sans(14, FontWeight.Medium)) }
        },
        dismissButton = {
            TextButton(colors = buttonColors, onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel), style = sans(14, FontWeight.Medium)) }
        },
        shape = RoundedCornerShape(24.dp),
        colors = colors,
    ) { DateRangePicker(state = state, modifier = Modifier.heightIn(max = 560.dp), colors = colors) }
}

@Composable
private fun budgetDatePickerColors(): androidx.compose.material3.DatePickerColors {
    val c = LocalColors.current
    return DatePickerDefaults.colors(
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
        dayInSelectionRangeContainerColor = c.primarySoft,
        dayInSelectionRangeContentColor = c.text,
        dividerColor = c.border,
    )
}
