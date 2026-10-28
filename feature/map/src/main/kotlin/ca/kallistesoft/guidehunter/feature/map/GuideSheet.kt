package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import ca.kallistesoft.guidehunter.core.designsystem.component.LinkButton
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SectionToggle
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.rememberLinkOpener
import ca.kallistesoft.guidehunter.core.ui.tripDates
import ca.kallistesoft.guidehunter.core.ui.tripLength
import java.text.NumberFormat
import java.util.Currency

/** A guide's offer, over the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideSheet(selected: SelectedGuide, onUnlock: () -> Unit, onTrip: (Trip) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        GuideDetail(selected, onUnlock, rememberLinkOpener(), onTrip)
    }
}

/** A guide's offer beside the map, on a wide window: a close button, then the same detail as the sheet. */
@Composable
internal fun GuidePane(selected: SelectedGuide, onUnlock: () -> Unit, onTrip: (Trip) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.testTag("guide-pane"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            IconButton(onClick = onDismiss, modifier = Modifier.padding(Spacing.s).testTag("pane-close")) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
            GuideDetail(selected, onUnlock, rememberLinkOpener(), onTrip)
        }
    }
}

/**
 * The public offer (what, where, dates, species, price, description), then the guide's name and contact: hidden behind
 * a rewarded ad until [SelectedGuide.contact] is unlocked. The next departure shows, the others open under "Other
 * dates"; the trip the user picks ([onTrip]) goes with the contact and into the email.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideDetail(
    selected: SelectedGuide,
    onUnlock: () -> Unit,
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
        Text(stringResource(guide.activity.label).uppercase(), style = GhTextStyles.overline, color = MaterialTheme.colorScheme.tertiary)
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
        ContactCard(selected.contact, onUnlock, links, guide.title, selected.trip)
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
        Text(stringResource(R.string.dates_on_request), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dates-on-request"))
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
private fun ContactCard(contact: ContactState, onUnlock: () -> Unit, links: LinkOpener, title: String, trip: Trip?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("contact"),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val tripText = trip?.let { LocalContext.current.tripDates(it) }
            tripText?.let {
                Text(stringResource(R.string.your_trip, it), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("your-trip"))
            }
            when (contact) {
                ContactState.Locked -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(Sizes.iconL))
                        Text(stringResource(R.string.contact_hidden), style = MaterialTheme.typography.bodyMedium)
                    }
                    ContactPlaceholder()
                    UnlockButton(onUnlock)
                }
                ContactState.Unlocking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    CircularProgressIndicator(Modifier.size(Sizes.iconL).testTag("contact-loading"))
                    Text(stringResource(R.string.contact_unlocking))
                }
                is ContactState.Unlocked -> ContactDetails(contact.contact, links, title, tripText)
                ContactState.Missing -> Text(stringResource(R.string.contact_missing))
                is ContactState.Failed -> {
                    Text(
                        stringResource(
                            when (contact.reason) {
                                UnlockError.NOT_EARNED -> R.string.contact_not_earned
                                UnlockError.OFFLINE -> R.string.contact_offline
                                UnlockError.LOAD_FAILED -> R.string.contact_load_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (contact.reason == UnlockError.LOAD_FAILED) {
                        SecondaryButton(stringResource(R.string.try_again), onUnlock, Modifier.fillMaxWidth().testTag("unlock"))
                    } else {
                        UnlockButton(onUnlock)
                    }
                }
            }
        }
    }
}

/** Grey bars where the name and contact will be: something is there, hidden. Decoration only (not read aloud). */
@Composable
private fun ContactPlaceholder() {
    val bar = MaterialTheme.colorScheme.surfaceVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { testTag = "contact-placeholder" },
    ) {
        Box(Modifier.fillMaxWidth(NAME_BAR).height(Sizes.placeholderLine).background(bar, MaterialTheme.shapes.extraSmall))
        Box(Modifier.fillMaxWidth(CONTACT_BAR).height(Sizes.placeholderLineSmall).background(bar, MaterialTheme.shapes.extraSmall))
    }
}

/** The bars' widths, as in the mockup: a name, then a shorter line. */
private const val NAME_BAR = 0.62f
private const val CONTACT_BAR = 0.44f

@Composable
private fun UnlockButton(onUnlock: () -> Unit) {
    PrimaryButton(
        stringResource(R.string.contact_unlock),
        onUnlock,
        Modifier.fillMaxWidth().testTag("unlock"),
        icon = Icons.Default.PlayCircle,
    )
}

@Composable
private fun ContactDetails(contact: GuideContact, links: LinkOpener, title: String, trip: String?) {
    Text(contact.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("contact-name"))
    // The email says which offer, and the trip the user chose.
    val subject = listOfNotNull(stringResource(R.string.contact_email_subject), title, trip).joinToString(" · ")
    val body = trip?.let { stringResource(R.string.contact_email_body, title, it) }
    if (contact.phone.isNotBlank()) {
        ContactLink(Icons.Default.Phone, stringResource(R.string.contact_call, contact.phone), "contact-phone") {
            links.dial(contact.phone)
        }
    }
    if (contact.email.isNotBlank()) {
        ContactLink(Icons.Default.Email, stringResource(R.string.contact_email, contact.email), "contact-email") {
            links.email(contact.email, subject, body)
        }
    }
    if (contact.website.isNotBlank()) {
        ContactLink(Icons.Default.Language, stringResource(R.string.contact_website, contact.website), "contact-website") {
            links.open(contact.website)
        }
    }
}

@Composable
private fun ContactLink(icon: ImageVector, text: String, tag: String, onClick: () -> Unit) {
    LinkButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, contentDescription = null, Modifier.size(Sizes.iconM))
        Text(text, Modifier.padding(start = Spacing.s))
    }
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
