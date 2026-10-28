package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Species
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

// The Firestore documents (see firestore.rules and README, "Firebase"), as plain maps so they're tested on the JVM.

internal object Collections {
    /** Published offers, public: copied from [OFFERS] once reviewed (scripts/approve-offer.py). */
    const val GUIDES = "guides"

    /** A guide's name and contact details, same document id as in [GUIDES]. */
    const val CONTACTS = "guideContacts"

    /** Guides' profiles, by Firebase user id: only their guide reads and writes them. */
    const val PROFILES = "guideProfiles"

    /** Guides' offers as they wrote them, waiting for review or reviewed; same id as in [GUIDES] once published. */
    const val OFFERS = "guideOffers"

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

/** A `guideProfiles` document: the profile, trimmed. */
internal fun GuideProfile.toDocument(): Map<String, Any?> = trimmed().run {
    mapOf("businessName" to businessName, "email" to email, "phone" to phone, "website" to website)
}

/** A `guideProfiles` document, or null when there is none yet. */
internal fun profileFrom(data: Map<String, Any?>?): GuideProfile? = data?.let {
    GuideProfile(
        businessName = it["businessName"] as? String ?: "",
        email = it["email"] as? String ?: "",
        phone = it["phone"] as? String ?: "",
        website = it["website"] as? String ?: "",
    )
}

/** The contact fields of the guide's `guideContacts` documents, updated when the profile changes. */
internal fun GuideProfile.toContact(): Map<String, Any?> = trimmed().run {
    mapOf("name" to businessName, "email" to email, "phone" to phone, "website" to website)
}

/**
 * A `guideOffers` document, as the guide saves it: trimmed, back to [OfferStatus.PENDING] (firestore.rules allows
 * nothing else from the app), and [live] as it was.
 */
internal fun OfferDraft.toDocument(uid: String, live: Boolean): Map<String, Any?> = mapOf(
    "ownerUid" to uid,
    "title" to title.trim(),
    "description" to description.trim(),
    "activity" to activity?.name,
    "species" to species.map { it.name },
    "region" to region.trim(),
    "lat" to location?.latitude,
    "lng" to location?.longitude,
    "priceFrom" to priceFrom.trim().toIntOrNull(),
    "status" to OfferStatus.PENDING.name,
    "live" to live,
    "updatedAt" to FieldValue.serverTimestamp(),
)

/** A `guideOffers` document; an unknown status reads as waiting for review. */
internal fun offerFrom(id: String, data: Map<String, Any?>): GuideOffer {
    val lat = (data["lat"] as? Number)?.toDouble()
    val lng = (data["lng"] as? Number)?.toDouble()
    return GuideOffer(
        id = id,
        draft = OfferDraft(
            title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "",
            activity = (data["activity"] as? String)?.let { name -> GuideActivity.entries.find { it.name == name } },
            species = Species.fromNames((data["species"] as? List<*>).orEmpty().filterIsInstance<String>()).toSet(),
            region = data["region"] as? String ?: "",
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            priceFrom = (data["priceFrom"] as? Number)?.toInt()?.toString().orEmpty(),
        ),
        status = OfferStatus.entries.find { it.name == data["status"] } ?: OfferStatus.PENDING,
        live = data["live"] == true,
    )
}

/** The guide's offers, the latest change first; one just saved (its server time not back yet) comes first. */
internal fun offersFrom(documents: List<Pair<String, Map<String, Any?>>>): List<GuideOffer> =
    documents.sortedByDescending { (_, data) -> (data["updatedAt"] as? Timestamp) ?: Timestamp(Long.MAX_VALUE / 1_000_000_000, 0) }
        .map { (id, data) -> offerFrom(id, data) }

/**
 * The `mail` document that emails [to] a new or edited offer, with the ids that scripts/approve-offer.py needs. The
 * reply goes straight to the guide.
 */
internal fun offerMail(to: String, id: String, uid: String, draft: OfferDraft, profile: GuideProfile, edited: Boolean): Map<String, Any?> {
    val offer = draft.copy(title = draft.title.trim(), region = draft.region.trim(), priceFrom = draft.priceFrom.trim())
    val guide = profile.trimmed()
    return mapOf(
        "to" to to,
        "replyTo" to guide.email,
        "message" to mapOf(
            "subject" to "${if (edited) "Offer edited" else "New offer"}: ${offer.title} (${guide.businessName})".take(MAX_SUBJECT),
            "text" to listOf(
                "Offer: ${offer.title}",
                "Offers: ${offer.activity?.name}",
                "Species: ${offer.species.joinToString { it.name }}",
                "Region: ${offer.region}",
                "Location: ${offer.location?.let { "${it.latitude}, ${it.longitude}" }}",
                "Price from: ${offer.priceFrom.ifEmpty { "on request" }}",
                "",
                "Business name: ${guide.businessName}",
                "Email: ${guide.email}",
                "Phone: ${guide.phone}",
                "Website: ${guide.website}",
                "",
                draft.description.trim(),
                "",
                "Approve: scripts/approve-offer.py <project> $id (guide $uid)",
            ).joinToString("\n"),
        ),
    )
}

/** firestore.rules caps the subject; a long title and business name are cut. */
private const val MAX_SUBJECT = 300
