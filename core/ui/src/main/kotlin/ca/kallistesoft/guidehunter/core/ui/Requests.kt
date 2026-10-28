package ca.kallistesoft.guidehunter.core.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import java.util.Formatter

// What the user's and the guide's screens share about requests: the status, the day, the confirmation.

/** "Pending", "Accepted", "Declined", "Cancelled", "Offer removed". */
@get:StringRes
val RequestStatus.label: Int
    get() = when (this) {
        RequestStatus.PENDING -> R.string.status_pending
        RequestStatus.APPROVED -> R.string.status_approved
        RequestStatus.REJECTED -> R.string.status_rejected
        RequestStatus.CANCELLED -> R.string.status_cancelled
        RequestStatus.OFFER_REMOVED -> R.string.status_offer_removed
    }

/** A request's status as a small pill with a dot: amber pending, green accepted, red declined, grey the others. */
@Composable
fun StatusChip(status: RequestStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (status) {
        RequestStatus.PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        RequestStatus.APPROVED -> colors.secondaryContainer to colors.onSecondaryContainer
        RequestStatus.REJECTED -> colors.errorContainer to colors.error
        RequestStatus.CANCELLED, RequestStatus.OFFER_REMOVED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Row(
        modifier
            .background(background, CircleShape)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs)
            .testTag("status-${status.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(Spacing.s).background(content, CircleShape))
        Text(stringResource(status.label), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** The day of [millis] in the phone's time zone and language: "Oct 28" (the year when it isn't this one). */
fun Context.day(millis: Long): String {
    val locale = resources.configuration.locales[0]
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateRange(this, Formatter(StringBuilder(), locale), millis, millis, flags).toString()
}

/** A request's trip, or "Dates on request" without one. */
fun Context.requestTrip(trip: Trip?): String = trip?.let(::tripDates) ?: getString(R.string.dates_on_request)

/**
 * "Send your request?": the offer and the trip, what the guide will get (with Edit, [onEdit]), then Send ([onSend])
 * or Not now ([onDismiss]). [sending] keeps the buttons from being tapped twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmRequestSheet(
    offerTitle: String,
    trip: Trip?,
    contact: UserContact,
    sending: Boolean,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ConfirmRequest(offerTitle, trip, contact, sending, onSend, onEdit, onDismiss)
    }
}

/** The confirmation's content, apart from its sheet so it's tested on its own. */
@Composable
internal fun ConfirmRequest(
    offerTitle: String,
    trip: Trip?,
    contact: UserContact,
    sending: Boolean,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xxl)
            .testTag("confirm-request"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.headlineSmall)
        InfoCard {
            Text(offerTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                trip?.let { stringResource(R.string.your_trip, context.tripDates(it)) } ?: stringResource(R.string.dates_on_request),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.confirm_shared),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit, modifier = Modifier.testTag("edit-contact")) { Text(stringResource(R.string.confirm_edit)) }
            }
            listOf(contact.name, contact.email, contact.phone).filter { it.isNotBlank() }.forEachIndexed { index, line ->
                Text(line, style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.confirm_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(stringResource(R.string.confirm_send), onSend, Modifier.fillMaxWidth().testTag("send-request"), enabled = !sending)
        SecondaryButton(stringResource(R.string.confirm_not_now), onDismiss, Modifier.fillMaxWidth().testTag("not-now"), enabled = !sending)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
    }
}

