package ca.kallistesoft.guidehunter.core.model

import kotlinx.datetime.LocalDate

/**
 * A guide's public offer, shown to everyone on the map. Who the guide is and how to reach them ([GuideContact]) is
 * kept apart and shown only once the user has watched a rewarded ad.
 */
data class Guide(
    val id: String,
    /** What the trip is ("Black bear hunt in Abitibi"), not the guide's name. */
    val title: String,
    val description: String,
    val activity: GuideActivity,
    val species: List<Species>,
    /** Town or region, as the guide wrote it. */
    val region: String,
    val location: GeoPoint,
    /** Starting price per person, in [currency]; null when the guide gives none ("on request"). */
    val priceFrom: Int? = null,
    val currency: String = "CAD",
    /** How long a trip lasts and when they leave; no departures: "dates on request". */
    val schedule: Schedule = Schedule(),
)

/** The guide's name and contact details, unlocked by a rewarded ad. Blank fields aren't shown. */
data class GuideContact(
    val name: String,
    val phone: String = "",
    val email: String = "",
    val website: String = "",
)

/**
 * What the map shows: guides of a discipline (null for both) offering any of [species] (empty for any species), with
 * a trip that fits the [dates] the hunter can go (null: any time).
 */
data class GuideFilter(
    val discipline: Discipline? = null,
    val species: Set<Species> = emptySet(),
    val dates: Trip? = null,
) {
    /** An offer whose dates are all over never shows; one with dates on request always fits the dates. */
    fun matches(guide: Guide, today: LocalDate): Boolean =
        (discipline == null || guide.activity.covers(discipline)) &&
            (species.isEmpty() || guide.species.any { it in species }) &&
            !guide.schedule.expired(today) &&
            (dates == null || guide.schedule.onRequest || guide.schedule.fits(dates, today))

    /** The species the picker offers with this filter's discipline. */
    val speciesChoices: List<Species>
        get() = Species.entries.filter { discipline == null || it.discipline == discipline }
}
