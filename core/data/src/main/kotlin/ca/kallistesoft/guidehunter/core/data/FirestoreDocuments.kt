package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Species

// The Firestore documents (see firestore.rules and README, "Firebase"), as plain maps so they're tested on the JVM.

internal object Collections {
    /** Published offers, public: written from the Firebase console once an application is approved. */
    const val GUIDES = "guides"

    /** A guide's name and contact details, same document id as in [GUIDES]. */
    const val CONTACTS = "guideContacts"

    /** "Become a guide" forms: create-only for the app. */
    const val APPLICATIONS = "guideApplications"

    /** Emails sent by the "Trigger Email from Firestore" extension. */
    const val MAIL = "mail"
}

/** A `guides` document, or null when a required field is missing or unknown (the app never shows half a guide). */
internal fun guideFrom(id: String, data: Map<String, Any?>): Guide? {
    val activity = (data["activity"] as? String)?.let { name -> GuideActivity.entries.find { it.name == name } }
    val lat = (data["lat"] as? Number)?.toDouble()
    val lng = (data["lng"] as? Number)?.toDouble()
    val title = data["title"] as? String
    if (activity == null || lat == null || lng == null || title.isNullOrBlank()) return null
    return Guide(
        id = id,
        title = title,
        description = data["description"] as? String ?: "",
        activity = activity,
        species = Species.fromNames((data["species"] as? List<*>).orEmpty().filterIsInstance<String>()),
        region = data["region"] as? String ?: "",
        location = GeoPoint(lat, lng),
        priceFrom = (data["priceFrom"] as? Number)?.toInt(),
        currency = data["currency"] as? String ?: "CAD",
    )
}

/** A `guideContacts` document, or null without a name. */
internal fun contactFrom(data: Map<String, Any?>): GuideContact? {
    val name = data["name"] as? String
    if (name.isNullOrBlank()) return null
    return GuideContact(
        name = name,
        phone = data["phone"] as? String ?: "",
        email = data["email"] as? String ?: "",
        website = data["website"] as? String ?: "",
    )
}

/** A `guideApplications` document: the form as sent, trimmed. */
internal fun GuideApplication.toDocument(): Map<String, Any?> = mapOf(
    "businessName" to businessName.trim(),
    "description" to description.trim(),
    "activity" to activity?.name,
    "species" to species.map { it.name },
    "region" to region.trim(),
    "lat" to location?.latitude,
    "lng" to location?.longitude,
    "priceFrom" to priceFrom.trim().toIntOrNull(),
    "email" to email.trim(),
    "phone" to phone.trim(),
    "website" to website.trim(),
)

/** The `mail` document that emails [to] the application; the reply goes straight to the guide. */
internal fun GuideApplication.toMail(to: String): Map<String, Any?> = mapOf(
    "to" to to,
    "replyTo" to email.trim(),
    "message" to mapOf(
        "subject" to "New guide application: ${businessName.trim()}",
        "text" to listOf(
            "Business name: ${businessName.trim()}",
            "Offers: ${activity?.name}",
            "Species: ${species.joinToString { it.name }}",
            "Region: ${region.trim()}",
            "Location: ${location?.let { "${it.latitude}, ${it.longitude}" }}",
            "Price from: ${priceFrom.trim().ifEmpty { "on request" }}",
            "Email: ${email.trim()}",
            "Phone: ${phone.trim()}",
            "Website: ${website.trim()}",
            "",
            description.trim(),
        ).joinToString("\n"),
    ),
)
