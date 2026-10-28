package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Species
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
        schedule = scheduleFrom(data),
        boostedUntil = boostedUntilFrom(data),
    )
}

/** A `guides` document's `boostedUntil` (a timestamp, set by its guide after an ad); null if never boosted. */
internal fun boostedUntilFrom(data: Map<String, Any?>): Instant? =
    (data[BOOSTED_UNTIL] as? Timestamp)?.let { Instant.fromEpochSeconds(it.seconds, it.nanoseconds) }

/** The update that boosts a `guides` document [until] then: that field only (firestore.rules allows nothing else). */
internal fun boostUpdate(until: Instant): Map<String, Any> =
    mapOf(BOOSTED_UNTIL to Timestamp(until.epochSeconds, until.nanosecondsOfSecond))

/** The boosts of a guide's `guides` documents, by offer id: the ones never boosted are left out. */
internal fun boostsFrom(documents: List<Pair<String, Map<String, Any?>>>): Map<String, Instant> =
    documents.mapNotNull { (id, data) -> boostedUntilFrom(data)?.let { id to it } }.toMap()

private const val BOOSTED_UNTIL = "boostedUntil"

/**
 * An offer's schedule in a `guideOffers` or `guides` document: `tripLength` ("HALF_DAY" or a number of days),
 * `departures` (ISO dates, a half day's ending in "/AM" or "/PM": "2027-05-20/AM") and `everyYear`. Readable in the
 * console and the review email; unreadable departures are dropped.
 */
internal fun Schedule.toFields(): Map<String, Any?> = mapOf(
    "tripLength" to when (val length = length) {
        TripLength.HalfDay -> HALF_DAY
        is TripLength.Days -> length.count
        null -> null
    },
    "departures" to departures.sorted().map(::departureText),
    "everyYear" to everyYear,
)

internal fun scheduleFrom(data: Map<String, Any?>): Schedule = Schedule(
    length = when (val length = data["tripLength"]) {
        HALF_DAY -> TripLength.HalfDay
        is Number -> TripLength.Days(length.toInt())
        else -> null
    },
    departures = (data["departures"] as? List<*>).orEmpty().filterIsInstance<String>().mapNotNull(::departureFrom),
    everyYear = data["everyYear"] == true,
)

private fun departureText(departure: Departure): String =
    departure.date.toString() + when (departure.slot) {
        Slot.MORNING -> "/AM"
        Slot.AFTERNOON -> "/PM"
        null -> ""
    }

private fun departureFrom(text: String): Departure? {
    val slot = when (text.substringAfter('/', "")) {
        "AM" -> Slot.MORNING
        "PM" -> Slot.AFTERNOON
        "" -> null
        else -> return null
    }
    return runCatching { Departure(LocalDate.parse(text.substringBefore('/')), slot) }.getOrNull()
}

private const val HALF_DAY = "HALF_DAY"

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
) + schedule.toFields()

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
            schedule = scheduleFrom(data),
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
                "Duration: ${when (val length = offer.schedule.length) {
                    TripLength.HalfDay -> "half day"
                    is TripLength.Days -> "${length.count} days"
                    null -> "not given"
                }}",
                "Departures: ${offer.schedule.departures.sorted().joinToString(transform = ::departureText).ifEmpty { "on request" }}" +
                    if (offer.schedule.everyYear) " (every year)" else "",
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
