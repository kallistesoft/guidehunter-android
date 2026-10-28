package ca.kallistesoft.guidehunter.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ca.kallistesoft.guidehunter.core.designsystem.component.LinkButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Trip

/**
 * A guide's name and contact, each one tap away (dialer, mail, website); blank ones are left out. Shown to a user
 * whose request the guide accepted. The email says which offer ([offerTitle]) and [trip].
 */
@Composable
fun ContactDetails(contact: GuideContact, links: LinkOpener, offerTitle: String, trip: Trip?) {
    val tripText = trip?.let { LocalContext.current.tripDates(it) }
    val subject = listOfNotNull(stringResource(R.string.contact_email_subject), offerTitle, tripText).joinToString(" · ")
    val body = tripText?.let { stringResource(R.string.contact_email_body, offerTitle, it) }
    Text(contact.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("contact-name"))
    if (contact.phone.isNotBlank()) {
        ContactLink(Icons.Default.Phone, stringResource(R.string.contact_call, contact.phone), "contact-phone") { links.dial(contact.phone) }
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

/** A phone, email or website: a green link with its icon. */
@Composable
fun ContactLink(icon: ImageVector, text: String, tag: String, onClick: () -> Unit) {
    LinkButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, contentDescription = null, Modifier.size(Sizes.iconM))
        Text(text, Modifier.padding(start = Spacing.s))
    }
}
