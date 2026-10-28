package ca.kallistesoft.guidehunter.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.model.Trip
import kotlinx.datetime.LocalDate

// The pickers' dates are UTC midnights; LocalDate keeps them days.

private const val DAY_MILLIS = 86_400_000L

private fun LocalDate.utcMillis() = toEpochDays() * DAY_MILLIS

private fun Long.toDate() = LocalDate.fromEpochDays(Math.floorDiv(this, DAY_MILLIS))

/** Only [today] and later can be chosen: no trip leaves in the past. */
@OptIn(ExperimentalMaterial3Api::class)
private class FromToday(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toDate() >= today

    override fun isSelectableYear(year: Int) = year >= today.year
}

/** Picks one day, from [today] on: a departure. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, initial: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.utcMillis(),
        initialDisplayedMonthMillis = (initial ?: today).utcMillis(),
        selectableDates = remember(today) { FromToday(today) },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(it.toDate()) } },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("date-ok"),
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    ) {
        DatePicker(state = state, modifier = Modifier.testTag("date-picker"))
    }
}

/**
 * Picks a range of days from [today] on (one day is a range too): the dates a hunter can go. [onClear] adds a button
 * that removes the dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    today: LocalDate,
    onPick: (Trip) -> Unit,
    onDismiss: () -> Unit,
    initial: Trip? = null,
    onClear: (() -> Unit)? = null,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.utcMillis(),
        initialSelectedEndDateMillis = initial?.end?.utcMillis(),
        initialDisplayedMonthMillis = (initial?.start ?: today).utcMillis(),
        selectableDates = remember(today) { FromToday(today) },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis?.toDate() ?: return@TextButton
                    onPick(Trip(start, state.selectedEndDateMillis?.toDate() ?: start))
                },
                enabled = state.selectedStartDateMillis != null,
                modifier = Modifier.testTag("dates-ok"),
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = onClear, modifier = Modifier.testTag("dates-clear")) { Text(stringResource(R.string.dialog_clear)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
            }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.fillMaxWidth().heightIn(max = Sizes.dateRangePickerMaxHeight).testTag("date-range-picker"))
    }
}
