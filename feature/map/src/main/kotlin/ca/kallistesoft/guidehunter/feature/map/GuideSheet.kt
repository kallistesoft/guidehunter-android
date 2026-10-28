package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ca.kallistesoft.guidehunter.core.designsystem.component.FeaturedBadge
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SectionToggle
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.rememberLinkOpener
import ca.kallistesoft.guidehunter.core.ui.tripDates
import ca.kallistesoft.guidehunter.core.ui.tripLength
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.ui.ContactDetails
import ca.kallistesoft.guidehunter.core.ui.StatusChip
import ca.kallistesoft.guidehunter.core.ui.day
import java.text.NumberFormat
import java.util.Currency

/** What the offer's contact card can do. */
internal class ContactActions(
    val unlock: () -> Unit = {},
    val interested: () -> Unit = {},
    val cancel: () -> Unit = {},
    val myRequests: () -> Unit = {},
    val settings: () -> Unit = {},
    /** "Request sent" was shown. */
    val sentShown: () -> Unit = {},
    /** The confirmation's Send, Edit (the user's contact) and Not now. */
    val send: () -> Unit = {},
    val editContact: () -> Unit = {},
    val dismissConfirm: () -> Unit = {},
)

/** A guide's offer, over the map; "Request sent" over its bottom when [sent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideSheet(selected: SelectedGuide, actions: ContactActions, sent: Boolean, onTrip: (Trip) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box {
            GuideDetail(selected, actions, rememberLinkOpener(), onTrip)
            SentNotice(sent, actions, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A guide's offer beside the map, on a wide window: a close button, then the same detail as the sheet. */
@Composable
internal fun GuidePane(
    selected: SelectedGuide,
    actions: ContactActions,
    sent: Boolean,
    onTrip: (Trip) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.testTag("guide-pane"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box {
            Column {
                IconButton(onClick = onDismiss, modifier = Modifier.padding(Spacing.s).testTag("pane-close")) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
                GuideDetail(selected, actions, rememberLinkOpener(), onTrip)
            }
            SentNotice(sent, actions, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** "Request sent to the guide", with "My requests". */
@Composable
private fun SentNotice(sent: Boolean, actions: ContactActions, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val message = stringResource(R.string.request_sent)
    val action = stringResource(UiR.string.my_requests)
    LaunchedEffect(sent) {
        if (!sent) return@LaunchedEffect
        val result = host.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Short)
        actions.sentShown()
        if (result == SnackbarResult.ActionPerformed) actions.myRequests()
    }
    SnackbarHost(host, modifier.testTag("request-sent"))
}

/**
 * The public offer (what, where, dates, species, price, description), then how to contact the guide: a rewarded ad,
 * then "I'm interested"; or the request already sent. The next departure shows, the others open under "Other dates";
 * the trip the user picks ([onTrip]) goes with the request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideDetail(
    selected: SelectedGuide,
    actions: ContactActions,
    links: LinkOpener,
    onTrip: (Trip) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val guide = selected.item.guide
    Column(
        modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xxxl)
            .testTag("guide-detail"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ActivityLabel(guide, selected.featured)
        Text(guide.title, style = MaterialTheme.typography.headlineSmall)
        Text(selected.item.subtitle(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(guide.price(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("price"))

        Section(stringResource(R.string.section_dates))
        Dates(guide, selected, onTrip)

        Section(stringResource(R.string.section_species))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            guide.species.forEach { species -> SpeciesTag(stringResource(species.label)) }
        }

        Section(stringResource(R.string.section_about))
        Text(guide.description, style = MaterialTheme.typography.bodyLarge)

        Section(stringResource(R.string.section_contact))
        ContactCard(selected, actions, links)
    }
}

/** The offer's activity ("HUNTING"), and the "Featured" badge of a boosted one: on its card and in its sheet. */
@Composable
internal fun ActivityLabel(guide: Guide, featured: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(guide.activity.label).uppercase(), style = GhTextStyles.overline, color = MaterialTheme.colorScheme.tertiary)
        if (featured) FeaturedBadge(stringResource(UiR.string.featured), Modifier.testTag("featured-${guide.id}"))
    }
}

/**
 * How long a trip lasts, then the next departure; the later ones under "Other dates", closed at first unless the
 * chosen trip is among them. Each is a choice (a radio button): the one the user wants to ask the guide about.
 */
@Composable
private fun Dates(guide: Guide, selected: SelectedGuide, onTrip: (Trip) -> Unit) {
    val context = LocalContext.current
    guide.schedule.length?.let {
        Text(context.tripLength(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("trip-length"))
    }
    val trips = selected.trips
    if (trips.isEmpty()) {
        Text(stringResource(UiR.string.dates_on_request), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dates-on-request"))
        return
    }
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.next_departure), style = MaterialTheme.typography.labelLarge)
        TripChoice(trips.first(), selected.trip, onTrip)
        val others = trips.drop(1)
        if (others.isNotEmpty()) {
            var open by rememberSaveable(guide.id) { mutableStateOf(selected.trip in others) }
            SectionToggle(
                title = stringResource(R.string.other_dates),
                expanded = open,
                onToggle = { open = !open },
                detail = others.size.toString(),
                modifier = Modifier.testTag("other-dates"),
            )
            if (open) others.forEach { TripChoice(it, selected.trip, onTrip) }
        }
    }
}

@Composable
private fun TripChoice(trip: Trip, chosen: Trip?, onTrip: (Trip) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .selectable(selected = trip == chosen, onClick = { onTrip(trip) }, role = Role.RadioButton)
            .testTag("trip-${trip.start}${trip.slot?.let { "-$it" }.orEmpty()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = trip == chosen, onClick = null)
        Text(LocalContext.current.tripDates(trip), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.s))
    }
}

/** A species the guide offers: a label, not a button (a disabled chip would read as one to TalkBack). */
@Composable
private fun SpeciesTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .border(Sizes.border, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = GhTextStyles.overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.s),
    )
}

@Composable
private fun ContactCard(selected: SelectedGuide, actions: ContactActions, links: LinkOpener) {
    val contact = selected.contact
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("contact"),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // The trip the request is about: the chosen one, or the request's own.
            val trip = (contact as? ContactState.Requested)?.request?.trip ?: selected.trip
            if (contact != ContactState.Own) {
                trip?.let {
                    Text(
                        stringResource(UiR.string.your_trip, LocalContext.current.tripDates(it)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("your-trip"),
                    )
                }
            }
            when (contact) {
                ContactState.Locked -> {
                    Info(Icons.Default.Lock, stringResource(R.string.contact_locked))
                    UnlockButton(actions.unlock)
                }
                ContactState.Unlocking, ContactState.Loading -> Working()
                is ContactState.Failed -> {
                    ErrorText(
                        stringResource(
                            when (contact.reason) {
                                UnlockError.NOT_EARNED -> R.string.contact_not_earned
                                UnlockError.OFFLINE -> R.string.contact_offline
                            },
                        ),
                    )
                    UnlockButton(actions.unlock)
                }
                ContactState.Unlocked -> {
                    Info(Icons.Default.Info, stringResource(R.string.interested_note))
                    RequestErrorText(selected.error)
                    PrimaryButton(
                        stringResource(R.string.interested),
                        actions.interested,
                        Modifier.fillMaxWidth().testTag("interested"),
                        enabled = !selected.busy,
                    )
                }
                ContactState.Own -> {
                    Info(Icons.Default.Storefront, stringResource(R.string.own_offer))
                    SecondaryButton(stringResource(R.string.switch_to_guide), actions.settings, Modifier.fillMaxWidth().testTag("own-switch"))
                }
                is ContactState.Requested -> Request(contact.request, selected, actions, links)
            }
        }
    }
}

/** The request already sent: its status, then what the user can do. */
@Composable
private fun Request(request: InterestRequest, selected: SelectedGuide, actions: ContactActions, links: LinkOpener) {
    val context = LocalContext.current
    StatusChip(request.status)
    Text(
        when (request.status) {
            RequestStatus.PENDING -> stringResource(R.string.request_pending, context.day(request.createdAt))
            RequestStatus.APPROVED -> stringResource(R.string.request_approved)
            RequestStatus.REJECTED -> stringResource(R.string.request_rejected)
            RequestStatus.CANCELLED -> stringResource(R.string.request_cancelled)
            RequestStatus.OFFER_REMOVED -> stringResource(R.string.request_offer_removed)
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("request-text"),
    )
    RequestErrorText(selected.error)
    when (request.status) {
        RequestStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(UiR.string.my_requests), actions.myRequests, Modifier.weight(1f).testTag("my-requests"))
            TextButton(onClick = actions.cancel, enabled = !selected.busy, modifier = Modifier.weight(1f).testTag("cancel-request")) {
                Text(stringResource(UiR.string.cancel_request), color = MaterialTheme.colorScheme.error)
            }
        }
        RequestStatus.APPROVED -> request.guideContact?.let { ContactDetails(it, links, request.offerTitle, request.trip) }
        RequestStatus.CANCELLED -> PrimaryButton(
            stringResource(UiR.string.send_again),
            actions.interested,
            Modifier.fillMaxWidth().testTag("send-again"),
            enabled = !selected.busy,
        )
        RequestStatus.REJECTED, RequestStatus.OFFER_REMOVED -> Unit
    }
}

@Composable
private fun Info(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(icon, contentDescription = null, Modifier.size(Sizes.iconL), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Working() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        CircularProgressIndicator(Modifier.size(Sizes.iconL).testTag("contact-loading"))
        Text(stringResource(R.string.contact_working))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("contact-error"))
}

@Composable
private fun RequestErrorText(error: RequestError?) {
    error ?: return
    ErrorText(
        stringResource(
            when (error) {
                RequestError.SIGN_IN_FAILED -> R.string.error_sign_in
                RequestError.SEND_FAILED -> R.string.error_send
                RequestError.NOT_CONFIGURED -> R.string.error_not_configured
            },
        ),
    )
}

@Composable
private fun UnlockButton(onUnlock: () -> Unit) {
    PrimaryButton(
        stringResource(R.string.contact_unlock),
        onUnlock,
        Modifier.fillMaxWidth().testTag("unlock"),
        icon = Icons.Default.PlayCircle,
    )
}

/** "Abitibi · About 470 km from you", or just the region while the user's location is unknown. */
@Composable
internal fun GuideItem.subtitle(): String {
    // Formats are made once per locale, not for every card on every pass.
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return listOfNotNull(
        guide.region.takeIf { it.isNotBlank() },
        distanceKm?.let { stringResource(R.string.distance_km, format.format(it)) },
    ).joinToString(" · ")
}

@Composable
private fun Guide.price(): String {
    val price = priceFrom ?: return stringResource(R.string.price_on_request)
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale, currency) {
        NumberFormat.getCurrencyInstance(locale).apply {
            runCatching { this.currency = Currency.getInstance(this@price.currency) }
            maximumFractionDigits = 0
        }
    }
    return stringResource(R.string.price_from, format.format(price))
}
