package ca.kallistesoft.guidehunter.core.model

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
) {
    /** The fields that keep the offer from being sent; empty when it can be. */
    fun errors(): Set<OfferField> = buildSet {
        if (title.isBlank()) add(OfferField.TITLE)
        if (description.trim().length < MIN_DESCRIPTION) add(OfferField.DESCRIPTION)
        if (activity == null) add(OfferField.ACTIVITY)
        if (species.isEmpty() || activity != null && species.any { !activity.covers(it.discipline) }) {
            add(OfferField.SPECIES)
        }
        if (region.isBlank()) add(OfferField.REGION)
        if (location == null) add(OfferField.LOCATION)
        if (priceFrom.isNotBlank() && priceFrom.trim().toIntOrNull()?.takeIf { it > 0 } == null) {
            add(OfferField.PRICE)
        }
    }

    companion object {
        const val MAX_TITLE = 100

        /** A description says enough for a hunter or angler to choose: a sentence or two at least. */
        const val MIN_DESCRIPTION = 30
        const val MAX_DESCRIPTION = 2_000
    }
}

/** A field of the offer form, to show its error. */
enum class OfferField { TITLE, DESCRIPTION, ACTIVITY, SPECIES, REGION, LOCATION, PRICE }

/** Where an offer is in our review. Stored by [name] in Firestore. */
enum class OfferStatus {
    /** Sent or edited by the guide, not reviewed yet. */
    PENDING,

    /** Reviewed, and on the map as sent. */
    PUBLISHED,
}

/** A guide's offer as saved. [live]: a reviewed version is on the map (an edit waiting for review leaves it there). */
data class GuideOffer(
    val id: String,
    val draft: OfferDraft,
    val status: OfferStatus = OfferStatus.PENDING,
    val live: Boolean = false,
)
