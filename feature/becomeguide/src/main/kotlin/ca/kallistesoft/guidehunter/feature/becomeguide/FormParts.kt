package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.GeoPoint

// The pieces the guide's forms (profile, offer) share.

/** Where a form is in being sent. */
enum class SendState { EDITING, SENDING, SENT, FAILED, NOT_CONFIGURED }

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    tag: String,
    error: String?,
    help: String? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error ?: help)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else DESCRIPTION_LINES,
        // Next moves the keyboard to the next field; the last field's Done closes it.
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        suffix = suffix?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
internal fun Label(text: Int) {
    Text(stringResource(text).uppercase(), style = GhTextStyles.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Error(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("error"))
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Why the last send failed, or that the form has errors to fix. */
@Composable
internal fun SendError(send: SendState, hasErrors: Boolean) {
    when (send) {
        SendState.FAILED -> Error(stringResource(R.string.send_failed))
        SendState.NOT_CONFIGURED -> Error(stringResource(R.string.send_not_configured))
        else -> if (hasErrors) Error(stringResource(R.string.form_has_errors))
    }
}

/** Latitude and longitude typed in, for a guide who knows them (or can't use the map): inline, under the map. */
@Composable
internal fun CoordinatesEntry(initial: GeoPoint?, onPick: (GeoPoint) -> Unit, onCancel: () -> Unit) {
    var latitude by rememberSaveable { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    val point = parseCoordinates(latitude, longitude)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.testTag("coordinates")) {
        Note(stringResource(R.string.coordinates_help))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text(stringResource(R.string.latitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f).testTag("latitude"),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text(stringResource(R.string.longitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).testTag("longitude"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("coordinates-cancel")) { Text(stringResource(R.string.coordinates_cancel)) }
            SecondaryButton(
                stringResource(R.string.coordinates_place),
                { point?.let(onPick) },
                Modifier.weight(1f).testTag("coordinates-ok"),
                enabled = point != null,
            )
        }
    }
}

/** "48.24" and "-79,02" (a comma as typed in French) within the world, else null. */
internal fun parseCoordinates(latitude: String, longitude: String): GeoPoint? {
    val lat = latitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val lng = longitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return GeoPoint(lat, lng)
}

private const val DESCRIPTION_LINES = 4
