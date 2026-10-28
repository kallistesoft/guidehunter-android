package ca.kallistesoft.guidehunter.core.model

import kotlin.time.Instant

/**
 * One of a guide's offers as they type it: what goes on the map once we've reviewed it. A guide can have several
 * (a bear hunt and a walleye trip), and edit them; every edit is reviewed again.
 */
data class OfferDraft(
    /** What the trip is ("Black bear hunt in Abitibi"): the offer's name on the map. */
    val title: String = "",
    val description: String = "",
    val activity: GuideActivity? = null,
    val species: Set<Species> = emptySet(),
    val region: String = "",
    val location: GeoPoint? = null,
    /** Starting price per person, as typed (digits only once valid); blank for "on request". */
    val priceFrom: String = "",
    /** How long a trip lasts and when they leave. */
    val schedule: Schedule = Schedule(),
) {
    /** The fields that keep the offer from being sent; empty when it can be. */
    fun errors(): Set<OfferField> = buildSet {
        if (title.isBlank()) add(OfferField.TITLE)
        if (description.trim().length < MIN_DESCRIPTION) add(OfferField.DESCRIPTION)
        // One type per offer: an older "both" has to be made hunting or fishing.
        if (activity !in GuideActivity.forOffers) add(OfferField.ACTIVITY)
        if (species.isEmpty() || activity != null && species.any { !activity.covers(it.discipline) }) {
            add(OfferField.SPECIES)
        }
        if (region.isBlank()) add(OfferField.REGION)
        if (location == null) add(OfferField.LOCATION)
        val length = schedule.length
        if (length == null || length is TripLength.Days && length.count !in 1..TripLength.MAX_DAYS) add(OfferField.DURATION)
        val departures = schedule.departures
        // A half day leaves in the morning or the afternoon; a longer trip just on a day.
        if (departures.size > MAX_DEPARTURES || departures.distinct().size != departures.size ||
            departures.any { (it.slot != null) != (length == TripLength.HalfDay) }
        ) {
            add(OfferField.DATES)
        }
        if (priceFrom.isNotBlank() && priceFrom.trim().toIntOrNull()?.takeIf { it > 0 } == null) {
            add(OfferField.PRICE)
        }
    }

    /** The fields of [step] that keep the offer from being sent: what stops the form going to the next step. */
    fun errors(step: OfferStep): Set<OfferField> = errors().filterTo(mutableSetOf()) { it.step == step }

    companion object {
        const val MAX_TITLE = 100

        /** A description says enough for a hunter or angler to choose: a sentence or two at least. */
        const val MIN_DESCRIPTION = 30
        const val MAX_DESCRIPTION = 2_000

        /** A season of weekly departures and then some; the rules allow as many. */
        const val MAX_DEPARTURES = 60
    }
}

/** The offer form's steps, in order: one purpose each. */
enum class OfferStep { WHAT, SPECIES, WHERE, WHEN, REVIEW }

/** A field of the offer form, to show its error, and the [step] it's on. */
enum class OfferField(val step: OfferStep) {
    TITLE(OfferStep.WHAT),
    DESCRIPTION(OfferStep.WHAT),
    ACTIVITY(OfferStep.WHAT),
    SPECIES(OfferStep.SPECIES),
    REGION(OfferStep.WHERE),
    LOCATION(OfferStep.WHERE),
    DURATION(OfferStep.WHEN),
    DATES(OfferStep.WHEN),
    PRICE(OfferStep.REVIEW),
}

/** Where an offer is in our review. Stored by [name] in Firestore. */
enum class OfferStatus {
    /** Sent or edited by the guide, not reviewed yet. */
    PENDING,

    /** Reviewed, and on the map as sent. */
    PUBLISHED,
}

/**
 * A guide's offer as saved. [live]: a reviewed version is on the map (an edit waiting for review leaves it there).
 * [boostedUntil]: until when the version on the map is boosted ([Boost]); null if never.
 */
data class GuideOffer(
    val id: String,
    val draft: OfferDraft,
    val status: OfferStatus = OfferStatus.PENDING,
    val live: Boolean = false,
    val boostedUntil: Instant? = null,
) {
    fun isBoosted(now: Instant): Boolean = boostedUntil != null && boostedUntil > now
}
